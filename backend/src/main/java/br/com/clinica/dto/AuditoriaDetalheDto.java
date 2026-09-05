package br.com.clinica.dto;

import java.math.BigDecimal;
import java.util.List;

// Detalhe de um atendimento auditado (spec: "Auditoria — Atendimento #1829",
// checklist de itens com ✓/✕, severidade, ação recomendada).
public record AuditoriaDetalheDto(
        Integer id,
        String status,
        BigDecimal valorEmRisco,
        String avaliadoEmTxt,
        String convenioNome,
        AtendimentoResumoDto atendimento,
        List<ItemDto> itens
) {

    public record AtendimentoResumoDto(String pacienteNome, String procedimento, String profissionalNome, String dataTxt) {
    }

    public record ItemDto(String status, String descricao, String severidade, String acaoRecomendada) {
    }
}
