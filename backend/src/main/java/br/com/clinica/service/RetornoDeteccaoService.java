package br.com.clinica.service;

import br.com.clinica.dto.RetornoGrupoDto;
import br.com.clinica.dto.RetornoPacienteItemDto;
import br.com.clinica.dto.RetornoResumoDto;
import br.com.clinica.model.GrupoRetorno;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Detecção dos 4 grupos de /retorno (docs/specs/retorno.md, Aba 1). Cada
// grupo exclui pacientes com paciente_retorno_status = 'nao_contatar' ou
// 'adiado' com adiado_ate ainda no futuro — o "não contatar" tem que
// funcionar de verdade, senão a mesma pessoa reaparece toda semana.
//
// "Tratamento interrompido" não tem como mostrar "X de Y sessões" (a spec
// pede isso, mas não existe conceito de plano de tratamento com total de
// sessões previstas em lugar nenhum do schema) — mostro "N sessões
// seguidas sem retorno agendado" em vez de inventar o "de Y".
@Service
public class RetornoDeteccaoService {

    private static final DateTimeFormatter DIA_MES_ANO = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String FILTRO_STATUS =
            "(prs.status IS NULL OR prs.status = 'pendente' OR (prs.status = 'adiado' AND prs.adiado_ate < CURRENT_DATE))";
    private static final String SEM_CONSULTA_FUTURA =
            "NOT EXISTS (SELECT 1 FROM consulta c2 JOIN agenda a2 ON a2.id_agenda = c2.id_agenda " +
                    "WHERE c2.id_paciente = p.id_paciente AND a2.data_slot >= CURRENT_DATE " +
                    "AND c2.status_consulta IN ('Agendada', 'Confirmada'))";

    private final EntityManager entityManager;

    public RetornoDeteccaoService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public RetornoResumoDto resumo() {
        Map<String, BigDecimal> ticketPorEspecialidade = carregarTicketMedioPorEspecialidade();
        BigDecimal ticketGeral = ticketMedioGeral();

        List<RetornoGrupoDto> grupos = new ArrayList<>();
        grupos.add(montarGrupo(GrupoRetorno.tratamento_interrompido,
                "Série de sessões iniciada e não concluída, sem agendamento futuro",
                listarTratamentoInterrompido(ticketPorEspecialidade, ticketGeral)));
        grupos.add(montarGrupo(GrupoRetorno.retorno_medico,
                "Retorno pedido pelo médico já venceu, sem consulta marcada",
                listarRetornoMedico(ticketPorEspecialidade, ticketGeral)));
        grupos.add(montarGrupo(GrupoRetorno.exame_pendente,
                "Exame solicitado sem resultado registrado",
                listarExamePendente(ticketPorEspecialidade, ticketGeral)));
        grupos.add(montarGrupo(GrupoRetorno.ritmo_quebrado,
                "Intervalo entre consultas excedeu bastante a média do paciente",
                listarRitmoQuebrado(ticketPorEspecialidade, ticketGeral)));

        // Ordem por valor, não por quantidade (spec: "tratamento interrompido
        // tem poucos pacientes mas alto valor unitário").
        grupos.sort((a, b) -> b.valorEstimado().compareTo(a.valorEstimado()));

        int total = grupos.stream().mapToInt(RetornoGrupoDto::quantidade).sum();
        BigDecimal valorTotal = grupos.stream().map(RetornoGrupoDto::valorEstimado)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new RetornoResumoDto(total, valorTotal, grupos);
    }

    public List<RetornoPacienteItemDto> listarGrupo(GrupoRetorno grupo) {
        Map<String, BigDecimal> ticketPorEspecialidade = carregarTicketMedioPorEspecialidade();
        BigDecimal ticketGeral = ticketMedioGeral();
        return switch (grupo) {
            case tratamento_interrompido -> listarTratamentoInterrompido(ticketPorEspecialidade, ticketGeral);
            case retorno_medico -> listarRetornoMedico(ticketPorEspecialidade, ticketGeral);
            case exame_pendente -> listarExamePendente(ticketPorEspecialidade, ticketGeral);
            case ritmo_quebrado -> listarRitmoQuebrado(ticketPorEspecialidade, ticketGeral);
        };
    }

    private RetornoGrupoDto montarGrupo(GrupoRetorno grupo, String descricao, List<RetornoPacienteItemDto> itens) {
        BigDecimal valor = itens.stream().map(RetornoPacienteItemDto::valorEstimado)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new RetornoGrupoDto(grupo.name(), descricao, itens.size(), valor);
    }

    @SuppressWarnings("unchecked")
    private List<RetornoPacienteItemDto> listarRetornoMedico(Map<String, BigDecimal> ticket, BigDecimal ticketGeral) {
        Query query = entityManager.createNativeQuery(
                "SELECT DISTINCT ON (p.id_paciente) p.id_paciente, p.nome, p.data_nascimento, p.ddd, p.numero, " +
                        "  pr.retorno_sugerido_em, a.data_slot, e.nome, m.nome " +
                        "FROM prontuario pr " +
                        "JOIN consulta c ON c.id_consulta = pr.id_consulta " +
                        "JOIN agenda a ON a.id_agenda = c.id_agenda " +
                        "JOIN medico m ON m.id_medico = a.id_medico " +
                        "JOIN especialidade e ON e.id_especialidade = m.id_especialidade " +
                        "JOIN paciente p ON p.id_paciente = c.id_paciente " +
                        "LEFT JOIN paciente_retorno_status prs ON prs.id_paciente = p.id_paciente " +
                        "WHERE pr.retorno_sugerido_em IS NOT NULL AND pr.retorno_sugerido_em < CURRENT_DATE " +
                        "  AND " + FILTRO_STATUS +
                        "  AND " + SEM_CONSULTA_FUTURA +
                        "ORDER BY p.id_paciente, pr.criado_em DESC"
        );
        List<RetornoPacienteItemDto> resultado = new ArrayList<>();
        for (Object[] l : (List<Object[]>) query.getResultList()) {
            LocalDate retornoEm = paraLocalDate(l[5]);
            long diasVencido = java.time.temporal.ChronoUnit.DAYS.between(retornoEm, LocalDate.now());
            resultado.add(montarItem(l, "retorno vencido há " + diasVencido + " dias", ticket, ticketGeral));
        }
        return resultado;
    }

    @SuppressWarnings("unchecked")
    private List<RetornoPacienteItemDto> listarExamePendente(Map<String, BigDecimal> ticket, BigDecimal ticketGeral) {
        Query query = entityManager.createNativeQuery(
                "SELECT DISTINCT ON (p.id_paciente) p.id_paciente, p.nome, p.data_nascimento, p.ddd, p.numero, " +
                        "  se.solicitado_em, a.data_slot, e.nome, m.nome, se.exame " +
                        "FROM solicitacao_exame se " +
                        "JOIN prontuario pr ON pr.id_prontuario = se.id_prontuario " +
                        "JOIN consulta c ON c.id_consulta = pr.id_consulta " +
                        "JOIN agenda a ON a.id_agenda = c.id_agenda " +
                        "JOIN medico m ON m.id_medico = a.id_medico " +
                        "JOIN especialidade e ON e.id_especialidade = m.id_especialidade " +
                        "JOIN paciente p ON p.id_paciente = c.id_paciente " +
                        "LEFT JOIN paciente_retorno_status prs ON prs.id_paciente = p.id_paciente " +
                        "WHERE se.status = 'SOLICITADO' AND " + FILTRO_STATUS +
                        "ORDER BY p.id_paciente, se.solicitado_em ASC"
        );
        List<RetornoPacienteItemDto> resultado = new ArrayList<>();
        for (Object[] l : (List<Object[]>) query.getResultList()) {
            LocalDate solicitadoEm = paraLocalDate(l[5]);
            String exame = (String) l[9];
            resultado.add(montarItem(l, "exame \"" + exame + "\" pedido em " + solicitadoEm.format(DIA_MES_ANO), ticket, ticketGeral));
        }
        return resultado;
    }

    @SuppressWarnings("unchecked")
    private List<RetornoPacienteItemDto> listarTratamentoInterrompido(Map<String, BigDecimal> ticket, BigDecimal ticketGeral) {
        Query query = entityManager.createNativeQuery(
                "SELECT p.id_paciente, p.nome, p.data_nascimento, p.ddd, p.numero, " +
                        "  COUNT(*) AS sessoes, MAX(a.data_slot) AS ultima_data, " +
                        "  (ARRAY_AGG(e.nome ORDER BY a.data_slot DESC))[1] AS especialidade, " +
                        "  (ARRAY_AGG(m.nome ORDER BY a.data_slot DESC))[1] AS medico " +
                        "FROM consulta c " +
                        "JOIN agenda a ON a.id_agenda = c.id_agenda " +
                        "JOIN medico m ON m.id_medico = a.id_medico " +
                        "JOIN especialidade e ON e.id_especialidade = m.id_especialidade " +
                        "JOIN paciente p ON p.id_paciente = c.id_paciente " +
                        "LEFT JOIN paciente_retorno_status prs ON prs.id_paciente = p.id_paciente " +
                        "WHERE c.status_consulta = 'Realizada' AND " + FILTRO_STATUS +
                        "GROUP BY p.id_paciente, p.nome, p.data_nascimento, p.ddd, p.numero " +
                        "HAVING COUNT(*) >= 3 AND " + SEM_CONSULTA_FUTURA
        );
        List<RetornoPacienteItemDto> resultado = new ArrayList<>();
        for (Object[] l : (List<Object[]>) query.getResultList()) {
            long sessoes = ((Number) l[5]).longValue();
            Object[] reordenado = { l[0], l[1], l[2], l[3], l[4], null, l[6], l[7], l[8] };
            resultado.add(montarItem(reordenado, sessoes + " sessões seguidas sem retorno agendado", ticket, ticketGeral));
        }
        return resultado;
    }

    @SuppressWarnings("unchecked")
    private List<RetornoPacienteItemDto> listarRitmoQuebrado(Map<String, BigDecimal> ticket, BigDecimal ticketGeral) {
        Query query = entityManager.createNativeQuery(
                "WITH consultas_paciente AS (" +
                        "  SELECT c.id_paciente, a.data_slot, " +
                        "    LAG(a.data_slot) OVER (PARTITION BY c.id_paciente ORDER BY a.data_slot) AS anterior " +
                        "  FROM consulta c JOIN agenda a ON a.id_agenda = c.id_agenda " +
                        "  WHERE c.status_consulta = 'Realizada'" +
                        "), intervalos AS (" +
                        "  SELECT id_paciente, AVG(data_slot - anterior) AS media_dias, MAX(data_slot) AS ultima_data " +
                        "  FROM consultas_paciente WHERE anterior IS NOT NULL " +
                        "  GROUP BY id_paciente HAVING COUNT(*) >= 2" +
                        ") " +
                        "SELECT p.id_paciente, p.nome, p.data_nascimento, p.ddd, p.numero, " +
                        "  i.media_dias, i.ultima_data, e.nome, m.nome " +
                        "FROM intervalos i " +
                        "JOIN paciente p ON p.id_paciente = i.id_paciente " +
                        "JOIN LATERAL (" +
                        "  SELECT a.id_medico FROM consulta c JOIN agenda a ON a.id_agenda = c.id_agenda " +
                        "  WHERE c.id_paciente = p.id_paciente AND a.data_slot = i.ultima_data LIMIT 1" +
                        ") ult ON true " +
                        "JOIN medico m ON m.id_medico = ult.id_medico " +
                        "JOIN especialidade e ON e.id_especialidade = m.id_especialidade " +
                        "LEFT JOIN paciente_retorno_status prs ON prs.id_paciente = p.id_paciente " +
                        "WHERE (CURRENT_DATE - i.ultima_data) > (i.media_dias * 1.5) " +
                        "  AND " + FILTRO_STATUS +
                        "  AND " + SEM_CONSULTA_FUTURA
        );
        List<RetornoPacienteItemDto> resultado = new ArrayList<>();
        for (Object[] l : (List<Object[]>) query.getResultList()) {
            double mediaDias = ((Number) l[5]).doubleValue();
            resultado.add(montarItem(l, "ritmo médio de " + Math.round(mediaDias) + " dias entre consultas, quebrado", ticket, ticketGeral));
        }
        return resultado;
    }

    private RetornoPacienteItemDto montarItem(Object[] l, String contexto, Map<String, BigDecimal> ticket, BigDecimal ticketGeral) {
        Integer idPaciente = ((Number) l[0]).intValue();
        String nome = (String) l[1];
        LocalDate nascimento = paraLocalDate(l[2]);
        String ddd = (String) l[3];
        String numero = (String) l[4];
        LocalDate ultimaData = l[6] != null ? paraLocalDate(l[6]) : null;
        String especialidade = (String) l[7];
        String medico = (String) l[8];

        int idade = Period.between(nascimento, LocalDate.now()).getYears();
        BigDecimal valor = ticket.getOrDefault(especialidade, ticketGeral);

        return new RetornoPacienteItemDto(
                idPaciente, nome, idade,
                ultimaData != null ? ultimaData.format(DIA_MES_ANO) : "—", especialidade,
                contexto, medico, valor,
                formatarTelefone(ddd, numero), true
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, BigDecimal> carregarTicketMedioPorEspecialidade() {
        Query query = entityManager.createNativeQuery(
                "SELECT esp.nome, AVG(f.valor) " +
                        "FROM especialidade esp " +
                        "JOIN medico m ON m.id_especialidade = esp.id_especialidade " +
                        "JOIN agenda a ON a.id_medico = m.id_medico " +
                        "JOIN consulta c ON c.id_agenda = a.id_agenda AND c.status_consulta = 'Realizada' " +
                        "JOIN fatura f ON f.id_consulta = c.id_consulta " +
                        "GROUP BY esp.nome"
        );
        Map<String, BigDecimal> mapa = new HashMap<>();
        for (Object[] l : (List<Object[]>) query.getResultList()) {
            mapa.put((String) l[0], (BigDecimal) l[1]);
        }
        return mapa;
    }

    private BigDecimal ticketMedioGeral() {
        Query query = entityManager.createNativeQuery(
                "SELECT COALESCE(AVG(f.valor), 0) FROM fatura f " +
                        "JOIN consulta c ON c.id_consulta = f.id_consulta WHERE c.status_consulta = 'Realizada'"
        );
        Object resultado = query.getSingleResult();
        return ((BigDecimal) resultado).setScale(2, RoundingMode.HALF_UP);
    }

    private LocalDate paraLocalDate(Object valor) {
        if (valor instanceof LocalDate localDate) return localDate;
        if (valor instanceof java.sql.Date sqlDate) return sqlDate.toLocalDate();
        if (valor instanceof java.sql.Timestamp timestamp) return timestamp.toLocalDateTime().toLocalDate();
        // Colunas timestamptz (ex.: solicitacao_exame.solicitado_em) voltam como
        // Instant em query nativa — java.time.ZoneId.systemDefault() porque é
        // assim que o resto do backend já trata timestamptz -> data local
        // (ver DashboardService/PacienteListagemService).
        if (valor instanceof java.time.Instant instant) {
            return instant.atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        }
        if (valor instanceof java.time.OffsetDateTime offsetDateTime) return offsetDateTime.toLocalDate();
        throw new IllegalStateException("Tipo de data inesperado: " + valor.getClass());
    }

    private String formatarTelefone(String ddd, String numero) {
        if (numero.length() == 9) {
            return String.format("(%s) %s-%s", ddd, numero.substring(0, 5), numero.substring(5));
        }
        if (numero.length() == 8) {
            return String.format("(%s) %s-%s", ddd, numero.substring(0, 4), numero.substring(4));
        }
        return String.format("(%s) %s", ddd, numero);
    }
}
