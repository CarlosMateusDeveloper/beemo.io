package br.com.clinica.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.Set;

public record IntegracaoCredencialRequest(
        @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9_-]{1,79}") String fonteCodigo,
        @NotBlank @Size(max = 160) String fonteNome,
        @NotBlank @Size(max = 80) String fusoHorario,
        @NotBlank @Size(max = 120) String nome,
        @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9_-]{1,79}") String adaptador,
        @NotEmpty Set<@Pattern(regexp = "eventos:escrever|estado:ler") String> escopos,
        Map<@Size(max = 160) String, @Pattern(regexp = "AGENDADO|CONFIRMADO|FALTOU|REALIZADO|CANCELADO|DESCONHECIDO") String> situacoes
) {}
