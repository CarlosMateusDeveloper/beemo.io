package br.com.clinica.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;
import static org.junit.jupiter.api.Assertions.*;

class RequestLoggingFilterTest {
    @Test void correlacionaSemRegistrarSegredosNemIdentificadorDoPaciente() throws Exception {
        var logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start(); logger.addAppender(appender);
        try {
            var req = new MockHttpServletRequest("GET", "/api/pacientes/123");
            req.addHeader("X-Request-ID", "request_42");
            req.addHeader("Authorization", "Bearer segredo");
            req.setQueryString("token=segredo");
            req.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/pacientes/{id}");
            var res = new MockHttpServletResponse();
            new RequestLoggingFilter().doFilter(req, res, (a, b) -> {
                assertEquals("request_42", MDC.get("requestId"));
                res.setStatus(403);
            });
            assertEquals("request_42", res.getHeader("X-Request-ID"));
            assertNull(MDC.get("requestId"));
            var event = appender.list.getLast();
            assertTrue(event.getKeyValuePairs().stream().anyMatch(p -> p.key.equals("status") && p.value.equals(403)));
            String log = event.getFormattedMessage() + event.getKeyValuePairs();
            assertTrue(log.contains("/api/pacientes/{id}"));
            assertFalse(log.contains("segredo")); assertFalse(log.contains("123"));
        } finally { logger.detachAppender(appender); appender.stop(); }
    }
    @Test void rejeitaRequestIdInjetadoELimpaMdcMesmoComErro() {
        var req = new MockHttpServletRequest();
        req.addHeader("X-Request-ID", "invalido\r\nInjected: true");
        var res = new MockHttpServletResponse();
        assertThrows(ServletException.class, () -> new RequestLoggingFilter().doFilter(req, res, (a, b) -> { throw new ServletException("teste"); }));
        assertTrue(res.getHeader("X-Request-ID").matches("[a-f0-9-]{36}"));
        assertNull(MDC.get("requestId"));
    }
}
