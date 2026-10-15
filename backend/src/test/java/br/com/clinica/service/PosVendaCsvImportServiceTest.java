package br.com.clinica.service;

import java.time.Instant;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class PosVendaCsvImportServiceTest {
    @Test void parserAceitaSeparadorAspasEQuebraDentroDoCampo() {
        var rows = PosVendaCsvImportService.parseCsv(
                "id;nome;situacao\r\n1;\"Maria; Silva\";Faltou\r\n2;\"João\nSouza\";Realizado", ';');
        assertEquals(3, rows.size());
        assertEquals("Maria; Silva", rows.get(1).get(1));
        assertEquals("João\nSouza", rows.get(2).get(1));
    }

    @Test void detectaColunasComunsSemDependerDaOrdem() {
        var mapping = PosVendaCsvImportService.detectarMapeamento(List.of(
                "Situação", "Nome Paciente", "ID Atendimento", "Celular", "ID Paciente", "Data Hora",
                "Médico", "Especialidade", "Procedimento"));
        assertEquals("ID Paciente", mapping.get("pacienteId"));
        assertEquals("ID Atendimento", mapping.get("atendimentoId"));
        assertEquals("Situação", mapping.get("situacao"));
    }

    @Test void faturamentoNuncaEInferidoComoComparecimento() {
        assertEquals("FALTOU", PosVendaCsvImportService.sugerirSituacao("Não compareceu"));
        assertEquals("REALIZADO", PosVendaCsvImportService.sugerirSituacao("Atendido"));
        assertEquals("DESCONHECIDO", PosVendaCsvImportService.sugerirSituacao("Faturado"));
        assertEquals("", PosVendaCsvImportService.sugerirSituacao("Status próprio do fornecedor"));
    }

    @Test void eventoAntigoNaoSobrescreveAtualizacaoMaisNova() {
        Instant current = Instant.parse("2026-10-10T12:00:00Z");
        assertFalse(PosVendaCsvImportService.newer(Instant.parse("2026-10-10T11:59:00Z"), current));
        assertTrue(PosVendaCsvImportService.newer(Instant.parse("2026-10-10T12:01:00Z"), current));
        assertFalse(PosVendaCsvImportService.newer(null, current));
    }

    @Test void previewReportaIdentificadorMaiorQueOContratoSemTruncar() {
        String id = "P".repeat(161);
        String csv = "paciente_id;nome_paciente;telefone;atendimento_id;data_hora;profissional;especialidade;procedimento;status\n"
                + id + ";Paciente fictício;85999999999;A-1;10/10/2026 09:00;Dra. Ana;Clínica;Consulta;Faltou";
        var service = new PosVendaCsvImportService(null, Clock.systemUTC());
        var file = new MockMultipartFile("arquivo", "piloto.csv", "text/csv", csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        var preview = service.preview(file, "America/Fortaleza");

        assertEquals(1, preview.get("linhasComErro"));
        var sample = (List<?>) preview.get("amostra");
        var row = (java.util.Map<?, ?>) sample.getFirst();
        assertEquals("campo_muito_longo", row.get("codigo"));
        assertEquals("pacienteId", row.get("campo"));
    }

    @Test void previewRejeitaArquivoSemDados() {
        var service = new PosVendaCsvImportService(null, Clock.systemUTC());
        var file = new MockMultipartFile("arquivo", "vazio.csv", "text/csv", "paciente_id;nome_paciente\n".getBytes());

        var error = assertThrows(ResponseStatusException.class,
                () -> service.preview(file, "America/Fortaleza"));

        assertEquals("O CSV não possui linhas de dados", error.getReason());
    }

    @Test void previewBloqueiaImportadoSemEvidenciaDeConsentimento() {
        String csv = "paciente_id;nome_paciente;telefone;atendimento_id;data_hora;profissional;especialidade;procedimento;status\n"
                + "P-1;Paciente;85999999999;A-1;10/10/2026 09:00;Dra. Ana;Clínica;Consulta;Faltou";
        var service = new PosVendaCsvImportService(null, Clock.systemUTC());
        var file = new MockMultipartFile("arquivo", "piloto.csv", "text/csv", csv.getBytes());

        var preview = service.preview(file, "America/Fortaleza");
        var row = (java.util.Map<?, ?>) ((List<?>) preview.get("amostra")).getFirst();

        assertEquals("Bloqueado até comprovação", row.get("consentimento"));
    }

    @Test void previewExigeTextoDoConsentimentoConcedido() {
        String csv = "paciente_id;nome_paciente;telefone;atendimento_id;data_hora;profissional;especialidade;procedimento;status;consentimento;data_consentimento\n"
                + "P-1;Paciente;85999999999;A-1;10/10/2026 09:00;Dra. Ana;Clínica;Consulta;Faltou;sim;10/10/2026 08:00";
        var service = new PosVendaCsvImportService(null, Clock.systemUTC());
        var file = new MockMultipartFile("arquivo", "piloto.csv", "text/csv", csv.getBytes());

        var preview = service.preview(file, "America/Fortaleza");
        var row = (java.util.Map<?, ?>) ((List<?>) preview.get("amostra")).getFirst();

        assertEquals("consentimento_sem_texto", row.get("codigo"));
    }
}
