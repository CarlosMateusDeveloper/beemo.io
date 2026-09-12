package br.com.clinica.dto;

import java.math.BigDecimal;

// "atrasado" é calculado na leitura (status=pendente e vencimento no
// passado) — não existe esse status armazenado no banco (ver Despesa.java).
public record DespesaDto(
        Integer id, String descricao, String categoria, BigDecimal valor,
        String vencimentoTxt, String pagoEmTxt, String status, boolean atrasado,
        String fornecedor, String observacoes
) {
}
