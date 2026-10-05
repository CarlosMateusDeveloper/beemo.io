package br.com.clinica.service;

import br.com.clinica.model.Usuario;
import java.util.Locale;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Autenticacao global. Nao consulta nem concede permissoes de clinica. */
@Service
public class OAuthLoginService {
    private final OAuthIdentityService identities;
    private final IdentityAccountService accounts;
    private final JdbcTemplate db;
    private final String microsoftTenant;
    public OAuthLoginService(OAuthIdentityService identities,IdentityAccountService accounts,JdbcTemplate db,Environment env) {
        this.identities=identities;this.accounts=accounts;this.db=db;
        microsoftTenant=br.com.clinica.config.MicrosoftIssuer.audience(env.getProperty("MICROSOFT_TENANT_ID","common"));
    }
    @Transactional
    public Usuario entrar(String provider,OidcUser principal) {
        var token=principal.getIdToken();
        String issuer=token.getIssuer().toString(),subject=token.getSubject();
        if(subject==null || subject.isBlank()) throw invalid();
        if("google".equals(provider)) {
            if(!"https://accounts.google.com".equals(issuer)) throw invalid();
        } else if("microsoft".equals(provider)) {
            if(!br.com.clinica.config.MicrosoftIssuer.accepts(microsoftTenant,token.getClaimAsString("tid"),issuer)) throw invalid();
        } else throw invalid();
        db.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,"oidc:"+issuer+":"+subject);
        try { return identities.localizar(issuer,subject); }
        catch(OAuth2AuthenticationException e) {
            if(!"oauth_unlinked".equals(e.getError().getErrorCode())) throw e;
        }
        String email=token.getEmail();
        if(email!=null) email=email.strip().toLowerCase(Locale.ROOT);
        if(email!=null && (email.length()>100 || !email.contains("@"))) email=null;
        String domain=token.getClaimAsString("hd");
        boolean attested="google".equals(provider) && email!=null && Boolean.TRUE.equals(token.getEmailVerified())
            && (email.endsWith("@gmail.com") || (domain!=null && !domain.isBlank() && email.endsWith("@"+domain.toLowerCase(Locale.ROOT))));
        // E-mail nao atestado nao impede autenticacao; apenas nao une identidades existentes.
        var user=attested?accounts.emailVerificado(email,token.getFullName()):accounts.novaIdentidade(email,token.getFullName());
        identities.vincular(user.getId(),provider,issuer,subject);
        return user;
    }
    private static OAuth2AuthenticationException invalid() { return new OAuth2AuthenticationException("invalid_identity"); }
}
