package br.com.clinica.service;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CrmMigrationContractTest {
    private final Path migration=Path.of("..","database","migrations","027_crm_conversao.sql");

    @Test void separaInteressadoPacienteEAgendamentoComRastreabilidade() throws Exception {
        String sql=Files.readString(migration);
        assertTrue(sql.contains("CREATE TABLE crm_interessado"));
        assertTrue(sql.contains("CREATE TABLE crm_oportunidade"));
        assertTrue(sql.contains("CREATE TABLE crm_oportunidade_evento"));
        assertTrue(sql.contains("FOREIGN KEY(id_clinica,id_paciente)"));
        assertTrue(sql.contains("FOREIGN KEY(id_clinica,id_consulta)"));
        assertTrue(sql.contains("trg_crm_consulta_rastrear"));
        assertTrue(sql.contains("Histórico do CRM é imutável"));
    }

    @Test void protegeTenantIdempotenciaEIdentidadeAmbigua() throws Exception {
        String sql=Files.readString(migration);
        assertTrue(sql.contains("crm_oportunidade_idempotencia_uk"));
        assertTrue(sql.contains("crm_identidade_revisao"));
        assertTrue(sql.contains("telefone_compartilhado"));
        assertTrue(sql.contains("FORCE ROW LEVEL SECURITY"));
        assertTrue(sql.contains("VALUES(27)"));
    }
}
