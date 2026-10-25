package br.com.clinica.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

@ExtendWith(OutputCaptureExtension.class)
class AccessAuditLogSafetyTest {
    @Test void falhaDeAuditoriaNaoRegistraMensagemComDadosClinicos(CapturedOutput output) {
        var db = mock(JdbcTemplate.class);
        String sensitive = "paciente-segredo-123";
        doThrow(new IllegalStateException(sensitive)).when(db).update(anyString(),
                any(), any(), any(), any(), any(), any(), any());

        new AccessAuditService(db, mock(TenantService.class))
                .record(1, 10, "prontuario.visualizar", "prontuario", "GET", 200, "127.0.0.1");

        assertTrue(output.getOut().contains("Não foi possível gravar auditoria"));
        assertFalse(output.getAll().contains(sensitive));
    }

    @Test void falhaDeExportacaoNaoExibeMensagemDaExcecao() {
        assertFalse(ExportacaoDadosService.safeError(new IllegalStateException("prontuario secreto"))
                .contains("prontuario secreto"));
    }
}
