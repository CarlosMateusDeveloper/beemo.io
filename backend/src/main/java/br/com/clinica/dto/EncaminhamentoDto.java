package br.com.clinica.dto;

// Resposta de POST/PUT /api/encaminhamentos — plana; prontuario e
// especialidadeDestino são LAZY sem JOIN FETCH.
public record EncaminhamentoDto(
        Integer id, Integer idProntuario, Integer idEspecialidadeDestino, String motivo, String prioridade, String emitidoEm
) {
}
