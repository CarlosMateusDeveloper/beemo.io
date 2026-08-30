package br.com.clinica.dto;

// Resposta de POST/PUT /api/documentos-clinicos — plana; DocumentoClinico.prontuario
// é LAZY sem JOIN FETCH.
public record DocumentoClinicoDto(
        Integer id, Integer idProntuario, String tipo, Integer diasAfastamento,
        String codigoCidRelacionado, String texto, String emitidoEm
) {
}
