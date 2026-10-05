package br.com.clinica.config;

import br.com.clinica.service.*;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class OAuthSecurityConfig {
    public static final String LINK_USER="clinicos.linkUser";
    public static final String LINK_PROVIDER="clinicos.linkProvider";
    @Bean @Order(1)
    SecurityFilterChain oauthSecurity(HttpSecurity http,OAuthClients clients,OAuthLoginSuccessHandler successHandler) throws Exception {
        http.securityMatcher("/oauth2/**","/login/oauth2/**")
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .requestCache(c -> c.disable())
            .authorizeHttpRequests(a -> a.anyRequest().permitAll())
            .oauth2Login(o -> o.clientRegistrationRepository(clients)
                .authorizedClientRepository(new TransientOAuthClients())
                .loginPage(clients.frontend()+"/login")
                .successHandler(successHandler)
                .failureHandler((req,res,error) -> {
                    var session=req.getSession(false);
                    boolean linking=session!=null && session.getAttribute(LINK_USER)!=null;
                    if(session!=null) session.invalidate();
                    org.springframework.security.core.context.SecurityContextHolder.clearContext();
                    res.sendRedirect(clients.frontend()+(linking?"/minha-conta":"/login")+"?oauth_error=failed");
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
