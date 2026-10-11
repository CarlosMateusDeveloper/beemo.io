package br.com.clinica.integration;

import br.com.clinica.dto.IntegracaoEventoRequest;
import org.springframework.stereotype.Component;

@Component
public class ClinisisStandardAdapter implements ClinicaIntegrationAdapter {
    @Override public String codigo() { return "clinisis-standard-v1"; }

    @Override public IntegracaoEventoRequest adaptar(IntegracaoEventoRequest evento) {
        var paciente = evento.paciente() == null ? null : new IntegracaoEventoRequest.Paciente(
                trim(evento.paciente().idExterno()), trim(evento.paciente().nome()),
                digits(evento.paciente().telefone()));
        var atendimento = evento.atendimento() == null ? null : new IntegracaoEventoRequest.Atendimento(
                trim(evento.atendimento().idExterno()), trim(evento.atendimento().pacienteIdExterno()),
                evento.atendimento().agendadoEm(), trim(evento.atendimento().fusoHorario()),
                trim(evento.atendimento().profissional()), trim(evento.atendimento().especialidade()),
                trim(evento.atendimento().tipo()), trim(evento.atendimento().situacao()));
        return new IntegracaoEventoRequest(trim(evento.eventoId()), evento.tipo(), evento.versao(),
                evento.ocorridoEm(), evento.atualizadoEmFonte(), paciente, atendimento);
    }

    private static String trim(String value) { return value == null ? null : value.trim(); }
    private static String digits(String value) { return value == null ? null : value.replaceAll("\\D", ""); }
}
