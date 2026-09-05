package br.com.clinica.dto;

import java.util.List;

public record LoteCriarRequest(Integer idConvenio, List<Integer> idsFatura) {
}
