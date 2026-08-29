package br.com.clinica.dto;

public record LoginResponse(String token, UsuarioDto usuario) {
}
