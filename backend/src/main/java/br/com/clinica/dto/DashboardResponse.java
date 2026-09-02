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
        String serieUnidade,
        HojeDto hoje
) {

    // Independente do período selecionado no filtro — "cartões do dia" da
    // issue #2 (consultas de hoje, fila de atendimento em tempo real,
    // próximas consultas), por isso fica de fora do "empty" do período.
    public record HojeDto(int consultas, int filaAguardando, List<ProximaConsultaDto> proximas) {
    }

    public record ProximaConsultaDto(Integer idConsulta, String paciente, String hora, String medico, String status) {
    }

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
