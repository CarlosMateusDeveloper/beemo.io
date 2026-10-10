package br.com.clinica.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

/** Apenas metadados de transporte; nunca registra URL bruta, query, corpo ou credenciais. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String supplied = req.getHeader("X-Request-ID");
        String id = supplied != null && supplied.matches("[a-zA-Z0-9_-]{1,64}") ? supplied : UUID.randomUUID().toString();
        String previous = MDC.get("requestId");
        MDC.put("requestId", id);
        req.setAttribute("requestId", id);
        res.setHeader("X-Request-ID", id);
        long start = System.nanoTime();
        boolean failed = false;
        try {
            chain.doFilter(req, res);
        } catch (IOException | ServletException | RuntimeException e) {
            failed = true;
            throw e;
        } finally {
            Object route = req.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            log.atInfo().addKeyValue("method", req.getMethod())
                .addKeyValue("route", route == null ? "unmapped" : route.toString())
                .addKeyValue("status", failed ? 500 : res.getStatus())
                .addKeyValue("durationMs", (System.nanoTime() - start) / 1_000_000.0)
                .log("http_request");
            if (previous == null) MDC.remove("requestId"); else MDC.put("requestId", previous);
        }
    }
}
