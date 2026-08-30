package br.com.clinica.dto;

import java.math.BigDecimal;
import java.util.List;

// Painel de análise de uma glosa (spec seção 4) — atendimento, documentos
// disponíveis (checklist computado, não cadastrado à mão), histórico e o
// recurso mais recente (se existir).
public record GlosaDetalheDto(
        Integer id,
        BigDecimal valorGlosado,
        BigDecimal valorFaturado,
        String motivo,
        String codigoMotivo,
        String dataGlosaTxt,
        String prazoRecursoTxt,
        Integer diasRestantes,
        String corPrazo,
        String status,
        String origem,
        String recorribilidade,
        String categoriaMotivo,
        String convenioNome,
        String responsavelNome,
        AtendimentoResumoDto atendimento,
        DocumentosDisponiveisDto documentosDisponiveis,
        List<HistoricoItemDto> historico,
        RecursoGlosaDto recursoAtual
) {

    public record AtendimentoResumoDto(String pacienteNome, String procedimento, String profissionalNome, String dataTxt) {
    }

    public record DocumentosDisponiveisDto(boolean prontuario, boolean guia, boolean solicitacaoMedica, boolean autorizacao) {
    }

    public record HistoricoItemDto(String dataHoraTxt, String evento, String usuarioNome) {
    }
}
