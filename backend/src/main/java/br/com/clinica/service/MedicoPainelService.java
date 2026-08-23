package br.com.clinica.service;

import br.com.clinica.dto.MedicosPainelRequest;
import br.com.clinica.dto.MedicosPainelResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Painel de /medicos: KPIs + tabela. "Taxa de retorno" é uma aproximação: %
// de pacientes com mais de uma consulta com o médico considerando todo o
// histórico até o fim do período, não uma janela móvel de 12 meses — não há
// necessidade de reabrir essa conta por período.
//
// Pontualidade, horas perdidas e receita líquida (issue #17) dependem de
// consulta.iniciado_em/cancelado_em e medico.repasse_percentual — colunas
// novas que só passam a ter dado real conforme o agenda-service grava essas
// transições daqui pra frente. Até lá, consultasComInicio/pacientesTotal
// zerados são o sinal de "sem amostra ainda", não "0%" de verdade — quem
// consome a resposta deve tratar amostra zero como indisponível, não como zero.
@Service
public class MedicoPainelService {

    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final EntityManager entityManager;

    public MedicoPainelService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public MedicosPainelResponse calcular(MedicosPainelRequest request) {
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
                return new MedicosPainelResponse(true, List.of());
            }
        }

        Map<Integer, Linha> porMedico = baseAgregados(inicio, fim);
        aplicarOcupacao(porMedico, inicio, fim);
        aplicarNovos(porMedico, inicio, fim);
        aplicarRetorno(porMedico, fim);
        aplicarPontualidade(porMedico, inicio, fim);
        aplicarHorasPerdidas(porMedico, inicio, fim);
        aplicarProximo(porMedico, hoje);

        List<Linha> linhas = new ArrayList<>(porMedico.values());
        linhas.sort((a, b) -> a.nome.compareTo(b.nome));

        List<MedicosPainelResponse.MedicoLinhaDto> medicos = linhas.stream()
                .map(Linha::paraDto)
                .toList();

        return new MedicosPainelResponse(false, medicos);
    }

    @SuppressWarnings("unchecked")
    private Map<Integer, Linha> baseAgregados(LocalDate inicio, LocalDate fim) {
        Query query = entityManager.createNativeQuery(
                "SELECT m.id_medico, m.nome, m.crm, m.status, m.repasse_percentual, m.id_especialidade, e.nome, " +
                        "  COUNT(c.id_consulta) AS atendimentos, " +
                        "  COUNT(c.id_consulta) FILTER (WHERE c.status_consulta = 'Faltou') AS faltas, " +
                        "  COALESCE(SUM(f.valor), 0) AS receita_bruta " +
                        "FROM medico m " +
                        "JOIN especialidade e ON e.id_especialidade = m.id_especialidade " +
                        "LEFT JOIN agenda a ON a.id_medico = m.id_medico AND a.data_slot BETWEEN :inicio AND :fim " +
                        "LEFT JOIN consulta c ON c.id_agenda = a.id_agenda " +
                        "LEFT JOIN fatura f ON f.id_consulta = c.id_consulta " +
                        "GROUP BY m.id_medico, m.nome, m.crm, m.status, m.repasse_percentual, m.id_especialidade, e.nome"
        );
        query.setParameter("inicio", inicio);
        query.setParameter("fim", fim);

        Map<Integer, Linha> porMedico = new LinkedHashMap<>();
        for (Object[] l : (List<Object[]>) query.getResultList()) {
            Linha linha = new Linha();
            linha.id = ((Number) l[0]).intValue();
            linha.nome = (String) l[1];
            linha.crm = (String) l[2];
            linha.status = (String) l[3];
            linha.repassePercentual = (BigDecimal) l[4];
            linha.especialidadeId = ((Number) l[5]).intValue();
            linha.especialidade = (String) l[6];
            linha.atendimentos = ((Number) l[7]).intValue();
            linha.faltas = ((Number) l[8]).intValue();
            linha.receitaBruta = (BigDecimal) l[9];
            porMedico.put(linha.id, linha);
        }
        return porMedico;
    }

    @SuppressWarnings("unchecked")
    private void aplicarOcupacao(Map<Integer, Linha> porMedico, LocalDate inicio, LocalDate fim) {
        Query query = entityManager.createNativeQuery(
                "SELECT id_medico, COUNT(*) FILTER (WHERE situacao <> 'Livre') AS usados, COUNT(*) AS abertos " +
                        "FROM agenda WHERE data_slot BETWEEN :inicio AND :fim GROUP BY id_medico"
        );
        query.setParameter("inicio", inicio);
        query.setParameter("fim", fim);
        for (Object[] l : (List<Object[]>) query.getResultList()) {
            Linha linha = porMedico.get(((Number) l[0]).intValue());
            if (linha == null) continue;
            linha.horariosUsados = ((Number) l[1]).intValue();
            linha.horariosAbertos = ((Number) l[2]).intValue();
        }
    }

    // "Novo" = a primeira consulta de todos os tempos do paciente caiu, com
    // este médico, dentro do período pedido (mesma definição de "novo" do
    // DashboardService/PacienteKpiService, só que particionada por médico).
    @SuppressWarnings("unchecked")
    private void aplicarNovos(Map<Integer, Linha> porMedico, LocalDate inicio, LocalDate fim) {
        Query query = entityManager.createNativeQuery(
                "SELECT primeira.id_medico, COUNT(*) FROM (" +
                        "  SELECT DISTINCT ON (c.id_paciente) c.id_paciente, a.id_medico, a.data_slot " +
                        "  FROM consulta c JOIN agenda a ON a.id_agenda = c.id_agenda " +
                        "  ORDER BY c.id_paciente, a.data_slot ASC" +
                        ") primeira " +
                        "WHERE primeira.data_slot BETWEEN :inicio AND :fim " +
                        "GROUP BY primeira.id_medico"
        );
        query.setParameter("inicio", inicio);
        query.setParameter("fim", fim);
        for (Object[] l : (List<Object[]>) query.getResultList()) {
            Linha linha = porMedico.get(((Number) l[0]).intValue());
            if (linha == null) continue;
            linha.novos = ((Number) l[1]).intValue();
        }
    }

    @SuppressWarnings("unchecked")
    private void aplicarRetorno(Map<Integer, Linha> porMedico, LocalDate fim) {
        Query query = entityManager.createNativeQuery(
                "SELECT sub.id_medico, COUNT(*) AS pacientes_total, " +
                        "  COUNT(*) FILTER (WHERE sub.qtd > 1) AS pacientes_retorno " +
                        "FROM (" +
                        "  SELECT a.id_medico, c.id_paciente, COUNT(*) AS qtd " +
                        "  FROM consulta c JOIN agenda a ON a.id_agenda = c.id_agenda " +
                        "  WHERE a.data_slot <= :fim AND c.status_consulta <> 'Cancelada' " +
                        "  GROUP BY a.id_medico, c.id_paciente" +
                        ") sub GROUP BY sub.id_medico"
        );
        query.setParameter("fim", fim);
        for (Object[] l : (List<Object[]>) query.getResultList()) {
            Linha linha = porMedico.get(((Number) l[0]).intValue());
            if (linha == null) continue;
            linha.pacientesTotal = ((Number) l[1]).intValue();
            linha.pacientesRetorno = ((Number) l[2]).intValue();
        }
    }

    // Atraso = minutos entre o horário marcado (agenda.data_slot+hora_slot) e
    // o início real (consulta.iniciado_em), zerado por baixo (começar adiantado
    // não é atraso). "Pontual" = atraso <= 15 min, mesmo critério do KPI.
    @SuppressWarnings("unchecked")
    private void aplicarPontualidade(Map<Integer, Linha> porMedico, LocalDate inicio, LocalDate fim) {
        Query query = entityManager.createNativeQuery(
                "SELECT a.id_medico, " +
                        "  COUNT(*) FILTER (WHERE c.iniciado_em IS NOT NULL) AS com_inicio, " +
                        "  COUNT(*) FILTER (WHERE c.iniciado_em IS NOT NULL AND " +
                        "    GREATEST(0, EXTRACT(EPOCH FROM (c.iniciado_em - (a.data_slot + a.hora_slot))) / 60.0) <= 15) AS pontuais, " +
                        "  COALESCE(AVG(GREATEST(0, EXTRACT(EPOCH FROM (c.iniciado_em - (a.data_slot + a.hora_slot))) / 60.0)) " +
                        "    FILTER (WHERE c.iniciado_em IS NOT NULL), 0) AS atraso_medio " +
                        "FROM consulta c JOIN agenda a ON a.id_agenda = c.id_agenda " +
                        "WHERE a.data_slot BETWEEN :inicio AND :fim " +
                        "GROUP BY a.id_medico"
        );
        query.setParameter("inicio", inicio);
        query.setParameter("fim", fim);
        for (Object[] l : (List<Object[]>) query.getResultList()) {
            Linha linha = porMedico.get(((Number) l[0]).intValue());
            if (linha == null) continue;
            linha.consultasComInicio = ((Number) l[1]).intValue();
            linha.consultasPontuais = ((Number) l[2]).intValue();
            linha.atrasoMedioMin = ((Number) l[3]).doubleValue();
        }
    }

    // Horas perdidas = duração dos slots cancelados com menos de 24h de
    // antecedência do horário marcado (o slot não deu tempo de ser reaberto
    // pra outro paciente).
    @SuppressWarnings("unchecked")
    private void aplicarHorasPerdidas(Map<Integer, Linha> porMedico, LocalDate inicio, LocalDate fim) {
        Query query = entityManager.createNativeQuery(
                "SELECT a.id_medico, COALESCE(SUM(c.duracao_minutos), 0) / 60.0 AS horas_perdidas " +
                        "FROM consulta c JOIN agenda a ON a.id_agenda = c.id_agenda " +
                        "WHERE a.data_slot BETWEEN :inicio AND :fim " +
                        "  AND c.status_consulta = 'Cancelada' AND c.cancelado_em IS NOT NULL " +
                        "  AND c.cancelado_em <= (a.data_slot + a.hora_slot) " +
                        "  AND c.cancelado_em >= (a.data_slot + a.hora_slot) - INTERVAL '24 hours' " +
                        "GROUP BY a.id_medico"
        );
        query.setParameter("inicio", inicio);
        query.setParameter("fim", fim);
        for (Object[] l : (List<Object[]>) query.getResultList()) {
            Linha linha = porMedico.get(((Number) l[0]).intValue());
            if (linha == null) continue;
            linha.horasPerdidas = ((Number) l[1]).doubleValue();
        }
    }

    @SuppressWarnings("unchecked")
    private void aplicarProximo(Map<Integer, Linha> porMedico, LocalDate hoje) {
        Query query = entityManager.createNativeQuery(
                "SELECT DISTINCT ON (a.id_medico) a.id_medico, a.data_slot, a.hora_slot, c.tipo::text " +
                        "FROM consulta c JOIN agenda a ON a.id_agenda = c.id_agenda " +
                        "WHERE c.status_consulta IN ('Agendada', 'Confirmada') " +
                        "  AND (a.data_slot > :hoje OR (a.data_slot = :hoje AND a.hora_slot >= :agora)) " +
                        "ORDER BY a.id_medico, a.data_slot ASC, a.hora_slot ASC"
        );
        query.setParameter("hoje", hoje);
        query.setParameter("agora", LocalTime.now());
        for (Object[] l : (List<Object[]>) query.getResultList()) {
            Linha linha = porMedico.get(((Number) l[0]).intValue());
            if (linha == null) continue;
            LocalDate data = paraLocalDate(l[1]);
            LocalTime hora = paraLocalTime(l[2]);
            String diaTxt = data.equals(hoje) ? "Hoje" : data.format(DIA_MES);
            linha.proximoHorarioTxt = diaTxt + " " + hora.format(HORA);
            linha.proximoTipo = (String) l[3];
        }
    }

    private LocalDate paraLocalDate(Object valor) {
        if (valor instanceof LocalDate localDate) return localDate;
        if (valor instanceof java.sql.Date sqlDate) return sqlDate.toLocalDate();
        if (valor instanceof java.sql.Timestamp timestamp) return timestamp.toLocalDateTime().toLocalDate();
        throw new IllegalStateException("Tipo de data inesperado: " + valor.getClass());
    }

    private LocalTime paraLocalTime(Object valor) {
        if (valor instanceof LocalTime localTime) return localTime;
        if (valor instanceof java.sql.Time sqlTime) return sqlTime.toLocalTime();
        throw new IllegalStateException("Tipo de hora inesperado: " + valor.getClass());
    }

    private static class Linha {
        Integer id;
        String nome;
        String crm;
        String status;
        BigDecimal repassePercentual;
        Integer especialidadeId;
        String especialidade;
        int atendimentos;
        int faltas;
        BigDecimal receitaBruta = BigDecimal.ZERO;
        int horariosUsados;
        int horariosAbertos;
        int novos;
        int pacientesTotal;
        int pacientesRetorno;
        int consultasComInicio;
        int consultasPontuais;
        double atrasoMedioMin;
        double horasPerdidas;
        String proximoHorarioTxt;
        String proximoTipo;

        MedicosPainelResponse.MedicoLinhaDto paraDto() {
            double noShowPct = atendimentos == 0 ? 0 : Math.round((faltas * 100.0 / atendimentos) * 10.0) / 10.0;
            BigDecimal receitaLiquida = repassePercentual == null ? null
                    : receitaBruta.multiply(BigDecimal.ONE.subtract(repassePercentual.divide(BigDecimal.valueOf(100))))
                            .setScale(2, RoundingMode.HALF_UP);
            return new MedicosPainelResponse.MedicoLinhaDto(
                    id, nome, crm, especialidadeId, especialidade, status,
                    atendimentos, novos, atendimentos - novos,
                    receitaBruta, repassePercentual, receitaLiquida,
                    horariosUsados, horariosAbertos,
                    noShowPct, pacientesTotal, pacientesRetorno,
                    consultasComInicio, consultasPontuais, Math.round(atrasoMedioMin * 10.0) / 10.0,
                    Math.round(horasPerdidas * 10.0) / 10.0,
                    proximoHorarioTxt, proximoTipo
            );
        }
    }
}
