package br.com.clinica.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record OnboardingConfigRequest(
        @NotNull Integer responsavelId,
        @NotBlank @Size(max = 80) String fusoHorario,
        @NotBlank @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$") String agendaInicio,
        @NotBlank @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$") String agendaFim,
        @NotNull @Size(min = 1, max = 7) List<Integer> diasAtendimento,
        @NotNull @Size(min = 1, max = 3) List<String> canais,
        boolean politicaConsentimentoConfirmada,
        @NotBlank @Pattern(regexp = "faltas|pos_atendimento|retorno_preventivo") String primeiroFluxo,
        @PositiveOrZero int versao
) {}
