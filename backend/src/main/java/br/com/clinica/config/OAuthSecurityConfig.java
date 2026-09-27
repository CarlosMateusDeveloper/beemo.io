package br.com.clinica.config;

import br.com.clinica.service.*;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class OAuthSecurityConfig {
    public static final String LINK_USER="clinicos.linkUser";
    public static final String LINK_PROVIDER="clinicos.linkProvider";
    @Bean @Order(1)
    SecurityFilterChain oauthSecurity(HttpSecurity http,OAuthClients clients,OAuthIdentityService identities,
            SessionService sessions,SessionCookies cookies) throws Exception {
        http.securityMatcher("/oauth2/**","/login/oauth2/**")
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .requestCache(c -> c.disable())
            .authorizeHttpRequests(a -> a.anyRequest().permitAll())
            .oauth2Login(o -> o.clientRegistrationRepository(clients)
                .authorizedClientRepository(new TransientOAuthClients())
                .loginPage(clients.frontend()+"/login")
                .successHandler((req,res,auth) -> {
                    var session=req.getSession(false);
                    Object linkUser=session==null?null:session.getAttribute(LINK_USER);
                    String destination=linkUser==null?"/login":"/conta";
                    String outcome="?oauth=success";
                    try {
                        if(!(auth.getPrincipal() instanceof OidcUser oidc) || !(auth instanceof OAuth2AuthenticationToken oauth))
                            throw new IllegalStateException("OIDC obrigatório");
                        String issuer=oidc.getIssuer().toString(),subject=oidc.getSubject();
                        if(linkUser instanceof Integer id) {
                            var claims=sessions.validar(SessionCookies.token(req));
                            if(claims.isEmpty() || !id.toString().equals(claims.get().getSubject())
                                    || !oauth.getAuthorizedClientRegistrationId().equals(session.getAttribute(LINK_PROVIDER)))
                                throw new IllegalStateException("Vínculo sem sessão correspondente");
                            identities.vincular(id,oauth.getAuthorizedClientRegistrationId(),issuer,subject);
                            outcome="?oauth=linked";
                        } else {
                            var usuario=identities.localizar(issuer,subject);
                            cookies.emitir(req,res,sessions.gerar(usuario));
                        }
                    } catch(org.springframework.security.oauth2.core.OAuth2AuthenticationException e) {
                        outcome="?oauth_error="+("oauth_unlinked".equals(e.getError().getErrorCode())?"unlinked":"failed");
                    } catch(RuntimeException e) { outcome="?oauth_error=failed"; }
                    finally {
                        if(session!=null) session.invalidate();
                        org.springframework.security.core.context.SecurityContextHolder.clearContext();
                    }
                    res.sendRedirect(clients.frontend()+destination+outcome);
                })
                .failureHandler((req,res,error) -> {
                    var session=req.getSession(false);
                    boolean linking=session!=null && session.getAttribute(LINK_USER)!=null;
                    if(session!=null) session.invalidate();
                    org.springframework.security.core.context.SecurityContextHolder.clearContext();
                    res.sendRedirect(clients.frontend()+(linking?"/conta":"/login")+"?oauth_error=failed");
                }));
        return http.build();
    }
    /** O provedor autentica; seus access/refresh tokens não são usados nem persistidos. */
    static class TransientOAuthClients implements org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository {
        public <T extends org.springframework.security.oauth2.client.OAuth2AuthorizedClient> T loadAuthorizedClient(String id,org.springframework.security.core.Authentication a,jakarta.servlet.http.HttpServletRequest r) { return null; }
        public void saveAuthorizedClient(org.springframework.security.oauth2.client.OAuth2AuthorizedClient c,org.springframework.security.core.Authentication a,jakarta.servlet.http.HttpServletRequest r,jakarta.servlet.http.HttpServletResponse s) {}
        public void removeAuthorizedClient(String id,org.springframework.security.core.Authentication a,jakarta.servlet.http.HttpServletRequest r,jakarta.servlet.http.HttpServletResponse s) {}
    }
}
