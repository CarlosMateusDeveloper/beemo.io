package br.com.clinica.dto;

import java.util.List;

// Reaproveitado por "adiar 30 dias" e, com motivo, base pro "não contatar".
public record RetornoIdsRequest(List<Integer> idsPaciente) {
}
