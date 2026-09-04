package br.com.clinica.dto;

import java.math.BigDecimal;
import java.util.List;

// Aba Resultados — conversão é obrigatória junto de "mensagens enviadas"
// (spec: "número de mensagens enviadas sozinho não diz nada").
public record RetornoResultadosDto(
        int mensagensEnviadas, int voltaramAAgendar, double conversaoPct, BigDecimal receitaGerada,
        List<ConversaoPorGrupoDto> conversaoPorGrupo,
        List<EvolucaoMensalDto> evolucaoMensal,
        List<HistoricoEnvioDto> historico
) {
    public record ConversaoPorGrupoDto(String grupo, int enviadas, int converteram, double conversaoPct) {
    }

    public record EvolucaoMensalDto(String mes, int enviadas, int converteram, BigDecimal receita) {
    }

    public record HistoricoEnvioDto(
            String data, String grupo, int quantidade, String disparadoPor
    ) {
    }
}
