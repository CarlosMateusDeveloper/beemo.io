package br.com.clinica.config;

import br.com.clinica.service.AccessAuditService;
import br.com.clinica.service.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AccessAuditFilter extends OncePerRequestFilter {
    private final AccessAuditService audit;
    public AccessAuditFilter(AccessAuditService audit) { this.audit = audit; }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                               FilterChain chain) throws ServletException, IOException {
        String action = action(request);
        try { chain.doFilter(request, response); }
        finally {
            var access = TenantContext.get();
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            Integer user = authentication != null && authentication.getPrincipal() instanceof Integer id ? id : null;
            audit.record(access == null ? null : access.id(), user, action, request.getRequestURI(),
                    request.getMethod(), response.getStatus(), remoteAddress(request));
        }
    }

    private String action(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        if (isClinical(path)) return method.equals("GET") ? "prontuario.visualizar" : "prontuario.editar";
        if (path.matches("/api/tenants/\\d+/members/\\d+")) return "permissao.gerenciar";
        if (path.matches("/api/glosas/\\d+/aceitar")) return "glosa.aceitar_perda";
        if (path.matches("/api/despesas/\\d+/(pagar|cancelar)")) return "despesa.aprovar";
        if (path.equals("/api/caixa/turno/fechar")) return "caixa.fechar";
        if (path.startsWith("/api/faturas") && !method.equals("GET")) return "fatura.editar";
        if (path.startsWith("/api/medicos") && !method.equals("GET")) return "repasse.editar";
        if (path.startsWith("/api/retorno/modelos") && !method.equals("GET")) return "campanha.configurar";
        return null;
    }

    private boolean isClinical(String path) {
        return java.util.List.of("/api/prontuarios", "/api/alergias", "/api/comorbidades",
                "/api/cirurgias-previas", "/api/documentos-clinicos", "/api/encaminhamentos",
                "/api/exames", "/api/itens-prescricao", "/api/medicamentos-uso-continuo",
                "/api/sinais-vitais", "/api/solicitacoes-exame")
                .stream().anyMatch(path::startsWith);
    }

    private String remoteAddress(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String value = forwarded == null ? request.getRemoteAddr() : forwarded.split(",", 2)[0].trim();
        return value == null ? null : value.substring(0, Math.min(value.length(), 64));
    }
}
