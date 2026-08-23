package br.com.clinica.service;

import br.com.clinica.dto.DashboardRequest;
import br.com.clinica.dto.DashboardResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// Agrega o painel do Dashboard a partir do que o schema hoje sustenta de
// verdade: consulta (status), agenda (ocupação — tabela do agenda-service,
// mesmo Postgres, sem entidade JPA aqui) e fatura (faturamento real). Sem
// dado inventado: o que a base não tem (ex. mix de procedimento) fica de
// fora da resposta em vez de aparecer como número fictício.
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

        for (Object[] linha : linhas) {
            Integer idPaciente = ((Number) linha[1]).intValue();
            String status = (String) linha[2];
            Integer idMedico = ((Number) linha[3]).intValue();
            String medicoNome = (String) linha[4];
            String especialidadeNome = (String) linha[5];
            BigDecimal valorFatura = linha[6] == null ? BigDecimal.ZERO : (BigDecimal) linha[6];

            pacienteIds.add(idPaciente);
            faturamento = faturamento.add(valorFatura);
            if ("Faltou".equals(status)) faltas++;
            if ("Realizada".equals(status)) realizadas++;

            RankingAcc acc = porMedico.computeIfAbsent(idMedico, id -> new RankingAcc(id, medicoNome, especialidadeNome));
            acc.total++;
            acc.faturamento = acc.faturamento.add(valorFatura);
            if ("Faltou".equals(status)) acc.faltas++;
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

        return new DashboardResponse(
                false, totalConsultas, faturamento,
                new DashboardResponse.OcupacaoDto(ocupacao[0], ocupacao[1], ocupacaoPct),
                new DashboardResponse.NoShowDto(faltas, baseAtendimentos, noShowPct),
                new DashboardResponse.NovosRetornosDto(novosRetornos[0], novosRetornos[1]),
                ranking
        );
    }

    private int[] calcularOcupacao(LocalDate inicio, LocalDate fim, Integer medicoId) {
        Query query = entityManager.createNativeQuery(
                "SELECT " +
                        "  COUNT(*) FILTER (WHERE situacao <> 'Livre') AS preenchidos, " +
                        "  COUNT(*) AS total " +
                        "FROM agenda " +
                        "WHERE data_slot BETWEEN :inicio AND :fim " +
                        "  AND (:medicoId IS NULL OR id_medico = :medicoId)"
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
                        "  f.valor AS fatura_valor " +
                        "FROM consulta c " +
                        "JOIN agenda a ON c.id_agenda = a.id_agenda " +
                        "JOIN medico m ON a.id_medico = m.id_medico " +
                        "JOIN especialidade e ON m.id_especialidade = e.id_especialidade " +
                        "LEFT JOIN fatura f ON f.id_consulta = c.id_consulta " +
                        "WHERE a.data_slot BETWEEN :inicio AND :fim " +
                        "  AND (:medicoId IS NULL OR a.id_medico = :medicoId)"
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
                List.of()
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
}
