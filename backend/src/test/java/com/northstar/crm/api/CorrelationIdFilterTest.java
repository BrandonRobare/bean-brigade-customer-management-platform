package com.northstar.crm.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void usesCorrelationIdHeaderAndClearsMdcAfterRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-ID", "lab-request-001");

        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> valueInsideFilter = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) ->
                valueInsideFilter.set(MDC.get("correlationId")));

        assertEquals("lab-request-001", valueInsideFilter.get());
        assertNull(MDC.get("correlationId"));
    }

    @Test
    void generatesCorrelationIdWhenHeaderIsMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> valueInsideFilter = new AtomicReference<>();
        filter.doFilter(request, response, (req, res) ->
                valueInsideFilter.set(MDC.get("correlationId")));

        String generated = valueInsideFilter.get();

        assertFalse(generated.isBlank());
        UUID.fromString(generated);
        assertNull(MDC.get("correlationId"));
    }
}


