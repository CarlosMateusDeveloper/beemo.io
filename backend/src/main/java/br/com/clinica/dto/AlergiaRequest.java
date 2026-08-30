package br.com.clinica.dto;

public record AlergiaRequest(Integer idPaciente, String tipo, String substancia, String gravidade, String observacao) {
}
