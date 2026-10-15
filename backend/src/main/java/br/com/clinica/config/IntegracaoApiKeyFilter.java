package br.com.clinica.config;

import br.com.clinica.service.IntegracaoCredencialService;
import br.com.clinica.service.TenantContext;
import br.com.clinica.service.TenantService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class IntegracaoApiKeyFilter extends OncePerRequestFilter {
    private final IntegracaoCredencialService credentials;
    public IntegracaoApiKeyFilter(IntegracaoCredencialService credentials) { this.credentials = credentials; }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getServletPath().startsWith("/api/integracoes/v1/");
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                                FilterChain chain) throws ServletException, IOException {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }
        var principal = credentials.autenticar(authorization.substring(7).trim());
        if (principal.isEmpty()) {
            response.setStatus(401);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"error\":{\"code\":\"integration_credential_invalid\",\"message\":\"Credencial de integração inválida ou revogada.\"}}");
            return;
        }
        var integration = principal.get();
        var authorities = new ArrayList<SimpleGrantedAuthority>();
        integration.escopos().forEach(scope -> authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope)));
        TenantContext.set(new TenantService.Access(integration.tenantId(), "Integração", "integracao",
                java.util.List.of(), java.util.Set.of(), null));
        request.setAttribute("clinisis.integration.source", integration.fonteId());
        request.setAttribute("clinisis.integration.credential", integration.credencialId());
        try {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(integration, null, authorities));
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
            TenantContext.clear();
        }
    }
}
