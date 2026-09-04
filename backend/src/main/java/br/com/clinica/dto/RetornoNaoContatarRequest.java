package br.com.clinica.dto;

import java.util.List;

public record RetornoNaoContatarRequest(List<Integer> idsPaciente, String motivo) {
}
