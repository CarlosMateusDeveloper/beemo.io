package br.com.clinica.service;

import br.com.clinica.dto.DashboardRequest;
import br.com.clinica.dto.DashboardResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Agregações no banco; nunca materializa a lista de consultas ou pacientes na JVM. */
@Service
public class DashboardService {
    private static final String BASE = """
        WITH base AS (
            SELECT c.id_paciente, c.status_consulta::text AS status, c.tipo::text AS tipo,
                   a.id_medico, a.data_slot, COALESCE(f.valor, 0) AS valor, p.id_convenio
            FROM consulta c
            JOIN agenda a ON a.id_agenda = c.id_agenda
            JOIN paciente p ON p.id_paciente = c.id_paciente
            LEFT JOIN fatura f ON f.id_consulta = c.id_consulta
            WHERE a.data_slot BETWEEN :inicio AND :fim
              AND (CAST(:medicoId AS INTEGER) IS NULL OR a.id_medico = :medicoId)
        )
        """;
    private final EntityManager entityManager;
    private final Clock clock;

    public DashboardService(EntityManager entityManager, Clock clock) {
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardResponse calcular(DashboardRequest request) {
        LocalDate hoje = LocalDate.now(clock);
        Periodo periodo = periodo(request, hoje);
        Object[] total = (Object[]) query(BASE + """
            SELECT COUNT(*), COALESCE(SUM(valor), 0),
                   COUNT(*) FILTER (WHERE status = 'Faltou'),
                   COUNT(*) FILTER (WHERE status IN ('Realizada', 'Faltou')),
                   COALESCE(SUM(valor) FILTER (WHERE id_convenio IS NOT NULL), 0),
                   COALESCE(SUM(valor) FILTER (WHERE id_convenio IS NULL), 0)
            FROM base
            """, periodo, request.profissionalId()).getSingleResult();
        Object[] ocupacao = (Object[]) query("""
            SELECT COUNT(*) FILTER (WHERE situacao <> 'Livre'), COUNT(*)
            FROM agenda WHERE data_slot BETWEEN :inicio AND :fim
              AND (CAST(:medicoId AS INTEGER) IS NULL OR id_medico = :medicoId)
            """, periodo, request.profissionalId()).getSingleResult();
        Object[] pacientes = (Object[]) query(BASE + """
            , primeiras AS (
                SELECT c.id_paciente, MIN(a.data_slot) AS primeira
                FROM consulta c JOIN agenda a ON a.id_agenda = c.id_agenda
                WHERE c.id_paciente IN (SELECT id_paciente FROM base)
                GROUP BY c.id_paciente
            )
            SELECT COUNT(*) FILTER (WHERE primeira BETWEEN :inicio AND :fim),
                   COUNT(*) FILTER (WHERE primeira < :inicio)
            FROM primeiras
            """, periodo, request.profissionalId()).getSingleResult();
        List<DashboardResponse.RankingItemDto> ranking = rows(query(BASE + """
            SELECT m.id_medico, m.nome, e.nome, COUNT(*), SUM(b.valor),
                   COUNT(*) FILTER (WHERE b.status = 'Faltou'),
                   COUNT(*) FILTER (WHERE b.status IN ('Realizada', 'Faltou'))
            FROM base b JOIN medico m ON m.id_medico = b.id_medico
            JOIN especialidade e ON e.id_especialidade = m.id_especialidade
            GROUP BY m.id_medico, m.nome, e.nome
            ORDER BY SUM(b.valor) DESC, m.id_medico LIMIT 5
            """, periodo, request.profissionalId())).stream()
            .map(r -> new DashboardResponse.RankingItemDto(integer(r[0]), (String) r[1], (String) r[2],
                integer(r[3]), decimal(r[4]), integer(r[5]), percentual(integer(r[5]), integer(r[6])))).toList();
        List<DashboardResponse.TipoAtendimentoDto> tipos = rows(query(BASE + """
            SELECT tipo, SUM(valor) FROM base GROUP BY tipo ORDER BY SUM(valor) DESC, tipo
            """, periodo, request.profissionalId())).stream()
            .map(r -> new DashboardResponse.TipoAtendimentoDto((String) r[0], decimal(r[1]))).toList();
        Map<LocalDate, SerieAcc> dias = new TreeMap<>();
        for (Object[] r : rows(query(BASE + """
            SELECT data_slot, SUM(valor),
                   COUNT(*) FILTER (WHERE status NOT IN ('Cancelada', 'Faltou')),
                   COUNT(*) FILTER (WHERE status = 'Cancelada'),
                   COUNT(*) FILTER (WHERE status = 'Faltou')
            FROM base GROUP BY data_slot ORDER BY data_slot
            """, periodo, request.profissionalId()))) {
            LocalDate data = r[0] instanceof LocalDate d ? d : ((java.sql.Date) r[0]).toLocalDate();
            dias.put(data, new SerieAcc(decimal(r[1]), integer(r[2]), integer(r[3]), integer(r[4])));
        }
        BigDecimal convenio = decimal(total[4]), particular = decimal(total[5]);
        return new DashboardResponse(integer(total[0]) == 0 && integer(ocupacao[1]) == 0, integer(total[0]), decimal(total[1]),
            new DashboardResponse.OcupacaoDto(integer(ocupacao[0]), integer(ocupacao[1]), percentual(integer(ocupacao[0]), integer(ocupacao[1]))),
            new DashboardResponse.NoShowDto(integer(total[2]), integer(total[3]), percentual(integer(total[2]), integer(total[3]))),
            new DashboardResponse.NovosRetornosDto(integer(pacientes[0]), integer(pacientes[1])), ranking,
            new DashboardResponse.PagadorDto(convenio, particular, percentual(convenio.doubleValue(), convenio.add(particular).doubleValue()), tipos),
            serie(periodo, dias), periodo.semanal() ? "por semana" : "por dia", calcularHoje(hoje, request.profissionalId()));
    }

    private DashboardResponse.HojeDto calcularHoje(LocalDate hoje, Integer medicoId) {
        Object[] resumo = (Object[]) entityManager.createNativeQuery("""
            SELECT COUNT(*), COUNT(*) FILTER (WHERE c.status_consulta::text = 'Em Espera')
            FROM consulta c JOIN agenda a ON a.id_agenda = c.id_agenda
            WHERE a.data_slot = :hoje
              AND c.status_consulta::text NOT IN ('Cancelada', 'Faltou')
              AND (CAST(:medicoId AS INTEGER) IS NULL OR a.id_medico = :medicoId)
            """).setParameter("hoje", hoje).setParameter("medicoId", medicoId).getSingleResult();
        Query proximas = entityManager.createNativeQuery("""
            SELECT c.id_consulta, p.nome, a.hora_slot, m.nome, c.status_consulta::text
            FROM consulta c JOIN agenda a ON a.id_agenda = c.id_agenda
            JOIN paciente p ON p.id_paciente = c.id_paciente
            JOIN medico m ON m.id_medico = a.id_medico
            WHERE a.data_slot = :hoje AND a.hora_slot >= :agora
              AND c.status_consulta::text IN ('Agendada', 'Confirmada', 'Em Espera')
              AND (CAST(:medicoId AS INTEGER) IS NULL OR a.id_medico = :medicoId)
            ORDER BY a.hora_slot, c.id_consulta LIMIT 5
            """).setParameter("hoje", hoje).setParameter("agora", LocalTime.now(clock)).setParameter("medicoId", medicoId);
        return new DashboardResponse.HojeDto(integer(resumo[0]), integer(resumo[1]), rows(proximas).stream().map(r -> {
            LocalTime hora = r[2] instanceof LocalTime t ? t : ((java.sql.Time) r[2]).toLocalTime();
            return new DashboardResponse.ProximaConsultaDto(integer(r[0]), (String) r[1],
                hora.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")), (String) r[3], (String) r[4]);
        }).toList());
    }

    record Periodo(LocalDate inicio, LocalDate fim, boolean semanal, boolean hoje) {}
    static Periodo periodo(DashboardRequest request, LocalDate hoje) {
        if (request.profissionalId() != null && request.profissionalId() <= 0) throw invalido("Profissional inválido.");
        String nome = request.periodo() == null ? "Mês" : request.periodo();
        Periodo resultado = switch (nome) {
            case "Hoje" -> new Periodo(hoje, hoje, false, true);
            case "7 dias" -> new Periodo(hoje.minusDays(6), hoje, false, false);
            case "Mês", "Mes" -> new Periodo(hoje.withDayOfMonth(1), hoje.withDayOfMonth(hoje.lengthOfMonth()), true, false);
            case "Personalizado" -> new Periodo(request.dataInicio(), request.dataFim(), false, false);
            default -> throw invalido("Período inválido. Use Hoje, 7 dias, Mês ou Personalizado.");
        };
        if (resultado.inicio() == null || resultado.fim() == null || resultado.inicio().isAfter(resultado.fim()))
            throw invalido("Informe um intervalo de datas válido.");
        if (ChronoUnit.DAYS.between(resultado.inicio(), resultado.fim()) >= 366)
            throw invalido("O intervalo deve ter no máximo 366 dias.");
        return resultado;
    }
    private static ResponseStatusException invalido(String mensagem) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem); }
    private Query query(String sql, Periodo periodo, Integer medicoId) {
        return entityManager.createNativeQuery(sql).setParameter("inicio", periodo.inicio())
            .setParameter("fim", periodo.fim()).setParameter("medicoId", medicoId);
    }
    @SuppressWarnings("unchecked")
    private static List<Object[]> rows(Query query) { return query.getResultList(); }
    private static int integer(Object valor) { return ((Number) valor).intValue(); }
    private static BigDecimal decimal(Object valor) { return (BigDecimal) valor; }
    private static double percentual(double parte, double total) { return total == 0 ? 0 : Math.round(parte * 1000.0 / total) / 10.0; }

    private static List<DashboardResponse.SerieItemDto> serie(Periodo periodo, Map<LocalDate, SerieAcc> dias) {
        List<DashboardResponse.SerieItemDto> resultado = new ArrayList<>();
        SerieAcc semana = new SerieAcc();
        for (LocalDate dia = periodo.inicio(); !dia.isAfter(periodo.fim()); dia = dia.plusDays(1)) {
            SerieAcc valor = dias.getOrDefault(dia, new SerieAcc());
            if (periodo.semanal()) {
                semana.somar(valor);
                if (dia.getDayOfMonth() % 7 == 0 || dia.equals(periodo.fim())) {
                    resultado.add(semana.dto("Sem " + ((dia.getDayOfMonth() - 1) / 7 + 1)));
                    semana = new SerieAcc();
                }
            } else resultado.add(valor.dto(periodo.hoje() ? "Hoje" : String.format("%02d/%02d", dia.getDayOfMonth(), dia.getMonthValue())));
        }
        return resultado;
    }
    private static class SerieAcc {
        BigDecimal receita;
        int atendimentos, cancelamentos, faltas;
        SerieAcc() { this(BigDecimal.ZERO, 0, 0, 0); }
        SerieAcc(BigDecimal receita, int atendimentos, int cancelamentos, int faltas) {
            this.receita = receita; this.atendimentos = atendimentos; this.cancelamentos = cancelamentos; this.faltas = faltas;
        }
        void somar(SerieAcc outro) {
            receita = receita.add(outro.receita); atendimentos += outro.atendimentos;
            cancelamentos += outro.cancelamentos; faltas += outro.faltas;
        }
        DashboardResponse.SerieItemDto dto(String label) { return new DashboardResponse.SerieItemDto(label, receita, atendimentos, cancelamentos, faltas); }
    }
}
