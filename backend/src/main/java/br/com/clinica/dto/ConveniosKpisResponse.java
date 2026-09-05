package br.com.clinica.dto;

import java.math.BigDecimal;

// aReceber (fatura) e emRisco (auditoria_atendimento) já são reais.
// glosado/recuperado dependem de uma agregação própria sobre
// glosa/recurso_glosa que ainda não foi escrita — zero real, não inventado.
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
