package br.com.clinica.service;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AccessAuditService {
    private static final Logger log = LoggerFactory.getLogger(AccessAuditService.class);
    private final JdbcTemplate db;
    private final TenantService tenants;
    public AccessAuditService(JdbcTemplate db, TenantService tenants) { this.db = db; this.tenants = tenants; }

    public void record(Integer tenant, Integer user, String action, String resource,
                       String method, int result, String address) {
        if (tenant == null || action == null) return;
        try {
            db.update("INSERT INTO auth_audit_log(id_clinica,id_usuario,acao,recurso,metodo,resultado,endereco_ip) "
                            + "VALUES (?,?,?,?,?,?,?)", tenant, user, action, resource, method, result, address);
        } catch (RuntimeException exception) {
            // Auditoria não pode transformar uma resposta já concluída em erro de aplicação.
            log.error("Não foi possível gravar auditoria de acesso", exception);
        }
    }

    public List<Map<String, Object>> list(int actor, int tenant, int limit) {
        var access = tenants.acesso(actor, tenant);
        if (!access.permissoes().contains("auditoria.visualizar")
                && !access.permissoes().contains("auditoria.financeira.visualizar"))
            throw AccessControlService.forbidden();
        int safeLimit = Math.max(1, Math.min(limit, 200));
        String financialScope = access.permissoes().contains("auditoria.visualizar") ? "" :
                " AND (a.acao LIKE 'caixa.%' OR a.acao LIKE 'despesa.%' OR a.acao LIKE 'glosa.%' "
                        + "OR a.acao LIKE 'convenio.%' OR a.acao LIKE 'fatura.%' "
                        + "OR a.acao LIKE 'pagamento.%' OR a.acao LIKE 'repasse.%')";
        return db.queryForList("SELECT a.id,a.id_usuario,u.nome AS usuario,a.acao,a.recurso,a.metodo,a.resultado,"
                        + "a.endereco_ip,a.criado_em FROM auth_audit_log a LEFT JOIN usuario u ON u.id=a.id_usuario "
                        + "WHERE a.id_clinica=?" + financialScope + " ORDER BY a.criado_em DESC LIMIT ?",
                tenant, safeLimit);
    }
}
