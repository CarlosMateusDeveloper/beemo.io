package br.com.clinica.dto;

// Spec seção 5 — recorribilidade e categoria padronizada do motivo.
public record GlosaClassificarRequest(String recorribilidade, String categoriaMotivo) {
}
