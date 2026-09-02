package br.com.clinica.dto;

import java.time.LocalDate;

// periodo: "Hoje" | "7 dias" | "Mês" | "Personalizado". profissionalId nulo = todos os médicos.
// dataInicio/dataFim só são usados (e obrigatórios) quando periodo = "Personalizado".
public record DashboardRequest(String periodo, Integer profissionalId, LocalDate dataInicio, LocalDate dataFim) {
}
