package br.com.clinica.dto;

// Status/especialidade são filtrados no frontend (mesmo padrão de /pacientes)
// — o painel sempre calcula pra todos os médicos no período pedido.
public record MedicosPainelRequest(String periodo) {
}
