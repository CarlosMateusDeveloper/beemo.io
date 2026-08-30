package br.com.clinica.dto;

import java.math.BigDecimal;

// Spec seção 14. taxaRecuperacaoPct = valor recuperado / (recuperado +
// não recuperado + perdido) dos recursos já concluídos no período — glosas
// ainda em aberto não entram na taxa (não têm desfecho pra medir).
public record GlosaIndicadoresDto(
        double taxaRecuperacaoPct,
        BigDecimal valorRecuperavel,
        BigDecimal valorRecuperado,
        BigDecimal valorPerdido,
        int recursosPendentes
) {
}
