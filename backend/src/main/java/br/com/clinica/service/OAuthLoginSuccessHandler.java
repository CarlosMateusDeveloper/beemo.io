package br.com.clinica.service;

import br.com.clinica.config.OAuthClients;
import br.com.clinica.config.OAuthSecurityConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
public class OAuthLoginSuccessHandler implements AuthenticationSuccessHandler {
    private final OAuthClients clients;
    private final OAuthLoginService login;
    private final OAuthIdentityService identities;
    private final SessionService sessions;
    private final SessionCookies cookies;

    public OAuthLoginSuccessHandler(OAuthClients clients,OAuthLoginService login,OAuthIdentityService identities,
            SessionService sessions,SessionCookies cookies) {
        this.clients=clients;this.login=login;this.identities=identities;this.sessions=sessions;this.cookies=cookies;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest req,HttpServletResponse res,Authentication auth) throws IOException {
        var session=req.getSession(false);
        Object linkUser=session==null?null:session.getAttribute(OAuthSecurityConfig.LINK_USER);
        String destination=linkUser==null?"/login":"/minha-conta";
        String outcome="?oauth=success";
        try {
            if(!(auth.getPrincipal() instanceof OidcUser oidc) || !(auth instanceof OAuth2AuthenticationToken oauth))
                throw new IllegalStateException("OIDC obrigatório");
            String provider=oauth.getAuthorizedClientRegistrationId();
            if(linkUser instanceof Integer id) {
                var claims=sessions.validar(SessionCookies.token(req));
                if(claims.isEmpty() || !id.toString().equals(claims.get().getSubject())
                        || !provider.equals(session.getAttribute(OAuthSecurityConfig.LINK_PROVIDER)))
                    throw new IllegalStateException("Vínculo sem sessão correspondente");
                identities.vincular(id,provider,oidc.getIdToken().getIssuer().toString(),oidc.getIdToken().getSubject());
                outcome="?oauth=linked";
            } else {
                var usuario=login.entrar(provider,oidc);
                cookies.emitir(req,res,sessions.gerar(usuario));
            }
        } catch(OAuth2AuthenticationException e) {
            outcome="oauth_not_authorized".equals(e.getError().getErrorCode())
                ?"?oauth_error=not_authorized":"?oauth_error=failed";
        } catch(RuntimeException e) {
            outcome="?oauth_error=failed";
        } finally {
            if(session!=null) session.invalidate();
            SecurityContextHolder.clearContext();
        }
        res.sendRedirect(clients.frontend()+destination+outcome);
    }
}
