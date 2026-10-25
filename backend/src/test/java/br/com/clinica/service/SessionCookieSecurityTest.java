package br.com.clinica.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionCookieSecurityTest {
    @Test void cookieDeProducaoProtegeSessaoNaEmissaoENoLogout() {
        var cookies = new SessionCookies(true, 30);
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        cookies.emitir(request, response, "token-teste");
        String issued = response.getHeader("Set-Cookie");
        assertTrue(issued.contains("Secure"));
        assertTrue(issued.contains("HttpOnly"));
        assertTrue(issued.contains("SameSite=Lax"));

        response = new MockHttpServletResponse();
        cookies.limpar(request, response);
        String cleared = response.getHeader("Set-Cookie");
        assertTrue(cleared.contains("Secure"));
        assertTrue(cleared.contains("HttpOnly"));
        assertTrue(cleared.contains("SameSite=Lax"));
        assertTrue(cleared.contains("Max-Age=0"));
    }
}
