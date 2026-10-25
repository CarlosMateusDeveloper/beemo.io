package br.com.clinica.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResponseSecurityHeadersFilterTest {
    @Test
    void protegeRespostasDaApiESinalizaAutenticacaoComoNaoCacheavel() throws Exception {
        for (String path : java.util.List.of("/api/auth/login", "/api/v1/auth/login")) {
            var request = new MockHttpServletRequest("POST", path);
            request.setServletPath(path);
            var response = new MockHttpServletResponse();

            new ResponseSecurityHeadersFilter().doFilter(request, response, (req, res) -> {});

            assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
            assertEquals("DENY", response.getHeader("X-Frame-Options"));
            assertEquals("no-referrer", response.getHeader("Referrer-Policy"));
            assertEquals("camera=(), geolocation=(), microphone=()", response.getHeader("Permissions-Policy"));
            assertEquals("same-origin", response.getHeader("Cross-Origin-Opener-Policy"));
            assertEquals("no-store", response.getHeader("Cache-Control"));
        }
    }
}
