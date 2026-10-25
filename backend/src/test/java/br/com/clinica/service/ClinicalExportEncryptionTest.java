package br.com.clinica.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClinicalExportEncryptionTest {
    @Test void authorizedExportDecryptsClinicalFieldsOnlyWithMatchingContext() {
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        var cipher = new ClinicalTextCipher("v1", key, "", false);
        var json = new ObjectMapper();
        var exporter = new ExportacaoDadosService(mock(JdbcTemplate.class), mock(AccessControlService.class),
                Runnable::run, mock(PlatformTransactionManager.class), cipher,
                mock(ProntuarioConteudoCifradoService.class), json, key);

        String chartCiphertext = cipher.encrypt("Evolução clínica çã", 7, "prontuario", "descricao", 12);
        String chartRow = "{\"id_clinica\":7,\"id_prontuario\":12,\"descricao\":\"" + chartCiphertext + "\"}";
        String chartExport = exporter.exportRow("prontuario", chartRow, 7);
        assertEquals("Evolução clínica çã", json.readTree(chartExport).get("descricao").asText());
        assertFalse(chartExport.contains(chartCiphertext));
        assertThrows(IllegalStateException.class, () -> exporter.exportRow("prontuario", chartRow, 8));

        String documentCiphertext = cipher.encrypt("Atestado médico", 7, "documento_clinico", "texto", 21);
        String documentRow = "{\"id_clinica\":7,\"id_documento_clinico\":21,\"texto\":\"" + documentCiphertext + "\"}";
        assertEquals("Atestado médico", json.readTree(exporter.exportRow("documento_clinico", documentRow, 7))
                .get("texto").asText());
        assertEquals("{\"id_clinica\":7}", exporter.exportRow("paciente", "{\"id_clinica\":7}", 7));
    }

    @Test void legacySidecarIsReadableInAuthorizedExport() {
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        var cipher = new ClinicalTextCipher("v1", key, "", false);
        var legacy = mock(ProntuarioConteudoCifradoService.class);
        when(legacy.load(12)).thenReturn(new ProntuarioConteudoCifradoService.Conteudo(
                "Queixa lateral", null, "Descrição lateral", null, null, null, null, null, null));
        var json = new ObjectMapper();
        var exporter = new ExportacaoDadosService(mock(JdbcTemplate.class), mock(AccessControlService.class),
                Runnable::run, mock(PlatformTransactionManager.class), cipher, legacy, json, key);
        TenantContext.set(new TenantService.Access(7, "Clínica de teste", "administrador"));
        try {
            String row = "{\"id_clinica\":7,\"id_prontuario\":12,\"descricao\":null}";
            String exported = exporter.exportRow("prontuario", row, 7);
            assertEquals("Descrição lateral", json.readTree(exported).get("descricao").asText());
            assertEquals("Queixa lateral", json.readTree(exported).get("queixa_principal").asText());
        } finally {
            TenantContext.clear();
        }
    }

}
