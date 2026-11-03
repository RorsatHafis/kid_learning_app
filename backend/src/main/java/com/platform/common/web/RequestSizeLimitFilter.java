package com.platform.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * A request body other than a file upload is at most {@code platform.http.max-body-bytes} (1 MB, as the nginx front
 * allows for /api). Without it, a deployment with no proxy in front (the hosted path) would read any JSON body whole
 * into memory before validation looks at it - one oversized request, even to the unauthenticated sign-in, could exhaust
 * a small container (the rate limiter counts requests, not bytes). A declared length over the limit is refused with
 * 413 before anything is read; a body without one (chunked) is read only up to the limit, then fails. Uploads
 * (multipart) keep their own limits ({@code spring.servlet.multipart}).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestSizeLimitFilter.class);

    private final long maxBytes;

    public RequestSizeLimitFilter(@Value("${platform.http.max-body-bytes:1048576}") long maxBytes) {
        this.maxBytes = maxBytes;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String type = request.getContentType();
        return type != null && type.toLowerCase(java.util.Locale.ROOT).startsWith(MediaType.MULTIPART_FORM_DATA_VALUE);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long declared = request.getContentLengthLong();
        if (declared > maxBytes) {
            log.warn("Request body of {} bytes refused on {} from {} (limit {})", declared, request.getRequestURI(),
                    request.getRemoteAddr(), maxBytes);
            response.setStatus(HttpStatus.CONTENT_TOO_LARGE.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("""
                    {"type":"about:blank","title":"Content Too Large","status":413,"detail":"%s","instance":"%s"}""".formatted(
                    ServerText.forRequest(request, ServerText.FILTER_BODY_TOO_LARGE, 413), request.getRequestURI()));
            return;
        }
        chain.doFilter(declared < 0 ? new Limited(request, maxBytes) : request, response);
    }

    /** A body of unknown length, read no further than the limit. */
    private static final class Limited extends HttpServletRequestWrapper {

        private final long maxBytes;
        private ServletInputStream stream;

        Limited(HttpServletRequest request, long maxBytes) {
            super(request);
            this.maxBytes = maxBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (stream == null) {
                ServletInputStream inner = super.getInputStream();
                stream = new ServletInputStream() {
                    private long read;

                    private void count(long n) throws IOException {
                        read += n;
                        if (read > maxBytes) {
                            throw new IOException("Request body larger than " + maxBytes + " bytes");
                        }
                    }

                    @Override
                    public int read() throws IOException {
                        int b = inner.read();
                        if (b >= 0) {
                            count(1);
                        }
                        return b;
                    }

                    @Override
                    public int read(byte[] buffer, int offset, int length) throws IOException {
                        int n = inner.read(buffer, offset, length);
                        if (n > 0) {
                            count(n);
                        }
                        return n;
                    }

                    @Override
                    public boolean isFinished() {
                        return inner.isFinished();
                    }

                    @Override
                    public boolean isReady() {
                        return inner.isReady();
                    }

                    @Override
                    public void setReadListener(ReadListener listener) {
                        inner.setReadListener(listener);
                    }
                };
            }
            return stream;
        }
    }
}
