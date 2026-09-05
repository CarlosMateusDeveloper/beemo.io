package br.com.clinica.dto;

import java.math.BigDecimal;

// Resumo do topo da aba Auditoria (spec: "300 atendimentos analisados —
// 281 aprovados · 12 em atenção · 7 bloqueados — R$ 2.840 em risco").
public record AuditoriaResumoDto(
        long totalAnalisados,
        long aprovados,
        long atencao,
        long bloqueados,
        BigDecimal valorEmRisco
) {
}
