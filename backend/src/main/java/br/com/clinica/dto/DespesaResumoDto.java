package br.com.clinica.dto;

import java.math.BigDecimal;

public record DespesaResumoDto(
        BigDecimal totalPeriodo, BigDecimal totalPago, BigDecimal totalPendente,
        BigDecimal totalAtrasado, long quantidadeAtrasada
) {
}
