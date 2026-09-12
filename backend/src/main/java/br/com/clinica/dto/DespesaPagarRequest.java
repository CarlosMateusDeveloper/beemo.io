package br.com.clinica.dto;

import java.time.LocalDate;

// dataPagamento nula = hoje (ver DespesaService.marcarPaga).
public record DespesaPagarRequest(LocalDate dataPagamento) {
}
