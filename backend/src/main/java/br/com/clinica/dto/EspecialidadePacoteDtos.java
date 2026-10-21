package br.com.clinica.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public final class EspecialidadePacoteDtos {
    private EspecialidadePacoteDtos() {}

    public record PublicarRequest(
            @NotNull @Positive Integer revisorId,
            @NotBlank @Size(max = 80) String registroProfissional) {}

    public record CriarVersaoRequest(@NotEmpty List<Map<String,Object>> componentes) {}

    public record AplicarRequest(
            @NotEmpty List<@NotBlank @Size(max = 100) String> componentes,
            @AssertTrue(message = "A clínica precisa aprovar a aplicação") boolean aprovadoPelaClinica,
            boolean politicaComunicacaoAprovada,
            @NotNull Integer versaoLock) {}

    public record PersonalizarRequest(
            @NotNull Map<String,Object> configuracao,
            @NotNull Integer versaoLock) {}

    public record AlterarStatusRequest(@NotNull Integer versaoLock) {}
}
