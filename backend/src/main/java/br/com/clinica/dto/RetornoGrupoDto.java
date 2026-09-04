package br.com.clinica.dto;

import java.math.BigDecimal;

public record RetornoGrupoDto(String grupo, String descricao, int quantidade, BigDecimal valorEstimado) {
}
