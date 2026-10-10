package br.com.clinica.config;

import br.com.clinica.service.MigratingPasswordEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
public class SecurityConfig {
    private final JwtAuthFilter jwt;
    public SecurityConfig(JwtAuthFilter jwt) { this.jwt=jwt; }

    @Bean
    @org.springframework.core.annotation.Order(2)
    public SecurityFilterChain apiSecurity(HttpSecurity http, TenantDatabaseFilter tenantDatabase, @org.springframework.beans.factory.annotation.Value("${app.auth.secure-cookies:false}") boolean secureCookies) throws Exception {
        var csrf=new CookieCsrfTokenRepository();
        csrf.setCookieName("clinicos_csrf");
        csrf.setHeaderName("X-CSRF-TOKEN");
        csrf.setCookieCustomizer(c -> c.sameSite("Lax").path("/").secure(secureCookies));
        http.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .cors(org.springframework.security.config.Customizer.withDefaults())
            .csrf(c -> c.csrfTokenRepository(csrf).csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            .requestCache(c -> c.disable())
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req,res,ex) -> {
                    res.setStatus(401);res.setContentType("application/json");res.setCharacterEncoding("UTF-8");
                    res.getWriter().write("{\"error\":{\"code\":\"unauthorized\",\"message\":\"Faça login para continuar.\"},\"message\":\"Faça login para continuar.\"}");
                })
                .accessDeniedHandler((req,res,ex) -> {
                    res.setStatus(403);res.setContentType("application/json");res.setCharacterEncoding("UTF-8");
                    boolean csrfError=ex instanceof org.springframework.security.web.csrf.CsrfException;
                    boolean noTenant=br.com.clinica.service.TenantContext.get()==null;
                    res.getWriter().write(csrfError
                        ? "{\"error\":{\"code\":\"csrf_invalid\",\"message\":\"Atualize a página e tente novamente.\"},\"code\":\"csrf_invalid\",\"message\":\"Atualize a página e tente novamente.\"}"
                        : noTenant ? "{\"error\":{\"code\":\"tenant_required\",\"message\":\"Sua conta ainda não possui acesso a esta organização.\"},\"code\":\"tenant_required\",\"message\":\"Sua conta ainda não possui acesso a esta organização.\"}"
                        : "{\"error\":{\"code\":\"forbidden\",\"message\":\"Seu perfil não tem permissão para esta ação.\"},\"message\":\"Seu perfil não tem permissão para esta ação.\"}");
                }))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/api/auth/login","/api/auth/magic-link","/api/auth/magic-link/verify",
                    "/api/auth/csrf","/api/auth/providers","/api/auth/logout",
                    "/api/v1/auth/login","/api/v1/auth/magic-link","/api/v1/auth/magic-link/verify",
                    "/api/v1/auth/csrf","/api/v1/auth/providers","/api/v1/auth/logout","/error").permitAll()
                .requestMatchers("/api/auth/session/check","/api/v1/auth/session/check").hasAuthority("TENANT_ACCESS")
                .requestMatchers("/api/auth/**","/api/v1/auth/**","/api/tenants","/api/tenants/**").authenticated()
                .requestMatchers("/actuator/**").denyAll()
                .requestMatchers("/api/usuarios/**").hasRole("ADMINISTRADOR")
                .anyRequest().hasAuthority("TENANT_ACCESS"))
            .addFilterBefore(jwt,UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(tenantDatabase,JwtAuthFilter.class);
        return http.build();
    }
    @Bean public org.springframework.boot.web.servlet.FilterRegistrationBean<TenantDatabaseFilter> tenantRegistration(TenantDatabaseFilter filter) {
        var registration=new org.springframework.boot.web.servlet.FilterRegistrationBean<>(filter);
        registration.setEnabled(false);return registration;
    }
    @Bean public PasswordEncoder passwordEncoder() { return new MigratingPasswordEncoder(); }

    // O filtro só participa da cadeia da API, nunca da cadeia de OAuth.
    @Bean public org.springframework.boot.web.servlet.FilterRegistrationBean<JwtAuthFilter> jwtRegistration() {
        var registration=new org.springframework.boot.web.servlet.FilterRegistrationBean<>(jwt);
        registration.setEnabled(false);
        return registration;
    }
}
