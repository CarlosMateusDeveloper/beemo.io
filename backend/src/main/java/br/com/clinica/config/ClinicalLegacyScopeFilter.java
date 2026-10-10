package br.com.clinica.config;

import br.com.clinica.service.AccessControlService;
import br.com.clinica.service.ClinicalScopeService;
import br.com.clinica.service.PermissionCatalog;
import br.com.clinica.service.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Fecha os endpoints clínicos legados no escopo do médico vinculado. */
@Component
public class ClinicalLegacyScopeFilter extends OncePerRequestFilter {
    private static final Set<String> RESOURCES = Set.of("alergias", "cirurgias-previas", "comorbidades",
            "documentos-clinicos", "encaminhamentos", "exames", "itens-prescricao",
            "medicamentos-uso-continuo", "sinais-vitais", "solicitacoes-exame");
    private final ClinicalScopeService scope;

    public ClinicalLegacyScopeFilter(ClinicalScopeService scope) {
        this.scope = scope;
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                               FilterChain chain) throws ServletException, IOException {
        String[] parts = request.getRequestURI().split("/");
        var tenant = TenantContext.get();
        if (parts.length < 3 || !"api".equals(parts[1]) || !RESOURCES.contains(parts[2])
                || tenant == null || !tenant.papeis().contains(PermissionCatalog.MEDICO)) {
            chain.doFilter(request, response);
            return;
        }
        try {
            String resource = parts[2];
            Integer resourceId = parts.length > 3 ? integer(parts[3]) : null;
            if ("GET".equals(request.getMethod())) {
                if (resourceId != null) scope.legacyResource(resource, resourceId);
                else validateCollection(request);
            } else if (resourceId != null && safeNestedWrite(resource, parts, request.getMethod())) {
                scope.legacyResource(resource, resourceId);
            } else {
                throw AccessControlService.forbidden();
            }
            chain.doFilter(request, response);
        } catch (org.springframework.web.server.ResponseStatusException denied) {
            response.setStatus(403);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"error\":{\"code\":\"forbidden\",\"message\":\"Seu perfil não tem permissão para este prontuário.\"},\"message\":\"Seu perfil não tem permissão para este prontuário.\"}");
        }
    }

    private void validateCollection(HttpServletRequest request) {
        Integer patient = integer(request.getParameter("idPaciente"));
        Integer chart = integer(request.getParameter("idProntuario"));
        Integer consultation = integer(request.getParameter("idConsulta"));
        if (patient != null) scope.patient(patient);
        else if (chart != null) scope.chart(chart);
        else if (consultation != null) scope.consultation(consultation);
        else throw AccessControlService.forbidden();
    }

    private boolean safeNestedWrite(String resource, String[] parts, String method) {
        return "exames".equals(resource) && parts.length > 4
                && ("status".equals(parts[4]) || "resultado".equals(parts[4]))
                && "POST".equals(method);
    }

    private Integer integer(String value) {
        if (value == null || !value.matches("\\d{1,10}")) return null;
        try { return Integer.valueOf(value); }
        catch (NumberFormatException ignored) { return null; }
    }
}
