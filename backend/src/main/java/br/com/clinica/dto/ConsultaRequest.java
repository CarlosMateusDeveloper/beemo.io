package br.com.clinica.dto;

// statusConsulta usa o nome da constante Java (ex.: "EmEspera"), não o
// literal do banco ("Em Espera") — ver StatusConsultaConverter.
public record ConsultaRequest(Integer idPaciente, Integer idAgenda, String statusConsulta) {
}
