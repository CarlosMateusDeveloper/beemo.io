package br.com.clinica.dto;

import java.math.BigDecimal;

public record FecharTurnoRequest(BigDecimal dinheiroContado, String observacao) {
}
