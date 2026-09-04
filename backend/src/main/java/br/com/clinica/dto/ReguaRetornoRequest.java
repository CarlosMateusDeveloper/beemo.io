package br.com.clinica.dto;

// Só edita prazo/ativa — cada um dos 4 grupos já tem sua régua semeada no
// schema (UNIQUE por grupo), não existe "criar régua nova" nesta fase.
public record ReguaRetornoRequest(Integer prazoDias, Boolean ativa) {
}
