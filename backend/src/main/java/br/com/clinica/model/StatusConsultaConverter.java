package br.com.clinica.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

// Java não aceita espaço em identificador de enum — status_consulta_enum no
// banco tem 'Em Espera'/'Em Atendimento' com espaço, StatusConsulta.java usa
// EmEspera/EmAtendimento sem espaço. Sem essa ponte, @Enumerated(STRING) puro
// quebra tanto lendo quanto escrevendo esses dois valores (valueOf/name não
// batem com o literal do banco).
@Converter
public class StatusConsultaConverter implements AttributeConverter<StatusConsulta, String> {

    @Override
    public String convertToDatabaseColumn(StatusConsulta status) {
        if (status == null) return null;
        return switch (status) {
            case EmEspera -> "Em Espera";
            case EmAtendimento -> "Em Atendimento";
            default -> status.name();
        };
    }

    @Override
    public StatusConsulta convertToEntityAttribute(String valor) {
        if (valor == null) return null;
        return switch (valor) {
            case "Em Espera" -> StatusConsulta.EmEspera;
            case "Em Atendimento" -> StatusConsulta.EmAtendimento;
            default -> StatusConsulta.valueOf(valor);
        };
    }
}
