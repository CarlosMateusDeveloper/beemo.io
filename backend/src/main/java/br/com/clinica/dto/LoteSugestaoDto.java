package br.com.clinica.dto;

import java.math.BigDecimal;

// Passo 1 do wizard: "Novo lote recomendado — Unimed — 281 atendimentos —
// R$ 42.150" (docs/specs/convenios.md, Aba 4). Um card por convênio com
// pelo menos 1 fatura elegível pra lote.
public record LoteSugestaoDto(Integer idConvenio, String convenioNome, int quantidade, BigDecimal valorTotal) {
}
