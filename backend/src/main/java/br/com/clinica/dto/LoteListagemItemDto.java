package br.com.clinica.dto;

import java.math.BigDecimal;

// divergencia = true quando o lote já devia estar liquidado (status pago/
// pago_parcial/com_glosas) e (valor pago + valor glosado) não fecha com o
// valor enviado — pagamento a menor sem glosa formal registrada
// (docs/specs/convenios.md, Aba 4: "conciliação").
public record LoteListagemItemDto(
        Integer id, String codigo, String convenioNome, String status,
        int quantidadeItens, BigDecimal valorTotal, BigDecimal valorPago, BigDecimal valorGlosado,
        String dataEnvio, String criadoEm, boolean divergencia
) {
}
