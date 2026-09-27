package br.com.clinica.dto;

public record LoginRequest(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Email @jakarta.validation.constraints.Size(max=100) String email,
        @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=200) String senha) {
}
