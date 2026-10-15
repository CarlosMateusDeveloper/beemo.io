package br.com.clinica.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public record IntegracaoEventoRequest(
        @NotBlank @Size(max = 160) String eventoId,
        @NotBlank @Pattern(regexp = "PACIENTE_ATUALIZADO|ATENDIMENTO_ATUALIZADO|SITUACAO_ATENDIMENTO_ATUALIZADA") String tipo,
        @Min(1) int versao,
        @NotNull OffsetDateTime ocorridoEm,
        @NotNull OffsetDateTime atualizadoEmFonte,
        @Valid Paciente paciente,
        @Valid Atendimento atendimento
) {
    public record Paciente(
            @NotBlank @Size(max = 160) String idExterno,
            @Size(max = 200) String nome,
            @Size(max = 20) String telefone
    ) {}

    public record Atendimento(
            @NotBlank @Size(max = 160) String idExterno,
            @Size(max = 160) String pacienteIdExterno,
            OffsetDateTime agendadoEm,
            @Size(max = 80) String fusoHorario,
            @Size(max = 200) String profissional,
            @Size(max = 160) String especialidade,
            @Size(max = 160) String tipo,
            @NotBlank @Size(max = 160) String situacao
    ) {}
}
