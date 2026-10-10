package br.com.clinica.service;

import br.com.clinica.config.*;
import br.com.clinica.controller.*;
import br.com.clinica.dto.*;
import br.com.clinica.model.Usuario;
import br.com.clinica.repository.UsuarioRepository;
import jakarta.servlet.FilterChain;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.boot.micrometer.observation.autoconfigure.ObservationAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcObservationAutoConfiguration;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {AuthController.class, DashboardController.class, UsuarioController.class}, properties = {
    "app.jwt.secret=test-secret-32-bytes-long-123456789", "app.jwt.expiration-minutes=30"
})
@Import({SecurityConfig.class, JwtAuthFilter.class, AuthService.class, SessionService.class, JwtService.class,
    SessionCookies.class, AuthRateLimiter.class, RequestLoggingFilter.class, DashboardErrors.class})
@ImportAutoConfiguration({MetricsAutoConfiguration.class, SimpleMetricsExportAutoConfiguration.class,
    ObservationAutoConfiguration.class, WebMvcObservationAutoConfiguration.class})
class ApiSecurityContractTest {
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @Autowired PasswordEncoder encoder;
    @Autowired io.micrometer.core.instrument.MeterRegistry metrics;
    @MockitoBean UsuarioRepository users;
    @MockitoBean TenantService tenants;
    @MockitoBean MagicLinkService magic;
    @MockitoBean JdbcTemplate db;
    @MockitoBean TenantDatabaseFilter tenantDatabase;
    @MockitoBean DashboardService dashboard;
    Usuario user;
    AtomicBoolean active;

    @BeforeEach void prepare() throws Exception {
        user = new Usuario(); user.setId(10); user.setNome("Teste"); user.setEmail("teste@example.invalid");
        user.setSenha(encoder.encode("Test-Password-123"));
        when(users.findById(10)).thenReturn(Optional.of(user));
        when(users.findByEmailIgnoreCase("teste@example.invalid")).thenReturn(Optional.of(user));
        when(tenants.resolver(10)).thenReturn(new TenantService.Access(2, "Clínica teste", "administrador"));
        active = new AtomicBoolean(true);
        when(db.queryForObject(startsWith("SELECT EXISTS"), eq(Boolean.class), any(), any()))
            .thenAnswer(invocation -> active.get());
        when(db.update(startsWith("UPDATE auth_session SET revogada_em"), anyString())).thenAnswer(invocation -> {
            active.set(false); return 1;
        });
        doAnswer(invocation -> {
            ((FilterChain) invocation.getArgument(2)).doFilter(invocation.getArgument(0), invocation.getArgument(1)); return null;
        }).when(tenantDatabase).doFilter(any(), any(), any());
    }
    @Test void loginEmiteCookieHttpOnlySemExporTokenSenhaOuCpf() throws Exception {
        var result = mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"teste@example.invalid\",\"senha\":\"Test-Password-123\"}"))
            .andExpect(status().isOk()).andExpect(cookie().httpOnly("clinicos_session", true))
            .andExpect(cookie().maxAge("clinicos_session", 1800))
            .andExpect(jsonPath("$.token").doesNotExist()).andExpect(jsonPath("$.usuario.senha").doesNotExist())
            .andReturn();
        String token = result.getResponse().getCookie("clinicos_session").getValue();
        var claims = jwt.validar(token).orElseThrow();
        assertEquals("10", claims.getSubject());
        assertEquals(Set.of("sub", "jti", "iss", "aud", "iat", "exp"), claims.keySet());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"teste@example.invalid\",\"senha\":\"Test-Password-123\"}")).andExpect(status().isOk());
    }
    @Test void credenciaisInvalidasTemErroGenerico() throws Exception {
        for (String address : List.of("teste@example.invalid", "ausente@example.invalid"))
            mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + address + "\",\"senha\":\"incorreta\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("E-mail ou senha inválidos"));
    }
    @Test void recusaAusenteInvalidoExpiradoERevogado() throws Exception {
        var expired = new JwtService("test-secret-32-bytes-long-123456789", -1).gerar(user);
        mvc.perform(get("/api/usuarios")).andExpect(status().isUnauthorized());
        for (String token : List.of("invalido", expired))
            mvc.perform(get("/api/usuarios").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        String token = jwt.gerar(user);
        mvc.perform(get("/api/usuarios").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/logout").with(csrf()).header("Authorization", "Bearer " + token))
            .andExpect(status().isNoContent()).andExpect(cookie().maxAge("clinicos_session", 0));
        mvc.perform(get("/api/usuarios").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }
    @Test void medicoNaoPodeAdministrarUsuariosMasPodeConsultarDashboard() throws Exception {
        when(tenants.resolver(10)).thenReturn(new TenantService.Access(2, "Clínica teste", "medico"));
        String token = jwt.gerar(user);
        mvc.perform(get("/api/usuarios").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/dashboard").with(csrf()).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andExpect(header().exists("X-Request-ID"));
        verify(dashboard).calcular(new DashboardRequest("Mês", null, null, null));
    }
    @Test void dashboardRejeitaAnonimoEJsonInvalidoComErroCorrelacionado() throws Exception {
        mvc.perform(post("/api/v1/dashboard").with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/dashboard").with(csrf()).header("Authorization", "Bearer " + jwt.gerar(user))
            .header("X-Request-ID", "test-error").contentType(MediaType.APPLICATION_JSON).content("{"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("invalid_request"))
            .andExpect(jsonPath("$.requestId").value("test-error"));
        verifyNoInteractions(dashboard);
    }
    @Test void limitaTentativasDeLoginNoEndpoint() throws Exception {
        for (int i = 0; i < 15; i++) loginLimitado().andExpect(status().isUnauthorized());
        loginLimitado().andExpect(status().isTooManyRequests());
    }
    @Test void registraDuracaoEContagemPorRotaEStatus() throws Exception {
        mvc.perform(post("/api/v1/dashboard").with(csrf()).header("Authorization", "Bearer " + jwt.gerar(user)))
            .andExpect(status().isOk());
        mvc.perform(post("/api/v1/dashboard").with(csrf()).header("Authorization", "Bearer " + jwt.gerar(user))
            .contentType(MediaType.APPLICATION_JSON).content("{")).andExpect(status().isBadRequest());
        var success = metrics.get("http.server.requests").tags("uri", "/api/v1/dashboard", "status", "200").timer();
        var failure = metrics.get("http.server.requests").tags("uri", "/api/v1/dashboard", "status", "400").timer();
        assertTrue(success.count() >= 1); assertTrue(failure.count() >= 1);
        assertTrue(success.totalTime(java.util.concurrent.TimeUnit.NANOSECONDS) > 0);
    }
    private org.springframework.test.web.servlet.ResultActions loginLimitado() throws Exception {
        return mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"limite@example.invalid\",\"senha\":\"incorreta\"}"));
    }
}
