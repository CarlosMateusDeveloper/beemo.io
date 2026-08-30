package br.com.clinica.dto;

// Resposta de POST/PUT /api/consultas — plana; Consulta.paciente é LAZY sem
// JOIN FETCH.
public record ConsultaDto(Integer id, Integer idPaciente, Integer idAgenda, String statusConsulta) {
}
