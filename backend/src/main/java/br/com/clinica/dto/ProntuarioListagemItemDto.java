package br.com.clinica.dto;

// Linha da tabela /prontuario: um paciente por linha, com o atendimento
// (consulta+prontuario) mais recente dele. Pacientes sem nenhum prontuario
// ainda não aparecem — a tela é "consulte os registros clínicos", não uma
// listagem de pacientes.
public record ProntuarioListagemItemDto(
        Integer pacienteId,
        String nome,
        String cpf,
        String telefone,
        Integer prontuarioId,
        String ultimaData,
        String ultimaTxt,
        String profissional,
        String status
) {
}
