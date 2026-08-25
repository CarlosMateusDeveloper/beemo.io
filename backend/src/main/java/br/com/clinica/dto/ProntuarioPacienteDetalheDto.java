package br.com.clinica.dto;

import java.util.List;

// Payload de /prontuario/:pacienteId — cabeçalho do paciente + o suficiente
// da aba Resumo (alergias/comorbidades/medicamentos ativos, pra segurança
// clínica rápida, não o prontuário inteiro) + histórico de atendimentos.
public record ProntuarioPacienteDetalheDto(
        Integer id,
        String nome,
        int idade,
        String cpf,
        String telefone,
        List<AlergiaResumoDto> alergias,
        List<ComorbidadeResumoDto> comorbidades,
        List<MedicamentoResumoDto> medicamentos,
        List<ProntuarioAtendimentoDto> atendimentos
) {

    public record AlergiaResumoDto(String substancia, String tipo, String gravidade) {
    }

    public record ComorbidadeResumoDto(String descricao, String codigoCid) {
    }

    public record MedicamentoResumoDto(String medicamento, String dosagem, String posologia) {
    }
}
