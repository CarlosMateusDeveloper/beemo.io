package br.com.clinica.dto;

import java.math.BigDecimal;
import java.util.List;

// Fluxo de caixa consolidado (regime de caixa — entradas via pagamento.pago_em,
// saídas via despesa.pago_em). Ver CaixaFluxoConsolidadoService.
public record CaixaFluxoConsolidadoResponse(
        List<PontoDto> serie, BigDecimal totalEntradas, BigDecimal totalSaidas, BigDecimal saldoPeriodo,
        int diasComMovimento, BigDecimal ticketMedioDiario
) {

    public record PontoDto(String dataTxt, BigDecimal entradas, BigDecimal saidas, BigDecimal saldoAcumulado) {
    }
}
