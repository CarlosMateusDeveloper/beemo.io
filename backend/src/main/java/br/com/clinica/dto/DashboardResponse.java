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
        List<RankingItemDto> ranking
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
}
