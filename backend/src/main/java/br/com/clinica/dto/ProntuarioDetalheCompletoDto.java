package br.com.clinica.dto;

// Registro completo de um atendimento ("Ver atendimento" / "Continuar
// atendimento" em /prontuario). Também é o formato que o formulário de
// Novo atendimento usa pra pré-carregar um rascunho existente.
public record ProntuarioDetalheCompletoDto(
        Integer id,
        Integer consultaId,
        Integer pacienteId,
        String pacienteNome,
        Integer medicoId,
        String profissional,
        String dataTxt,
        String hora,
        String tipo,
        String queixaPrincipal,
        String historiaDoencaAtual,
        String descricao,
        String exameFisico,
        String hipoteseDiagnostica,
        String diagnostico,
        String tipoDiagnostico,
        String prescricao,
        String planoTerapeutico,
        String conduta,
        boolean finalizado,
        String assinadoEm
) {
}
