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
        String email,
        String convenio,
        EnderecoDto endereco,
        List<AlergiaResumoDto> alergias,
        List<ComorbidadeResumoDto> comorbidades,
        List<MedicamentoResumoDto> medicamentos,
        List<ProntuarioAtendimentoDto> atendimentos
) {

    // null quando nenhum campo de endereco foi preenchido ainda — o frontend
    // mostra "nao cadastrado" nesse caso, em vez de campos vazios.
    public record EnderecoDto(
            String cep, String logradouro, String numero, String complemento, String bairro, String cidade, String uf
    ) {
    }

    public record AlergiaResumoDto(String substancia, String tipo, String gravidade) {
    }

    public record ComorbidadeResumoDto(String descricao, String codigoCid) {
    }

    public record MedicamentoResumoDto(String medicamento, String dosagem, String posologia) {
    }
}
