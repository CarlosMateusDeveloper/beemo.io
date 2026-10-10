package br.com.clinica.config;

import br.com.clinica.service.MigratingPasswordEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.http.HttpMethod;
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
    public SecurityFilterChain apiSecurity(HttpSecurity http, TenantDatabaseFilter tenantDatabase,
            ClinicalLegacyScopeFilter clinicalScope, AccessAuditFilter accessAudit,
            @org.springframework.beans.factory.annotation.Value("${app.auth.secure-cookies:false}") boolean secureCookies) throws Exception {
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
                .requestMatchers("/api/usuarios/**").hasAuthority("PERM_usuario.gerenciar")

                .requestMatchers(HttpMethod.POST,"/api/dashboard","/api/v1/dashboard")
                    .hasAnyAuthority("PERM_dashboard.operacional.visualizar","PERM_dashboard.financeiro.visualizar")

                .requestMatchers(HttpMethod.GET,"/api/pacientes/**").hasAuthority("PERM_paciente.visualizar")
                .requestMatchers(HttpMethod.POST,"/api/pacientes/**").hasAuthority("PERM_paciente.cadastrar")
                .requestMatchers(HttpMethod.PUT,"/api/pacientes/**").hasAuthority("PERM_paciente.editar")
                .requestMatchers(HttpMethod.DELETE,"/api/pacientes/**").hasAuthority("PERM_paciente.editar")

                .requestMatchers(HttpMethod.GET,"/api/prontuarios/**","/api/alergias/**","/api/comorbidades/**",
                    "/api/cirurgias-previas/**","/api/documentos-clinicos/**","/api/encaminhamentos/**",
                    "/api/exames/**","/api/itens-prescricao/**","/api/medicamentos-uso-continuo/**",
                    "/api/resultados-exame/**","/api/sinais-vitais/**","/api/solicitacoes-exame/**",
                    "/api/diagnosticos-cid/**","/api/diagnosticos-ciap2/**").hasAuthority("PERM_prontuario.visualizar")
                .requestMatchers("/api/prontuarios/**","/api/alergias/**","/api/comorbidades/**",
                    "/api/cirurgias-previas/**","/api/documentos-clinicos/**","/api/encaminhamentos/**",
                    "/api/exames/**","/api/itens-prescricao/**","/api/medicamentos-uso-continuo/**",
                    "/api/resultados-exame/**","/api/sinais-vitais/**","/api/solicitacoes-exame/**",
                    "/api/diagnosticos-cid/**","/api/diagnosticos-ciap2/**").hasAuthority("PERM_prontuario.editar")

                .requestMatchers(HttpMethod.GET,"/api/consultas/**").hasAuthority("PERM_agenda.visualizar")
                .requestMatchers("/api/consultas/**").hasAuthority("PERM_agenda.gerenciar")
                .requestMatchers(HttpMethod.POST,"/api/medicos/painel").hasAuthority("PERM_medico.indicadores.visualizar")
                .requestMatchers(HttpMethod.GET,"/api/medicos/**","/api/especialidades/**").hasAuthority("PERM_medico.visualizar")
                .requestMatchers("/api/medicos/**","/api/especialidades/**").hasAuthority("PERM_medico.gerenciar")

                .requestMatchers(HttpMethod.POST,"/api/glosas/*/aceitar").hasAuthority("PERM_glosa.aceitar_perda")
                .requestMatchers(HttpMethod.GET,"/api/glosas/**","/api/recursos/**").hasAuthority("PERM_glosa.visualizar")
                .requestMatchers("/api/glosas/**","/api/recursos/**").hasAuthority("PERM_glosa.recorrer")
                .requestMatchers(HttpMethod.GET,"/api/convenios").hasAnyAuthority("PERM_paciente.visualizar","PERM_convenio.gerenciar")
                .requestMatchers("/api/convenios/**","/api/faturas/**","/api/lotes/**",
                    "/api/autorizacoes-convenio/**","/api/auditoria/**").hasAuthority("PERM_convenio.gerenciar")

                .requestMatchers(HttpMethod.GET,"/api/caixa/turno-atual").hasAuthority("PERM_caixa.visualizar")
                .requestMatchers(HttpMethod.GET,"/api/caixa/dre").hasAuthority("PERM_financeiro.dre.visualizar")
                .requestMatchers(HttpMethod.GET,"/api/caixa/fluxo-consolidado").hasAuthority("PERM_financeiro.fluxo.visualizar")
                .requestMatchers(HttpMethod.POST,"/api/caixa/pagamentos").hasAuthority("PERM_caixa.receber")
                .requestMatchers(HttpMethod.POST,"/api/caixa/turno/fechar").hasAuthority("PERM_caixa.fechar")
                .requestMatchers(HttpMethod.POST,"/api/despesas/*/pagar","/api/despesas/*/cancelar").hasAuthority("PERM_despesa.aprovar")
                .requestMatchers(HttpMethod.GET,"/api/despesas/**").hasAuthority("PERM_despesa.visualizar")
                .requestMatchers("/api/despesas/**").hasAuthority("PERM_despesa.gerenciar")

                .requestMatchers(HttpMethod.PUT,"/api/retorno/reguas/**").hasAuthority("PERM_jornada.configurar")
                .requestMatchers(HttpMethod.PUT,"/api/retorno/modelos/**").hasAuthority("PERM_campanha.configurar")
                .requestMatchers(HttpMethod.GET,"/api/pos-venda/**","/api/retorno/**").hasAuthority("PERM_oportunidade.visualizar")
                .requestMatchers("/api/pos-venda/**","/api/retorno/**").hasAuthority("PERM_oportunidade.gerenciar")
                .anyRequest().denyAll())
            .addFilterBefore(jwt,UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(tenantDatabase,JwtAuthFilter.class)
            .addFilterAfter(clinicalScope,TenantDatabaseFilter.class)
            .addFilterAfter(accessAudit,ClinicalLegacyScopeFilter.class);
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
    @Bean public org.springframework.boot.web.servlet.FilterRegistrationBean<AccessAuditFilter> auditRegistration(AccessAuditFilter filter) {
        var registration=new org.springframework.boot.web.servlet.FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
    @Bean public org.springframework.boot.web.servlet.FilterRegistrationBean<ClinicalLegacyScopeFilter> clinicalScopeRegistration(ClinicalLegacyScopeFilter filter) {
        var registration=new org.springframework.boot.web.servlet.FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
