package br.com.clinica.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import static org.junit.jupiter.api.Assertions.*;

class OAuthProtocolTest {
    @Test void codigoComEstadoNoncePkceERedirectFixo() {
        var registration=ClientRegistration.withRegistrationId("google")
            .clientId("test-client").clientSecret("test-secret")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("https://clinic.example/login/oauth2/code/google")
            .scope("openid","profile","email")
            .authorizationUri("https://accounts.example/authorize")
            .tokenUri("https://accounts.example/token")
            .jwkSetUri("https://accounts.example/jwks")
            .clientSettings(ClientRegistration.ClientSettings.builder().requireProofKey(true).build()).build();
        var resolver=new DefaultOAuth2AuthorizationRequestResolver(new InMemoryClientRegistrationRepository(registration),"/oauth2/authorization");
        var request=new MockHttpServletRequest("GET","/oauth2/authorization/google");
        request.setServletPath("/oauth2/authorization/google");
        request.addHeader("Host","attacker.example");
        var first=resolver.resolve(request,"google");
        var second=resolver.resolve(request,"google");
        assertNotNull(first.getState());
        assertNotEquals(first.getState(),second.getState());
        assertEquals("https://clinic.example/login/oauth2/code/google",first.getRedirectUri());
        assertNotNull(first.getAdditionalParameters().get("nonce"));
        assertNotNull(first.getAdditionalParameters().get("code_challenge"));
        assertEquals("S256",first.getAdditionalParameters().get("code_challenge_method"));
        assertNotNull(first.getAttributes().get("code_verifier"));
    }
}
