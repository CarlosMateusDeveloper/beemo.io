package br.com.clinica.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ExportacaoContinuidadeMigrationContractTest {
    @Test void exportacaoPermaneceCifradaAuditavelETenantScoped() throws Exception {
        String migration = Files.readString(Path.of("../database/migrations/029_exportacao_continuidade.sql"));
        String service = Files.readString(Path.of("src/main/java/br/com/clinica/service/ExportacaoDadosService.java"));
        String docs = Files.readString(Path.of("../docs/continuidade-operacional.md"));
        assertTrue(migration.contains("arquivo_criptografado"));
        assertTrue(migration.contains("FORCE ROW LEVEL SECURITY"));
        assertTrue(migration.contains("token_hash"));
        assertTrue(service.contains("AES/GCM/NoPadding"));
        assertTrue(service.contains("afterCommit"));
        assertTrue(service.contains("set_config('app.tenant_id'"));
        assertFalse(service.contains("BYPASSRLS"));
        assertTrue(docs.contains("RTO"));
        assertTrue(docs.contains("RPO"));
    }
}
