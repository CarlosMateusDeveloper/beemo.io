package br.com.clinica.dto;

// Resposta de POST/PUT /api/alergias — plana de propósito. Alergia.paciente
// é LAZY sem JOIN FETCH; devolver a entidade direto quebraria a
// serialização (open-in-view: false fecha a sessão antes do Jackson
// acessar esse campo).
public record AlergiaDto(
        Integer id, Integer idPaciente, String tipo, String substancia, String gravidade,
        String observacao, String registradoEm
) {
}
