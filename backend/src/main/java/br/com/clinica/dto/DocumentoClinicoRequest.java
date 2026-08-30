package br.com.clinica.dto;

public record DocumentoClinicoRequest(
        Integer idProntuario, String tipo, Integer diasAfastamento, String codigoCidRelacionado, String texto
) {
}
