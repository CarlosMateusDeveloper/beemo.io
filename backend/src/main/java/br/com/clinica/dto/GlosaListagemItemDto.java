package br.com.clinica.dto;

import java.math.BigDecimal;

// Linha da fila de glosas (docs/specs/recuperacao-glosas.md, seção 3) —
// ordenada por prazo crescente no frontend. corPrazo: verde (>7d) / amarelo
// (3-7d) / vermelho (<3d) / preto (expirado) / null (sem prazo definido).
public record GlosaListagemItemDto(
        Integer id,
        String pacienteNome,
        String convenioNome,
        String procedimento,
        BigDecimal valorGlosado,
        String dataGlosaTxt,
        String prazoRecursoTxt,
        Integer diasRestantes,
        String corPrazo,
        String status,
        String statusRecursoAtual,
        String responsavelNome
) {
}
