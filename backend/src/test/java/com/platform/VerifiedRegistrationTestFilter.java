package com.platform;

import com.icegreen.greenmail.util.GreenMail;
import com.platform.identity.entity.Account;
import com.platform.identity.registration.RegistrationVerificationService;
import com.platform.identity.repository.AccountRepository;
import jakarta.mail.Message;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * TEST ONLY (never on the production classpath). Since 2026-11-10 an account is created only with a verified email's
 * registration grant. Most integration tests create their families through {@code POST /api/v1/auth/register}; for
 * those, this filter performs the REAL verification first - {@code start} sends the email to the test SMTP server
 * (GreenMail), the link token is read out of the delivered email, {@code verify} turns it into a grant - and adds the
 * grant to the request. Nothing is bypassed: the registration still consumes a genuine grant.
 *
 * <p>Left untouched: requests that already carry a grant, addresses that already have an account (the registration
 * answers 409 before it looks at a grant), and any request with {@code X-Test-Verify-Registration: off} - the tests of
 * the verification itself use that to see exactly what production does.
 */
public class VerifiedRegistrationTestFilter extends OncePerRequestFilter {

    public static final String OPT_OUT_HEADER = "X-Test-Verify-Registration";
    private static final Pattern LINK = Pattern.compile("/register/continue#token=([A-Za-z0-9_-]+)");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    // Looked up at the first request, not at startup: the filter is created with the web server, before the test
    // database's connection properties are in place.
    private final org.springframework.beans.factory.ObjectProvider<RegistrationVerificationService> verifications;
    private final org.springframework.beans.factory.ObjectProvider<AccountRepository> accounts;
    private final org.springframework.beans.factory.ObjectProvider<GreenMail> greenMail;
    private final org.springframework.beans.factory.ObjectProvider<org.springframework.jdbc.core.JdbcTemplate> jdbc;
    /** A grant per address while it has no account, shared by retries of the same registration. */
    private final Map<String, String> grants = new ConcurrentHashMap<>();

    public VerifiedRegistrationTestFilter(org.springframework.beans.factory.ObjectProvider<RegistrationVerificationService> verifications,
                                          org.springframework.beans.factory.ObjectProvider<AccountRepository> accounts,
                                          org.springframework.beans.factory.ObjectProvider<GreenMail> greenMail,
                                          org.springframework.beans.factory.ObjectProvider<org.springframework.jdbc.core.JdbcTemplate> jdbc) {
        this.verifications = verifications;
        this.accounts = accounts;
        this.greenMail = greenMail;
        this.jdbc = jdbc;
    }

    /**
     * A registration grant for {@code email} through the real flow - for tests that call the registration directly
     * rather than over HTTP.
     */
    public String grantFor(String email, String language) {
        return verifiedGrant(email, language);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !"/api/v1/auth/register".equals(request.getRequestURI())
                || "off".equalsIgnoreCase(request.getHeader(OPT_OUT_HEADER));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        byte[] body = request.getInputStream().readAllBytes();
        byte[] forwarded = body;
        JsonNode parsed = null;
        try {
            parsed = JSON.readTree(body);
        } catch (RuntimeException notJson) {
            // malformed on purpose: let the application answer it
        }
        String normalized = null;
        if (parsed instanceof ObjectNode node && node.hasNonNull("email")
                && (!node.hasNonNull("registrationGrant") || node.get("registrationGrant").asString().isBlank())) {
            String email = node.get("email").asString().strip();
            String language = node.hasNonNull("preferredLanguage") ? node.get("preferredLanguage").asString() : "en";
            if (PLAUSIBLE_EMAIL.matcher(email).matches()) {
                normalized = Account.normalize(email);
                if (accounts.getObject().findByNormalizedEmail(normalized).isEmpty()) {
                    try {
                        node.put("registrationGrant", grants.computeIfAbsent(normalized, key -> verifiedGrant(email, language)));
                        forwarded = JSON.writeValueAsBytes(node);
                    } catch (RuntimeException unverifiable) {
                        // the request goes on as sent, and the application answers it as in production
                    }
                }
            }
        }
        chain.doFilter(new BodyRequest(request, forwarded), response);
        // Used up once the address has an account; an address registered again later (after an erasure) needs a new one.
        if (normalized != null && accounts.getObject().findByNormalizedEmail(normalized).isPresent()) {
            grants.remove(normalized);
        }
    }

    private static final Pattern PLAUSIBLE_EMAIL = Pattern.compile("[^@ ]+@[^@ ]+[.][^@ ]+");

    /** The real flow: the email is sent, its link read from the delivered message, the link verified. */
    private String verifiedGrant(String email, String language) {
        String normalized = Account.normalize(email);
        // A test may register the same address again within the minute (after erasing it): the one-email-a-minute
        // pace is for people, so this helper starts the address afresh. Production keeps the pace.
        jdbc.getObject().update("delete from registration_verifications where normalized_email = ?", normalized);
        java.util.Set<String> earlier = linkTokens(normalized);
        verifications.getObject().start(email, language, "127.0.0.1");
        String token = awaitNewLinkToken(normalized, earlier);
        return verifications.getObject().verify(token).registrationGrant();
    }

    /** The registration link tokens emailed to the address so far (GreenMail lists mail by mailbox, not by time). */
    private java.util.Set<String> linkTokens(String normalizedEmail) {
        java.util.Set<String> tokens = new java.util.HashSet<>();
        for (MimeMessage message : greenMail.getObject().getReceivedMessages()) {
            try {
                boolean toAddress = Arrays.stream(message.getRecipients(Message.RecipientType.TO))
                        .anyMatch(a -> Account.normalize(a.toString()).equals(normalizedEmail));
                if (!toAddress) continue;
                Matcher link = LINK.matcher(String.valueOf(message.getContent()));
                if (link.find()) tokens.add(link.group(1));
            } catch (Exception unreadable) {
                // not one of ours
            }
        }
        return tokens;
    }

    private String awaitNewLinkToken(String normalizedEmail, java.util.Set<String> earlier) {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            for (String token : linkTokens(normalizedEmail)) {
                if (!earlier.contains(token)) return token;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new IllegalStateException("No registration verification email reached the test SMTP server for " + normalizedEmail);
    }

    /** The request with its body replaced. */
    private static final class BodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        BodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream in = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return in.read();
                }

                @Override
                public boolean isFinished() {
                    return in.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }

        @Override
        public java.io.BufferedReader getReader() {
            return new java.io.BufferedReader(new java.io.InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
