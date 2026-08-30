package br.com.clinica.dto;

public record SolicitacaoExameRequest(Integer idProntuario, String exame, Boolean urgente, String justificativa, String status) {
}
