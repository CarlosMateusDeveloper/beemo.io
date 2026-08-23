package br.com.clinica.service;

import br.com.clinica.dto.DashboardRequest;
import br.com.clinica.dto.DashboardResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

// Agrega o painel do Dashboard a partir do que o schema hoje sustenta de
// verdade: consulta (status, tipo), agenda (ocupação — tabela do
// agenda-service, mesmo Postgres, sem entidade JPA aqui), paciente
// (convênio x particular) e fatura (faturamento real). Sem dado inventado:
// o schema não tem tabela de procedimento com preço por item, então o mix
// de receita é por tipo de atendimento (consulta.tipo), não por procedimento;
// não há meta de faturamento configurável, então a série temporal não traz meta.
@Service
public class DashboardService {

    private final EntityManager entityManager;

    public DashboardService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public DashboardResponse calcular(DashboardRequest request) {
        LocalDate hoje = LocalDate.now();
        LocalDate inicio;
        LocalDate fim;

        String periodo = request.periodo() == null ? "" : request.periodo();
        switch (periodo) {
            case "Hoje" -> {
                inicio = hoje;
                fim = hoje;
            }
            case "7 dias" -> {
                inicio = hoje.minusDays(6);
                fim = hoje;
            }
            case "Mês", "Mes" -> {
                inicio = hoje.withDayOfMonth(1);
                fim = hoje.withDayOfMonth(hoje.lengthOfMonth());
            }
            default -> {
                // "Personalizado" (sem seletor de datas ainda) ou período desconhecido.
                return empty();
            }
        }

        Integer medicoId = request.profissionalId();

        int[] ocupacao = calcularOcupacao(inicio, fim, medicoId);
        List<Object[]> linhas = buscarConsultas(inicio, fim, medicoId);

        int totalConsultas = linhas.size();
        BigDecimal faturamento = BigDecimal.ZERO;
        int faltas = 0;
        int realizadas = 0;
        Set<Integer> pacienteIds = new LinkedHashSet<>();
        Map<Integer, RankingAcc> porMedico = new LinkedHashMap<>();
        Map<String, BigDecimal> porTipo = new LinkedHashMap<>();
        BigDecimal convenioValor = BigDecimal.ZERO;
        BigDecimal particularValor = BigDecimal.ZERO;
        Map<LocalDate, SerieAcc> porDia = new TreeMap<>();

        for (Object[] linha : linhas) {
            Integer idPaciente = ((Number) linha[1]).intValue();
            String status = (String) linha[2];
            Integer idMedico = ((Number) linha[3]).intValue();
            String medicoNome = (String) linha[4];
            String especialidadeNome = (String) linha[5];
            BigDecimal valorFatura = linha[6] == null ? BigDecimal.ZERO : (BigDecimal) linha[6];
            String tipoConsulta = (String) linha[7];
            boolean particular = linha[8] == null;
            LocalDate dataSlot = paraLocalDate(linha[9]);

            pacienteIds.add(idPaciente);
            faturamento = faturamento.add(valorFatura);
            if ("Faltou".equals(status)) faltas++;
            if ("Realizada".equals(status)) realizadas++;

            RankingAcc acc = porMedico.computeIfAbsent(idMedico, id -> new RankingAcc(id, medicoNome, especialidadeNome));
            acc.total++;
            acc.faturamento = acc.faturamento.add(valorFatura);
            if ("Faltou".equals(status)) acc.faltas++;

            porTipo.merge(tipoConsulta, valorFatura, BigDecimal::add);
            if (particular) particularValor = particularValor.add(valorFatura);
            else convenioValor = convenioValor.add(valorFatura);

            SerieAcc diaAcc = porDia.computeIfAbsent(dataSlot, d -> new SerieAcc());
            diaAcc.receita = diaAcc.receita.add(valorFatura);
            if ("Cancelada".equals(status)) diaAcc.cancelamentos++;
            else if ("Faltou".equals(status)) diaAcc.faltas++;
            else diaAcc.atendimentos++;
        }

        int[] novosRetornos = calcularNovosRetornos(pacienteIds, inicio, fim);

        int baseAtendimentos = realizadas + faltas;
        double noShowPct = baseAtendimentos == 0 ? 0 : arredondar((faltas * 100.0) / baseAtendimentos);
        double ocupacaoPct = ocupacao[1] == 0 ? 0 : arredondar((ocupacao[0] * 100.0) / ocupacao[1]);

        List<DashboardResponse.RankingItemDto> ranking = porMedico.values().stream()
                .sorted((a, b) -> b.faturamento.compareTo(a.faturamento))
                .limit(5)
                .map(acc -> new DashboardResponse.RankingItemDto(
                        acc.id, acc.nome, acc.especialidade, acc.total, acc.faturamento, acc.faltas,
                        acc.total == 0 ? 0 : arredondar((acc.faltas * 100.0) / acc.total)
                ))
                .collect(Collectors.toList());

        BigDecimal totalPagador = convenioValor.add(particularValor);
        double convenioPct = totalPagador.signum() == 0 ? 0
                : arredondar(convenioValor.doubleValue() * 100.0 / totalPagador.doubleValue());
        List<DashboardResponse.TipoAtendimentoDto> porTipoDto = porTipo.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .map(e -> new DashboardResponse.TipoAtendimentoDto(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
        DashboardResponse.PagadorDto pagador =
                new DashboardResponse.PagadorDto(convenioValor, particularValor, convenioPct, porTipoDto);

        List<DashboardResponse.SerieItemDto> serieTemporal = construirSerie(periodo, inicio, fim, porDia);
        String serieUnidade = ("Mês".equals(periodo) || "Mes".equals(periodo)) ? "por semana" : "por dia";

        return new DashboardResponse(
                false, totalConsultas, faturamento,
                new DashboardResponse.OcupacaoDto(ocupacao[0], ocupacao[1], ocupacaoPct),
                new DashboardResponse.NoShowDto(faltas, baseAtendimentos, noShowPct),
                new DashboardResponse.NovosRetornosDto(novosRetornos[0], novosRetornos[1]),
                ranking, pagador, serieTemporal, serieUnidade
        );
    }

    // "Mês" agrupa em semanas do calendário (dias 1-7, 8-14, ...) pra não virar
    // um gráfico de ~30 barras; "Hoje" e "7 dias" ficam por dia.
    private List<DashboardResponse.SerieItemDto> construirSerie(
            String periodo, LocalDate inicio, LocalDate fim, Map<LocalDate, SerieAcc> porDia
    ) {
        List<DashboardResponse.SerieItemDto> serie = new ArrayList<>();
        if ("Mês".equals(periodo) || "Mes".equals(periodo)) {
            Map<Integer, SerieAcc> porSemana = new TreeMap<>();
            for (Map.Entry<LocalDate, SerieAcc> entrada : porDia.entrySet()) {
                int semana = (entrada.getKey().getDayOfMonth() - 1) / 7;
                porSemana.computeIfAbsent(semana, k -> new SerieAcc()).somar(entrada.getValue());
            }
            int totalSemanas = (fim.getDayOfMonth() - 1) / 7 + 1;
            for (int i = 0; i < totalSemanas; i++) {
                SerieAcc acc = porSemana.getOrDefault(i, new SerieAcc());
                serie.add(acc.paraDto("Sem " + (i + 1)));
            }
        } else {
            for (LocalDate dia = inicio; !dia.isAfter(fim); dia = dia.plusDays(1)) {
                SerieAcc acc = porDia.getOrDefault(dia, new SerieAcc());
                String label = "Hoje".equals(periodo) ? "Hoje" : String.format("%02d/%02d", dia.getDayOfMonth(), dia.getMonthValue());
                serie.add(acc.paraDto(label));
            }
        }
        return serie;
    }

    private int[] calcularOcupacao(LocalDate inicio, LocalDate fim, Integer medicoId) {
        Query query = entityManager.createNativeQuery(
                "SELECT " +
                        "  COUNT(*) FILTER (WHERE situacao <> 'Livre') AS preenchidos, " +
                        "  COUNT(*) AS total " +
                        "FROM agenda " +
                        "WHERE data_slot BETWEEN :inicio AND :fim " +
                        "  AND (CAST(:medicoId AS INTEGER) IS NULL OR id_medico = :medicoId)"
        );
        query.setParameter("inicio", inicio);
        query.setParameter("fim", fim);
        query.setParameter("medicoId", medicoId);
        Object[] linha = (Object[]) query.getSingleResult();
        return new int[]{((Number) linha[0]).intValue(), ((Number) linha[1]).intValue()};
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> buscarConsultas(LocalDate inicio, LocalDate fim, Integer medicoId) {
        Query query = entityManager.createNativeQuery(
                "SELECT c.id_consulta, c.id_paciente, c.status_consulta::text, " +
                        "  a.id_medico, m.nome AS medico_nome, e.nome AS especialidade_nome, " +
                        "  f.valor AS fatura_valor, c.tipo::text AS tipo_consulta, " +
                        "  p.id_convenio, a.data_slot " +
                        "FROM consulta c " +
                        "JOIN agenda a ON c.id_agenda = a.id_agenda " +
                        "JOIN medico m ON a.id_medico = m.id_medico " +
                        "JOIN especialidade e ON m.id_especialidade = e.id_especialidade " +
                        "JOIN paciente p ON c.id_paciente = p.id_paciente " +
                        "LEFT JOIN fatura f ON f.id_consulta = c.id_consulta " +
                        "WHERE a.data_slot BETWEEN :inicio AND :fim " +
                        "  AND (CAST(:medicoId AS INTEGER) IS NULL OR a.id_medico = :medicoId)"
        );
        query.setParameter("inicio", inicio);
        query.setParameter("fim", fim);
        query.setParameter("medicoId", medicoId);
        return query.getResultList();
    }

    // Novo = a primeira consulta de todos os tempos do paciente caiu dentro do período pedido.
    @SuppressWarnings("unchecked")
    private int[] calcularNovosRetornos(Set<Integer> pacienteIds, LocalDate inicio, LocalDate fim) {
        if (pacienteIds.isEmpty()) return new int[]{0, 0};

        Query query = entityManager.createNativeQuery(
                "SELECT c.id_paciente, MIN(a.data_slot) " +
                        "FROM consulta c " +
                        "JOIN agenda a ON c.id_agenda = a.id_agenda " +
                        "WHERE c.id_paciente IN :pacienteIds " +
                        "GROUP BY c.id_paciente"
        );
        query.setParameter("pacienteIds", pacienteIds);
        List<Object[]> linhas = query.getResultList();

        int novos = 0;
        int retornos = 0;
        for (Object[] linha : linhas) {
            LocalDate primeira = paraLocalDate(linha[1]);
            boolean isNovo = !primeira.isBefore(inicio) && !primeira.isAfter(fim);
            if (isNovo) novos++;
            else retornos++;
        }
        return new int[]{novos, retornos};
    }

    private LocalDate paraLocalDate(Object valor) {
        if (valor instanceof LocalDate localDate) return localDate;
        if (valor instanceof java.sql.Date sqlDate) return sqlDate.toLocalDate();
        if (valor instanceof java.sql.Timestamp timestamp) return timestamp.toLocalDateTime().toLocalDate();
        throw new IllegalStateException("Tipo de data inesperado: " + valor.getClass());
    }

    private double arredondar(double valor) {
        return Math.round(valor * 10.0) / 10.0;
    }

    private DashboardResponse empty() {
        return new DashboardResponse(
                true, 0, BigDecimal.ZERO,
                new DashboardResponse.OcupacaoDto(0, 0, 0),
                new DashboardResponse.NoShowDto(0, 0, 0),
                new DashboardResponse.NovosRetornosDto(0, 0),
                List.of(),
                new DashboardResponse.PagadorDto(BigDecimal.ZERO, BigDecimal.ZERO, 0, List.of()),
                List.of(),
                null
        );
    }

    private static class RankingAcc {
        final Integer id;
        final String nome;
        final String especialidade;
        int total = 0;
        BigDecimal faturamento = BigDecimal.ZERO;
        int faltas = 0;

        RankingAcc(Integer id, String nome, String especialidade) {
            this.id = id;
            this.nome = nome;
            this.especialidade = especialidade;
        }
    }

    private static class SerieAcc {
        BigDecimal receita = BigDecimal.ZERO;
        int atendimentos = 0;
        int cancelamentos = 0;
        int faltas = 0;

        void somar(SerieAcc outro) {
            receita = receita.add(outro.receita);
            atendimentos += outro.atendimentos;
            cancelamentos += outro.cancelamentos;
            faltas += outro.faltas;
        }

        DashboardResponse.SerieItemDto paraDto(String label) {
            return new DashboardResponse.SerieItemDto(label, receita, atendimentos, cancelamentos, faltas);
        }
    }
}
