package br.com.clinica.service;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EspecialidadePacoteMigrationContractTest {
    @Test void pacoteTemVersaoAplicacaoAuditoriaEIsolamento() throws Exception {
        String sql = Files.readString(Path.of("..","database","migrations","028_pacotes_especialidade.sql"));
        assertTrue(sql.contains("especialidade_pacote_versao"));
        assertTrue(sql.contains("UNIQUE(id_clinica,id_pacote,versao)"));
        assertTrue(sql.contains("UNIQUE(id_clinica,id_pacote)"));
        assertTrue(sql.contains("especialidade_pacote_evento"));
        assertTrue(sql.contains("FORCE ROW LEVEL SECURITY"));
        assertTrue(sql.contains("WITH CHECK(id_clinica=nullif(current_setting(''app.tenant_id''"));
        assertTrue(sql.contains("VALUES (28) ON CONFLICT DO NOTHING"));
    }

    @Test void publicacaoExigeRevisaoEAplicacaoPreservaConfiguracaoLocal() throws Exception {
        String sql = Files.readString(Path.of("..","database","migrations","028_pacotes_especialidade.sql"));
        assertTrue(sql.contains("status <> 'PUBLICADA'"));
        assertTrue(sql.contains("registro_revisor IS NOT NULL"));
        assertTrue(sql.contains("configuracao_base JSONB NOT NULL"));
        assertTrue(sql.contains("configuracao_local JSONB NOT NULL"));
        assertTrue(sql.contains("personalizado BOOLEAN NOT NULL DEFAULT false"));
    }
}
