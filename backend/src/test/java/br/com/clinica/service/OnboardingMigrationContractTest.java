package br.com.clinica.service;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OnboardingMigrationContractTest {
    @Test void onboardingTemIsolamentoAuditoriaERollback() throws Exception {
        String sql = Files.readString(Path.of("..", "database", "migrations", "026_onboarding_clinicas.sql"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS onboarding_clinica"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS onboarding_evento"));
        assertTrue(sql.contains("FORCE ROW LEVEL SECURITY"));
        assertTrue(sql.contains("WITH CHECK(id_clinica=nullif(current_setting(''app.tenant_id''"));
        assertTrue(sql.contains("rollback_relatorio JSONB"));
        assertTrue(sql.contains("desfeita_por INTEGER"));
        assertTrue(sql.contains("VALUES (26) ON CONFLICT DO NOTHING"));
    }

    @Test void arquivoOriginalEPersistidoSomenteProtegido() throws Exception {
        String sql = Files.readString(Path.of("..", "database", "migrations", "026_onboarding_clinicas.sql"));
        assertTrue(sql.contains("arquivo_conteudo_criptografado BYTEA"));
        assertTrue(sql.contains("octet_length(arquivo_nonce)=12"));
        assertTrue(sql.contains("arquivo_chave_versao=1"));
    }
}
