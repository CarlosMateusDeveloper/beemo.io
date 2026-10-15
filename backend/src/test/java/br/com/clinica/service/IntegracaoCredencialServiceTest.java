package br.com.clinica.service;

import br.com.clinica.dto.IntegracaoCredencialRequest;
import br.com.clinica.integration.ClinicaIntegrationAdapters;
import br.com.clinica.integration.ClinisisStandardAdapter;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IntegracaoCredencialServiceTest {
    @AfterEach void clear() { TenantContext.clear(); }

    @Test void hashDaCredencialEDeterministicoENaoArmazenaOValorOriginal() {
        String token = "cls.identificador.um-segredo-com-alta-entropia";
        String hash = IntegracaoCredencialService.sha256(token);

        assertEquals(64, hash.length());
        assertNotEquals(token, hash);
        assertEquals(hash, IntegracaoCredencialService.sha256(token));
    }

    @Test void normalizaSituacaoSemDependerDeAcentosOuFormatacao() {
        assertEquals("consulta_realizada", IntegracaoCredencialService.normalize(" Consulta Realizada "));
        assertEquals("nao_compareceu", IntegracaoCredencialService.normalize("Não compareceu"));
    }

    @Test void autorizacaoFinanceiraNaoPodeSerInterpretadaComoPresenca() {
        var db = mock(JdbcTemplate.class);
        when(db.queryForObject(anyString(), eq(Long.class), any(), any(), any(), any(), any()))
                .thenReturn(12L);
        var adapters = new ClinicaIntegrationAdapters(java.util.List.of(new ClinisisStandardAdapter()));
        var service = new IntegracaoCredencialService(db, adapters);
        TenantContext.set(new TenantService.Access(4, "Clínica", "administrador",
                java.util.List.of("administrador"), Set.of("integracao.gerenciar"), null));
        var request = new IntegracaoCredencialRequest(
                "sistema", "Sistema da clínica", "America/Fortaleza", "Produção",
                "clinisis-standard-v1", Set.of("eventos:escrever"), Map.of("Autorizado", "REALIZADO"));

        var error = assertThrows(ResponseStatusException.class, () -> service.criar(request, 9));

        assertEquals(400, error.getStatusCode().value());
        assertTrue(error.getReason().contains("presença"));
        verify(db, never()).update(contains("auth_integracao_credencial"), any(Object[].class));
    }
}
