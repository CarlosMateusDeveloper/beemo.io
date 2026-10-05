package br.com.clinica.service;

import br.com.clinica.model.Usuario;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OAuthLoginServiceTest {
    static final String GOOGLE="https://accounts.google.com";
    static final String TENANT="11111111-2222-3333-4444-555555555555";
    static final String OID="aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
    static final String MICROSOFT="https://login.microsoftonline.com/"+TENANT+"/v2.0";
    final OAuthIdentityService identities=mock(OAuthIdentityService.class);
    final IdentityAccountService accounts=mock(IdentityAccountService.class);
    final JdbcTemplate db=mock(JdbcTemplate.class);
    final MockEnvironment env=new MockEnvironment().withProperty("MICROSOFT_TENANT_ID",TENANT);
    static OidcUser principal(String issuer,String subject,Map<String,Object> claims) {
        var values=new java.util.HashMap<String,Object>(claims);
        values.put("iss",issuer);values.put("sub",subject);
        return new DefaultOidcUser(java.util.List.of(),new OidcIdToken("test-id-token",Instant.now(),Instant.now().plusSeconds(300),values));
    }
    Usuario usuario() { var user=new Usuario();user.setId(19);return user; }
    void firstAccess(String issuer) { when(identities.localizar(issuer,"subject")).thenThrow(new OAuth2AuthenticationException("oauth_unlinked")); }
    OAuthLoginService service() { return new OAuthLoginService(identities,accounts,db,env); }
    @Test void googleNovoEmailAutenticaSemCadastroOuTenant() {
        firstAccess(GOOGLE);var user=usuario();
        when(accounts.emailVerificado("novo@gmail.com",null)).thenReturn(user);
        assertSame(user,service().entrar("google",principal(GOOGLE,"subject",Map.of("email","Novo@gmail.com","email_verified",true))));
        verify(identities).vincular(19,"google",GOOGLE,"subject");
        assertNull(user.getPerfil());
    }
    @Test void googleComEmailExternoTambemAutenticaSemApropriarContaDoMesmoEmail() {
        firstAccess(GOOGLE);var user=usuario();
        when(accounts.novaIdentidade("pessoa@example.com",null)).thenReturn(user);
        assertSame(user,service().entrar("google",principal(GOOGLE,"subject",Map.of("email","pessoa@example.com","email_verified",true))));
        verify(accounts,never()).emailVerificado(anyString(),any());
    }
    @Test void emailNaoVerificadoNaoImpedeIdentidadeOidcValida() {
        firstAccess(GOOGLE);var user=usuario();
        when(accounts.novaIdentidade("pessoa@gmail.com",null)).thenReturn(user);
        assertSame(user,service().entrar("google",principal(GOOGLE,"subject",Map.of("email","pessoa@gmail.com","email_verified",false))));
        verify(accounts,never()).emailVerificado(anyString(),any());
    }
    @Test void microsoftAutenticaSemMapeamentoAdministrativoDeUsuarios() {
        firstAccess(MICROSOFT);var user=usuario();
        when(accounts.novaIdentidade("pessoa@example.com",null)).thenReturn(user);
        assertSame(user,service().entrar("microsoft",principal(MICROSOFT,"subject",Map.of("tid",TENANT,"oid",OID,"email","pessoa@example.com"))));
        verify(accounts,never()).emailVerificado(anyString(),any());
    }
    @Test void identidadePersistidaPrevaleceMesmoSemEmail() {
        var user=usuario();when(identities.localizar(GOOGLE,"subject")).thenReturn(user);
        assertSame(user,service().entrar("google",principal(GOOGLE,"subject",Map.of())));
        verifyNoInteractions(accounts);
    }
    @Test void issuerInvalidoNaoCriaConta() {
        assertThrows(OAuth2AuthenticationException.class,()->service().entrar("google",principal("https://attacker.example","subject",Map.of())));
        verifyNoInteractions(accounts,identities);
    }
    @Test void emailWorkspaceAtestadoPodeReusarIdentidadeGlobalDeEmail() {
        firstAccess(GOOGLE);var user=usuario();
        when(accounts.emailVerificado("pessoa@clinic.example",null)).thenReturn(user);
        assertSame(user,service().entrar("google",principal(GOOGLE,"subject",Map.of("email","pessoa@clinic.example","email_verified",true,"hd","clinic.example"))));
    }
}
