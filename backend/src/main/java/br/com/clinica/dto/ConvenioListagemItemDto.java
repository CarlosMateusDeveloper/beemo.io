package br.com.clinica.dto;

public record ConvenioListagemItemDto(
        Integer id, String nome, boolean ativo,
        long totalProcedimentos, long totalRegras, String ultimaAtualizacao
) {
}
