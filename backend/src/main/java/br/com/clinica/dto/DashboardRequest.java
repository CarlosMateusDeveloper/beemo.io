package br.com.clinica.dto;

// periodo: "Hoje" | "7 dias" | "Mês" | "Personalizado". profissionalId nulo = todos os médicos.
public record DashboardRequest(String periodo, Integer profissionalId) {
}
