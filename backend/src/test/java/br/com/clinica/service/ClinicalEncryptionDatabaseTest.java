package br.com.clinica.service;

import br.com.clinica.dto.DocumentoClinicoRequest;
import br.com.clinica.dto.ProntuarioSalvarRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

/** Run only against a disposable restore of the clinical database. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.jpa.hibernate.ddl-auto=none", "app.clinical-encryption.migrate=false"
})
@ActiveProfiles("clinical-migration")
@EnabledIfEnvironmentVariable(named = "CLINICAL_TEST_DB_NAME", matches = "clinisis_issue67_validation_[a-zA-Z0-9_]+")
class ClinicalEncryptionDatabaseTest {
    @Autowired JdbcTemplate db;
    @Autowired ProntuarioEscritaService charts;
    @Autowired ProntuarioDetalheService chartDetails;
    @Autowired DocumentoClinicoEscritaService documents;
    @Autowired ClinicalTextCipher cipher;

    @AfterEach void clearTenant() { TenantContext.clear(); }

    @Test
    @Transactional
    void restoredSignedChartAndNewDraftAndDocumentRoundTrip() {
        String expectedDatabase = System.getenv("CLINICAL_TEST_DB_NAME");
        assertEquals(expectedDatabase, db.queryForObject("SELECT current_database()", String.class));
        TenantContext.set(new TenantService.Access(1, "Validação", "administrador"));
        db.queryForObject("SELECT set_config('app.tenant_id', '1', true)", String.class);

        Integer signedId = db.queryForObject("""
                SELECT id_prontuario FROM prontuario
                WHERE assinado_em IS NOT NULL AND descricao IS NOT NULL
                ORDER BY id_prontuario LIMIT 1
                """, Integer.class);
        String signedCiphertext = db.queryForObject(
                "SELECT descricao FROM prontuario WHERE id_prontuario = ?", String.class, signedId);
        assertTrue(signedCiphertext.startsWith("enc:" + cipher.activeVersion() + ":"));
        var signed = chartDetails.detalharProntuario(signedId);
        assertTrue(signed.finalizado());
        assertEquals(cipher.decrypt(signedCiphertext, 1, "prontuario", "descricao", signedId), signed.descricao());

        Integer consultationId = db.queryForObject("""
                SELECT c.id_consulta FROM consulta c
                WHERE NOT EXISTS (SELECT 1 FROM prontuario p WHERE p.id_consulta = c.id_consulta)
                ORDER BY c.id_consulta LIMIT 1
                """, Integer.class);
        var draft = charts.criar(new ProntuarioSalvarRequest(consultationId, null,
                "Queixa com acentuação", null, "Evolução clínica", null, null,
                null, null, null, null, null, null, false));
        String stored = db.queryForObject(
                "SELECT descricao FROM prontuario WHERE id_prontuario = ?", String.class, draft.id());
        assertTrue(stored.startsWith("enc:" + cipher.activeVersion() + ":"));
        assertFalse(stored.contains("Evolução clínica"));
        assertFalse(chartDetails.detalharProntuario(draft.id()).finalizado());
        assertEquals("Evolução clínica", chartDetails.detalharProntuario(draft.id()).descricao());

        charts.atualizar(draft.id(), new ProntuarioSalvarRequest(consultationId, null,
                "Queixa revisada", null, "Evolução revisada", null, null,
                null, null, null, null, null, null, false));
        assertEquals("Evolução revisada", chartDetails.detalharProntuario(draft.id()).descricao());

        var document = documents.criar(new DocumentoClinicoRequest(draft.id(), "ATESTADO",
                null, null, "Texto clínico çãõ"));
        assertEquals("Texto clínico çãõ", document.texto());
        String documentStored = db.queryForObject(
                "SELECT texto FROM documento_clinico WHERE id_documento_clinico = ?", String.class, document.id());
        assertTrue(documentStored.startsWith("enc:" + cipher.activeVersion() + ":"));
        assertEquals("Texto clínico çãõ", documents.buscar(document.id()).texto());
        assertEquals("Texto clínico çãõ", documents.listar(draft.id()).getFirst().texto());

        documents.atualizar(document.id(), new DocumentoClinicoRequest(draft.id(), "ATESTADO",
                null, null, "Texto clínico atualizado"));
        assertEquals("Texto clínico atualizado", documents.buscar(document.id()).texto());
        assertThrows(IllegalStateException.class,
                () -> cipher.decrypt(documentStored, 4, "documento_clinico", "texto", document.id()));

        TenantContext.set(new TenantService.Access(4, "Outra clínica", "administrador"));
        db.queryForObject("SELECT set_config('app.tenant_id', '4', true)", String.class);
        var denied = assertThrows(ResponseStatusException.class,
                () -> chartDetails.detalharProntuario(signedId));
        assertEquals(404, denied.getStatusCode().value());
        assertTrue(documents.listar(null).isEmpty());
    }
}
