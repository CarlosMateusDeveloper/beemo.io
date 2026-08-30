package br.com.clinica.dto;

// Resposta de POST/PUT /api/solicitacoes-exame — plana; prontuario é LAZY
// sem JOIN FETCH.
public record SolicitacaoExameDto(
        Integer id, Integer idProntuario, String exame, boolean urgente, String justificativa, String status, String solicitadoEm
) {
}
