package br.com.clinica.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public final class CrmDtos {
    private CrmDtos() {}

    public record CriarOportunidade(
            @NotBlank @Size(max=160) String nome,
            @Size(max=30) String telefone,
            @Email @Size(max=180) String email,
            @NotBlank String canalPreferido,
            boolean contatoPermitido,
            @NotBlank String canalOrigem,
            @NotBlank @Size(max=120) String origem,
            @NotBlank @Size(max=180) String servicoInteresse,
            Integer responsavelId,
            OffsetDateTime proximaAcao,
            @PositiveOrZero BigDecimal orcamento,
            @Size(max=180) String idempotencyKey,
            @Size(max=180) String conversaExternaId
    ) {}

    public record AtualizarOportunidade(
            @NotBlank @Size(max=180) String servicoInteresse,
            Integer responsavelId,
            OffsetDateTime proximaAcao,
            @PositiveOrZero BigDecimal orcamento,
            @NotNull @PositiveOrZero Integer versao
    ) {}

    public record Transicao(
            @NotBlank String etapa,
            @Size(max=300) String motivoPerda,
            OffsetDateTime proximaAcao,
            Integer responsavelId,
            @NotNull @PositiveOrZero Integer versao
    ) {}

    public record Interacao(
            @NotBlank String tipo,
            @NotBlank String canal,
            @Size(max=2000) String resumo,
            OffsetDateTime ocorridoEm,
            Long mensagemId
    ) {}

    public record NovoPaciente(
            @NotBlank @Size(max=100) String nome,
            @NotBlank @Pattern(regexp="\\d{11}") String cpf,
            @NotNull LocalDate dataNascimento,
            @NotBlank @Pattern(regexp="\\d{10,13}") String telefone,
            @Email @Size(max=150) String email
    ) {}

    public record Converter(
            Integer pacienteId,
            @Valid NovoPaciente novoPaciente,
            boolean confirmarNovoPaciente,
            @Positive Integer agendaId,
            @Positive Integer medicoId,
            LocalDate data,
            String hora,
            String tipo,
            @Min(10) @Max(480) Integer duracaoMinutos,
            @NotNull @PositiveOrZero Integer versao
    ) {}
}
