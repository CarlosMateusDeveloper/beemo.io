package br.com.clinica.dto;

// pulados = pacientes que já receberam uma mensagem automática/manual nos
// últimos 30 dias (limite de frequência global — docs/specs/retorno.md,
// Aba 2: "sem isso o sistema vira spam e a clínica queima o canal").
public record RetornoEnviarMensagemResponse(int enviados, int pulados) {
}
