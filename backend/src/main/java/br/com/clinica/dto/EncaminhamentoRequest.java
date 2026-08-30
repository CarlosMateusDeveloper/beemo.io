package br.com.clinica.dto;

public record EncaminhamentoRequest(Integer idProntuario, Integer idEspecialidadeDestino, String motivo, String prioridade) {
}
