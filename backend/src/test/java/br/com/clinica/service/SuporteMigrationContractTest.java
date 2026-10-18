package br.com.clinica.service;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SuporteMigrationContractTest {
    private String sql() throws Exception { return Files.readString(Path.of("..","database","migrations","029_suporte_auditado.sql")); }

    @Test void chamadosSaoIsoladosPorTenantETemHistorico() throws Exception {
        String sql=sql();
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS suporte_chamado"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS suporte_chamado_mensagem"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS suporte_chamado_evento"));
        assertTrue(sql.contains("FORCE ROW LEVEL SECURITY"));
        assertTrue(sql.contains("WITH CHECK(id_clinica=nullif(current_setting(''app.tenant_id''"));
    }

    @Test void concessaoExpiraNaoPodeSerAutoaprovadaETodaAcaoEAuditada() throws Exception {
        String sql=sql();
        assertTrue(sql.contains("expira_em <= solicitado_em + interval '8 hours'"));
        assertTrue(sql.contains("id_autorizador <> id_solicitante"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS auth_suporte_acesso_evento"));
        assertTrue(sql.contains("'ACAO'"));
        assertTrue(sql.contains("VALUES (29) ON CONFLICT DO NOTHING"));
    }

    @Test void servicoRevogaSessaoEAutenticacaoRevalidaPrazo() throws Exception {
        String service=Files.readString(Path.of("src","main","java","br","com","clinica","service","SuporteService.java"));
        String tenants=Files.readString(Path.of("src","main","java","br","com","clinica","service","TenantService.java"));
        String audit=Files.readString(Path.of("src","main","java","br","com","clinica","service","AccessAuditService.java"));
        assertTrue(service.contains("actor==agent || actor==requester"));
        assertTrue(service.contains("UPDATE auth_session SET id_clinica=NULL"));
        assertTrue(tenants.contains("a.expira_em>now()"));
        assertTrue(tenants.contains("a.revogado_em IS NULL"));
        assertTrue(audit.contains("auth_suporte_acesso_evento"));
    }
}
