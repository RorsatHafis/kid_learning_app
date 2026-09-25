package com.platform.common.web;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTests {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void generatesAnIdAddsItToTheResponseAndClearsMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        try {
            filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
                    assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNotBlank());

            String correlationId = response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
            assertThat(correlationId).isNotBlank();
            assertThat(UUID.fromString(correlationId)).isNotNull();
            assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
        } finally {
            MDC.clear();
        }
    }

    @Test
    void preservesAnInboundIdAcrossTheRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "gateway-request-42");
        MockHttpServletResponse response = new MockHttpServletResponse();

        try {
            filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
                    assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isEqualTo("gateway-request-42"));

            assertThat(response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER))
                    .isEqualTo("gateway-request-42");
        } finally {
            MDC.clear();
        }
    }
}
