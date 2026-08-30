package br.com.clinica.dto;

// tipo: prontuario / guia / solicitacao_medica / autorizacao / laudo /
// comprovante / outro. idDocumentoAnexo é opcional — só quando a evidência
// é um arquivo real já existente em documento_anexo (spec seção 6: "os
// documentos existentes no ClinicOS devem poder ser selecionados sem
// precisar fazer novo upload").
public record RecursoGlosaDocumentoRequest(String tipo, Integer idDocumentoAnexo, String descricao) {
}
