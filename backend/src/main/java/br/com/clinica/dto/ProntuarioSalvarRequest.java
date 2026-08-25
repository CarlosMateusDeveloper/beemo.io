package br.com.clinica.dto;

// consultaId só é usado na criação (POST) — o vínculo com a consulta é
// imutável depois de criado (UNIQUE(id_consulta) no schema). finalizar=true
// exige descricao/diagnostico/prescricao preenchidos (validado no service);
// finalizar=false salva como rascunho, com qualquer campo em branco.
public record ProntuarioSalvarRequest(
        Integer consultaId,
        Integer medicoResponsavelId,
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
        boolean finalizar
) {
}
