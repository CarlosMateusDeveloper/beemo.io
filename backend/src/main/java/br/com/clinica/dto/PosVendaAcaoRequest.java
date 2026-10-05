package br.com.clinica.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public record PosVendaAcaoRequest(
        @NotNull @PositiveOrZero Integer versao,
        @NotBlank String acao,
        Integer idResponsavel,
        OffsetDateTime proximaAcao,
        @Size(max = 500) String motivoFalta,
        Boolean recorrente,
        String canal,
        String resultado,
        @Size(max = 1500) String observacao,
        Integer idConsulta
) {}
