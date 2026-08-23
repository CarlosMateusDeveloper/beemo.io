package br.com.clinica.dto;

import java.math.BigDecimal;
import java.util.List;

// empty=true quando o período não pôde ser calculado (ex.: "Personalizado" sem
// intervalo de datas ainda) — o frontend mostra o estado "sem dados" nesse caso.
public record DashboardResponse(
        boolean empty,
        int totalConsultas,
        BigDecimal faturamento,
        OcupacaoDto ocupacao,
        NoShowDto noShow,
        NovosRetornosDto novosRetornos,
        List<RankingItemDto> ranking,
        PagadorDto pagador,
        List<SerieItemDto> serieTemporal,
        String serieUnidade
) {

    public record OcupacaoDto(int preenchidos, int totalSlots, double percentual) {
    }

    public record NoShowDto(int faltas, int baseAtendimentos, double percentual) {
    }

    public record NovosRetornosDto(int novos, int retornos) {
    }

    public record RankingItemDto(
            Integer id, String nome, String especialidade,
            int totalConsultas, BigDecimal faturamento, int faltas, double noShowPct
    ) {
    }

    // convenioPercentual é sobre (convenioValor + particularValor); porTipo usa
    // consulta.tipo (Consulta/Retorno/Exame/Avaliação) — não há tabela de
    // procedimento/preço por item no schema hoje, então o mix real disponível
    // é por tipo de atendimento, não por procedimento.
    public record PagadorDto(
            BigDecimal convenioValor, BigDecimal particularValor, double convenioPercentual,
            List<TipoAtendimentoDto> porTipo
    ) {
    }

    public record TipoAtendimentoDto(String tipo, BigDecimal faturamento) {
    }

    public record SerieItemDto(String label, BigDecimal receita, int atendimentos, int cancelamentos, int faltas) {
    }
}
