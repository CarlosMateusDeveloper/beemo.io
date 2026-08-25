package br.com.clinica.dto;

// Resposta de POST/PUT /api/prontuarios — deliberadamente plana. Prontuario
// tem consulta/medicoResponsavel LAZY sem JOIN FETCH; devolver a entidade
// direto quebraria a serialização (open-in-view: false fecha a sessão antes
// do Jackson acessar esses campos).
public record ProntuarioSalvoDto(Integer id, Integer consultaId, boolean finalizado) {
}
