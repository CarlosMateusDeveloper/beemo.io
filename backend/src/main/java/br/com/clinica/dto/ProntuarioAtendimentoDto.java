package br.com.clinica.dto;

// Um item do histórico de atendimentos de um paciente (aba Histórico de
// /prontuario/:pacienteId). status: "finalizado" | "pendente" | "sem_registro"
// (consulta ocorreu mas ninguém documentou o prontuário ainda).
public record ProntuarioAtendimentoDto(
        Integer consultaId,
        Integer prontuarioId,
        String dataTxt,
        String hora,
        String profissional,
        String tipo,
        String status,
        String resumo
) {
}
