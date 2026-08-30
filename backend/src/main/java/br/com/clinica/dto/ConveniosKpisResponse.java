package br.com.clinica.dto;

import java.math.BigDecimal;

// emRisco/glosado/recuperado dependem das abas Auditoria/Glosas (fase
// futura) — zero real por enquanto, não número inventado. aReceber já é
// real, calculado a partir de `fatura`.
public record ConveniosKpisResponse(
        AReceberDto aReceber, EmRiscoDto emRisco, GlosadoDto glosado, RecuperadoDto recuperado
) {
    public record AReceberDto(BigDecimal valor, long lotes, int mediaDias) {
    }

    public record EmRiscoDto(BigDecimal valor, long atendimentosPendentes) {
    }

    public record GlosadoDto(BigDecimal valor, long quantidadeGlosas) {
    }

    public record RecuperadoDto(BigDecimal valor, long quantidadeRecursos) {
    }
}
