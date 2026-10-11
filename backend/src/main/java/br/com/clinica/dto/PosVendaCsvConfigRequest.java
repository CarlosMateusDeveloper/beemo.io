package br.com.clinica.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record PosVendaCsvConfigRequest(
        @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9_-]{1,79}") String fonteCodigo,
        @NotBlank @Size(max = 160) String fonteNome,
        @NotBlank @Size(max = 80) String fusoHorario,
        @NotNull Map<String, String> colunas,
        @NotNull Map<String, String> situacoes
) {}
