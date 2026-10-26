package br.com.clinica.service;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PreAtendimentoMigrationContractTest {
    @Test void contratoProtegeEscopoExpiracaoConcorrenciaEIsolamento() throws Exception {
        String sql=Files.readString(Path.of("..","database","migrations","033_pre_atendimento_seguro.sql"));
        assertTrue(sql.contains("token_hash CHAR(64) PRIMARY KEY"));
        assertTrue(sql.contains("expira_em TIMESTAMPTZ NOT NULL"));
        assertTrue(sql.contains("revogado_em TIMESTAMPTZ"));
        assertTrue(sql.contains("max_tentativas"));
        assertTrue(sql.contains("UNIQUE(id_clinica,id_consulta)"));
        assertTrue(sql.contains("UNIQUE(id_clinica,id_pre_atendimento,sha256)"));
        assertTrue(sql.contains("FORCE ROW LEVEL SECURITY"));
        assertTrue(sql.contains("id_formulario BIGINT NOT NULL"));
        assertFalse(sql.contains("token VARCHAR"));
    }
}
