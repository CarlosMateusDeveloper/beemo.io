package br.com.clinica.config;

import br.com.clinica.integration.IntegracaoPrincipal;
import br.com.clinica.service.IntegracaoCredencialService;
import br.com.clinica.service.TenantContext;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IntegracaoApiKeyFilterTest {
    @AfterEach void clear() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test void credencialDefineTenantEscoposEFonteSomenteDuranteARequisicao() throws Exception {
        var credentials = mock(IntegracaoCredencialService.class);
        when(credentials.autenticar("cls.identificador.segredo-com-tamanho-suficiente"))
                .thenReturn(Optional.of(new IntegracaoPrincipal(
                        31L, 7, 19L, Set.of("eventos:escrever", "estado:ler"))));
        var request = new MockHttpServletRequest("POST", "/api/integracoes/v1/eventos");
        request.setServletPath("/api/integracoes/v1/eventos");
        request.addHeader("Authorization", "Bearer cls.identificador.segredo-com-tamanho-suficiente");
        var response = new MockHttpServletResponse();

        new IntegracaoApiKeyFilter(credentials).doFilter(request, response, (req, res) -> {
            assertEquals(7, TenantContext.id());
            assertEquals(19L, req.getAttribute("clinisis.integration.source"));
            assertEquals(31L, req.getAttribute("clinisis.integration.credential"));
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            assertEquals(2, authentication.getAuthorities().size());
            assertTrue(authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("SCOPE_eventos:escrever")));
        });

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertThrows(org.springframework.web.server.ResponseStatusException.class, TenantContext::id);
    }

    @Test void credencialRevogadaOuInvalidaFalhaSemExecutarARequisicao() throws Exception {
        var credentials = mock(IntegracaoCredencialService.class);
        when(credentials.autenticar(anyString())).thenReturn(Optional.empty());
        var request = new MockHttpServletRequest("POST", "/api/integracoes/v1/eventos");
        request.setServletPath("/api/integracoes/v1/eventos");
        request.addHeader("Authorization", "Bearer cls.revogada.segredo-com-tamanho-suficiente");
        var response = new MockHttpServletResponse();
        var chain = mock(jakarta.servlet.FilterChain.class);

        new IntegracaoApiKeyFilter(credentials).doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("integration_credential_invalid"));
        verifyNoInteractions(chain);
        assertThrows(org.springframework.web.server.ResponseStatusException.class, TenantContext::id);
    }
}
