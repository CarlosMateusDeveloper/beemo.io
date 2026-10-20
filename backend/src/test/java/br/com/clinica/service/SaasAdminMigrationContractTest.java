package br.com.clinica.service;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SaasAdminMigrationContractTest {
    @Test void painelInternoUsaProjecoesSemBypassDeRls() throws Exception {
        String sql=Files.readString(Path.of("..","database","migrations","030_saas_painel_interno.sql"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS auth_saas_funcionario"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS auth_saas_clinica_indicador"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS auth_saas_acao"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS auth_saas_audit_log"));
        assertFalse(sql.contains("app.saas_internal"));
        assertFalse(sql.contains("prontuario"));
        assertFalse(sql.contains("payload JSONB"));
        assertTrue(sql.contains("VALUES (30) ON CONFLICT DO NOTHING"));
    }

    @Test void acessoInternoTemPermissaoPropriaEReautenticacao() throws Exception {
        String service=Files.readString(Path.of("src","main","java","br","com","clinica","service","SaasAdminService.java"));
        String filter=Files.readString(Path.of("src","main","java","br","com","clinica","config","JwtAuthFilter.java"));
        assertTrue(service.contains("s.autenticado_em>now()-interval '8 hours'"));
        assertTrue(service.contains("saas.dashboard.visualizar"));
        assertTrue(filter.contains("SAAS_"));
        assertFalse(service.contains("prontuario"));
    }
}
