package br.com.clinica.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class PreAtendimentoDtos {
    private PreAtendimentoDtos() {}

    public record CriarLink(Integer validadeHoras, List<String> escopo, String canal) {}
    public record Verificar(
            @NotNull LocalDate dataNascimento,
            @NotBlank @Pattern(regexp="\\d{4}", message="Informe os quatro últimos dígitos do CPF") String cpfUltimos4) {}
    public record Salvar(
            @Positive int versao,
            @NotNull Map<String,Object> dadosPropostos,
            @NotNull Map<String,Object> respostas) {}
    public record Revisar(
            @NotBlank @Pattern(regexp="aprovado|pendente") String status,
            List<String> camposAprovados,
            List<String> camposPendentes,
            String observacao) {}
}
