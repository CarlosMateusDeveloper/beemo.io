package br.com.clinica.dto;

public record LoginResponse(@com.fasterxml.jackson.annotation.JsonIgnore String token, UsuarioDto usuario) {
}
