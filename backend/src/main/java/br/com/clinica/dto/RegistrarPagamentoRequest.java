package br.com.clinica.dto;

import java.math.BigDecimal;

// metodo: "dinheiro" | "debito" | "credito" | "pix" (chaves curtas do frontend,
// ver METODOS em caixaData.js — mapeadas pro enum do banco no service).
public record RegistrarPagamentoRequest(
        Integer idFatura, BigDecimal valor, String metodo, Integer parcelas, BigDecimal desconto, String motivoDesconto
) {
}
