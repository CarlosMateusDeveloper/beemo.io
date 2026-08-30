package br.com.clinica.dto;

import java.math.BigDecimal;

// Spec seção 12 — registrado quando o convênio responde ao recurso.
public record RecursoGlosaResultadoRequest(
        BigDecimal valorRecuperado,
        BigDecimal valorNaoRecuperado,
        String motivoNegativa,
        String protocoloResposta,
        String documentoRespostaUrl
) {
}
