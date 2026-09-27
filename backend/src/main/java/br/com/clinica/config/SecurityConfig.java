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
    public SecurityFilterChain apiSecurity(HttpSecurity http, @org.springframework.beans.factory.annotation.Value("${app.auth.secure-cookies:false}") boolean secureCookies) throws Exception {
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
                    res.getWriter().write("{\"message\":\"Faça login para continuar.\"}");
                })
                .accessDeniedHandler((req,res,ex) -> {
                    res.setStatus(403);res.setContentType("application/json");res.setCharacterEncoding("UTF-8");
                    boolean csrfError=ex instanceof org.springframework.security.web.csrf.CsrfException;
                    res.getWriter().write(csrfError
                        ? "{\"code\":\"csrf_invalid\",\"message\":\"Atualize a página e tente novamente.\"}"
                        : "{\"message\":\"Seu perfil não tem permissão para esta ação.\"}");
                }))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/api/auth/login","/api/auth/magic-link","/api/auth/magic-link/verify",
                    "/api/auth/csrf","/api/auth/providers","/api/auth/logout","/error").permitAll()
                .requestMatchers("/api/usuarios/**").hasRole("ADMINISTRADOR")
                .anyRequest().authenticated())
            .addFilterBefore(jwt,UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
    @Bean public PasswordEncoder passwordEncoder() { return new MigratingPasswordEncoder(); }

    // O filtro só participa da cadeia da API, nunca da cadeia de OAuth.
    @Bean public org.springframework.boot.web.servlet.FilterRegistrationBean<JwtAuthFilter> jwtRegistration() {
        var registration=new org.springframework.boot.web.servlet.FilterRegistrationBean<>(jwt);
        registration.setEnabled(false);
        return registration;
    }
}
