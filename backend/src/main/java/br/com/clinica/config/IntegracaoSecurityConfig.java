package br.com.clinica.config;

import br.com.clinica.service.IntegracaoCredencialService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@Profile("!clinical-migration")
public class IntegracaoSecurityConfig {
    @Bean
    IntegracaoApiKeyFilter integrationApiKeyFilter(IntegracaoCredencialService credentials) {
        return new IntegracaoApiKeyFilter(credentials);
    }

    @Bean @Order(1)
    SecurityFilterChain integrationSecurity(HttpSecurity http, IntegracaoApiKeyFilter apiKey,
                                             TenantDatabaseFilter tenantDatabase) throws Exception {
        http.securityMatcher("/api/integracoes/v1/**")
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(csrf -> csrf.disable())
            .requestCache(cache -> cache.disable())
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request,response,error) -> {
                    response.setStatus(401); response.setContentType("application/json");
                    response.setCharacterEncoding("UTF-8");
                    response.getWriter().write("{\"error\":{\"code\":\"integration_credential_required\",\"message\":\"Informe uma credencial de integração válida.\"}}");
                })
                .accessDeniedHandler((request,response,error) -> {
                    response.setStatus(403); response.setContentType("application/json");
                    response.setCharacterEncoding("UTF-8");
                    response.getWriter().write("{\"error\":{\"code\":\"integration_scope_required\",\"message\":\"A credencial não possui o escopo necessário.\"}}");
                }))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(HttpMethod.GET, "/api/integracoes/v1/openapi.yaml").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/integracoes/v1/eventos").hasAuthority("SCOPE_eventos:escrever")
                .requestMatchers(HttpMethod.GET, "/api/integracoes/v1/estado").hasAuthority("SCOPE_estado:ler")
                .anyRequest().denyAll())
            .addFilterBefore(apiKey, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(tenantDatabase, IntegracaoApiKeyFilter.class);
        return http.build();
    }

    @Bean
    org.springframework.boot.web.servlet.FilterRegistrationBean<IntegracaoApiKeyFilter> integrationKeyRegistration(
            IntegracaoApiKeyFilter filter) {
        var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
