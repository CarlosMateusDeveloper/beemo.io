package br.com.clinica.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public final class ComunicacaoDtos {
    private ComunicacaoDtos() {}

    public record Consentimento(
            @NotNull @Positive Integer pacienteId,
            @NotBlank @Pattern(regexp="whatsapp|email|sms|telefone|todos") String canal,
            @NotBlank @Pattern(regexp="atendimento|pos_venda|campanha|transacional|todos") String finalidade,
            @NotBlank @Pattern(regexp="concedido|revogado") String decisao,
            @NotBlank @Pattern(regexp="paciente|formulario|usuario|canal|sistema") String origem,
            @Size(max=10000) String textoAceito,
            @Size(max=300) String evidenciaReferencia,
            @NotNull OffsetDateTime ocorridoEm
    ) {}

    public record Modelo(
            @NotBlank @Pattern(regexp="[a-z0-9][a-z0-9_-]{1,99}") String codigo,
            @NotBlank @Size(max=160) String nome,
            @NotBlank @Pattern(regexp="whatsapp|email|sms") String canal,
            @NotBlank @Pattern(regexp="atendimento|pos_venda|campanha|transacional") String finalidade,
            @NotBlank @Size(max=40) String categoria,
            @NotBlank @Pattern(regexp="[a-z]{2}([-_][A-Z]{2})?") String idioma,
            @NotBlank @Size(max=10000) String conteudo
    ) {}

    public record ModeloStatus(
            @NotBlank @Pattern(regexp="submetido|aprovado|rejeitado|inativo") String status,
            @Size(max=80) String provedor,
            @Size(max=160) String provedorModeloId,
            @Size(max=500) String motivoRejeicao
    ) {}
}
