package br.com.clinica.dto;

import java.math.BigDecimal;

// Linha da lista da aba Auditoria (docs/specs/convenios.md, seção "Aba —
// Auditoria"). status: aprovado / atencao / bloqueado.
public record AuditoriaListagemItemDto(
        Integer id,
        String pacienteNome,
        String procedimento,
        String convenioNome,
        String status,
        BigDecimal valorEmRisco,
        String avaliadoEmTxt
) {
}
