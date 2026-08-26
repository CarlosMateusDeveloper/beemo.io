package br.com.clinica.dto;

import java.math.BigDecimal;

public record FecharTurnoResponse(boolean temDiferenca, BigDecimal diferenca) {
}
