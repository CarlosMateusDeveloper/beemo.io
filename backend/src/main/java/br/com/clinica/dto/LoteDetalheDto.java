package br.com.clinica.dto;

import java.math.BigDecimal;
import java.util.List;

public record LoteDetalheDto(
        Integer id, String codigo, Integer idConvenio, String convenioNome, String status,
        String dataEnvio, String criadoEm,
        BigDecimal valorTotal, BigDecimal valorPago, BigDecimal valorGlosado, BigDecimal valorDivergente,
        List<ItemDto> itens
) {
    public record ItemDto(
            Integer idFatura, String pacienteNome, String dataAtendimento, String tipo,
            BigDecimal valor, BigDecimal valorPago, BigDecimal valorGlosado, String statusFatura
    ) {
    }
}
