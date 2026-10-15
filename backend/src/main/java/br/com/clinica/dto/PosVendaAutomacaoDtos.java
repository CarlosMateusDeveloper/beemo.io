package br.com.clinica.dto;

import jakarta.validation.constraints.*;
import java.util.Map;

public final class PosVendaAutomacaoDtos {
    private PosVendaAutomacaoDtos() {}

    public record Regra(
            @NotBlank @Size(max=160) String nome,
            @NotBlank String eventoTipo,
            @NotBlank String acao,
            @Positive Long jornadaId,
            @Positive Integer responsavelId,
            @NotNull Map<String,Object> configuracao,
            @NotNull @Min(0) @Max(1000) Integer prioridade,
            @NotNull Boolean ativa,
            @Min(0) Integer versao
    ) {}
}
