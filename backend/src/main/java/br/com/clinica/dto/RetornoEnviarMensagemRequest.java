package br.com.clinica.dto;

import java.util.List;

// idUsuario: sem sessão real hoje (auth desligada), então "quem disparou"
// vem explícito do front — mesmo padrão já usado em GlosaController.
public record RetornoEnviarMensagemRequest(List<Integer> idsPaciente, String grupo, String texto, Integer idUsuario) {
}
