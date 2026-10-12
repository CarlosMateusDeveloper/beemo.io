package br.com.clinica.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class PosVendaOperacaoDtos {
    private PosVendaOperacaoDtos() {}

    public record Tarefa(
            @NotBlank @Size(max=160) String titulo,
            @Size(max=2000) String descricao,
            Integer pacienteId,
            @Size(max=160) String pacienteReferencia,
            Integer responsavelId,
            @NotBlank String prioridade,
            @NotNull OffsetDateTime prazo,
            @NotBlank @Size(max=160) String origem,
            @Min(0) Integer versao
    ) {}

    public record Status(@NotBlank String status, @NotNull @Min(0) Integer versao) {}

    public record Etapa(
            @NotNull @Min(0) Integer ordem,
            @NotNull @Min(0) Integer atrasoDias,
            @NotBlank @Size(max=160) String titulo,
            @NotBlank String canal,
            @Size(max=4000) String conteudo
    ) {}

    public record Jornada(
            @NotBlank @Size(max=160) String nome,
            @Size(max=2000) String descricao,
            @NotBlank String tipo,
            @NotNull Map<String,Object> regraEntrada,
            Integer responsavelId,
            @NotEmpty List<@Valid Etapa> etapas,
            @Min(0) Integer versao
    ) {}

    public record Inscricao(@NotNull @Positive Integer pacienteId) {}

    public record Campanha(
            @NotBlank @Size(max=160) String nome,
            @NotBlank @Size(max=160) String objetivo,
            @NotNull Map<String,Object> publico,
            @NotBlank String canal,
            @NotBlank @Size(max=4000) String conteudo,
            OffsetDateTime programadoPara,
            Integer responsavelId,
            List<@Positive Integer> pacientes,
            @Positive Long modeloId,
            @Min(0) Integer versao
    ) {}
}
