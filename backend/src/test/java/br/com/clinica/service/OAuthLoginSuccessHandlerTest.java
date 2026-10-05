package br.com.clinica.service;

import br.com.clinica.config.OAuthClients;
import br.com.clinica.config.OAuthSecurityConfig;
import br.com.clinica.model.Usuario;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OAuthLoginSuccessHandlerTest {
    final OAuthClients clients=mock(OAuthClients.class);
    final OAuthLoginService login=mock(OAuthLoginService.class);
    final OAuthIdentityService identities=mock(OAuthIdentityService.class);
    final SessionService sessions=mock(SessionService.class);
    final OAuthLoginSuccessHandler handler=new OAuthLoginSuccessHandler(clients,login,identities,sessions,new SessionCookies(false,30));
    final MockHttpServletRequest request=new MockHttpServletRequest();
    final MockHttpServletResponse response=new MockHttpServletResponse();

    OAuth2AuthenticationToken auth(String provider) {
        when(clients.frontend()).thenReturn("http://localhost:5173");
        var principal=OAuthLoginServiceTest.principal(OAuthLoginServiceTest.GOOGLE,"subject",Map.of("email","authorized@gmail.com","email_verified",true));
        return new OAuth2AuthenticationToken(principal,principal.getAuthorities(),provider);
    }
    @Test void callbackEmiteSessaoDiretamenteEEncerraEstadoOAuth() throws Exception {
        var authentication=auth("google");var user=new Usuario();user.setId(19);
        when(login.entrar("google",(org.springframework.security.oauth2.core.oidc.user.OidcUser)authentication.getPrincipal())).thenReturn(user);
        when(sessions.gerar(user)).thenReturn("signed-session");
        var temporary=(MockHttpSession)request.getSession(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        handler.onAuthenticationSuccess(request,response,authentication);
        assertEquals("http://localhost:5173/login?oauth=success",response.getRedirectedUrl());
        assertTrue(response.getHeader("Set-Cookie").contains("clinicos_session=signed-session"));
        assertTrue(response.getHeader("Set-Cookie").contains("HttpOnly"));
        assertTrue(temporary.isInvalid());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
    @Test void usuarioNaoAutorizadoNaoRecebeCookieNemConfirmacao() throws Exception {
        var authentication=auth("google");
        when(login.entrar(anyString(),any())).thenThrow(new OAuth2AuthenticationException("oauth_not_authorized"));
        var temporary=(MockHttpSession)request.getSession(true);
        handler.onAuthenticationSuccess(request,response,authentication);
        assertEquals("http://localhost:5173/login?oauth_error=not_authorized",response.getRedirectedUrl());
        assertNull(response.getHeader("Set-Cookie"));
        assertTrue(temporary.isInvalid());
        verifyNoInteractions(sessions);
    }
    @Test void falhaAoPersistirVinculoNaoEmiteSessao() throws Exception {
        var authentication=auth("microsoft");
        when(login.entrar(anyString(),any())).thenThrow(new IllegalStateException("database unavailable"));
        handler.onAuthenticationSuccess(request,response,authentication);
        assertEquals("http://localhost:5173/login?oauth_error=failed",response.getRedirectedUrl());
        assertNull(response.getHeader("Set-Cookie"));
        verifyNoInteractions(sessions);
    }
    @Test void vinculoExplicitoNoPerfilAindaExigeSessaoPropria() throws Exception {
        var authentication=auth("microsoft");
        request.getSession(true).setAttribute(OAuthSecurityConfig.LINK_USER,19);
        request.getSession().setAttribute(OAuthSecurityConfig.LINK_PROVIDER,"microsoft");
        when(sessions.validar("")).thenReturn(Optional.empty());
        handler.onAuthenticationSuccess(request,response,authentication);
        assertEquals("http://localhost:5173/minha-conta?oauth_error=failed",response.getRedirectedUrl());
        verifyNoInteractions(login,identities);
        assertNull(response.getHeader("Set-Cookie"));
    }
}
