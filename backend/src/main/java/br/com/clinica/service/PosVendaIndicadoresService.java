package br.com.clinica.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Coorte pela data da falta; resultados refletem a situação atual desses casos. */
@Service
public class PosVendaIndicadoresService {
    private static final Set<String> METRICAS = Set.of("faltas", "contatados", "reagendados", "recuperados",
            "abertos", "novas_faltas", "pendencias_vencidas", "sem_responsavel");

    private final JdbcTemplate db;
    private final AccessControlService access;
    @Value("${app.clinic.time-zone:America/Fortaleza}")
    private String clinicTimeZone = "America/Fortaleza";

    static final String BASE = """
        WITH base AS (
          SELECT pv.id AS id_caso,pv.status,pv.id_responsavel,pv.motivo_falta,pv.recorrente,
            pv.id_consulta_origem,pv.id_consulta_reagendada,pv.data_falta,pv.proxima_acao,pv.atualizado_em,
            c.id_paciente,p.nome AS paciente,concat(p.ddd,p.numero) AS telefone,
            m.nome AS profissional,e.nome AS especialidade,u.nome AS responsavel,
            cr.status_consulta::text AS status_consulta_reagendada,
            ar.data_slot AS data_reagendada,ar.hora_slot AS hora_reagendada,
            ev.primeiro_contato,ev.ultima_interacao,ev.tentativas,
            EXISTS(SELECT 1 FROM pos_venda_consulta v WHERE v.id_caso=pv.id
              AND v.id_consulta<>pv.id_consulta_origem) AS ja_reagendou
          FROM pos_venda_caso pv
          JOIN consulta c ON c.id_consulta=pv.id_consulta_origem
          JOIN paciente p ON p.id_paciente=c.id_paciente
          JOIN agenda a ON a.id_agenda=c.id_agenda
          JOIN medico m ON m.id_medico=a.id_medico
          JOIN especialidade e ON e.id_especialidade=m.id_especialidade
          LEFT JOIN usuario u ON u.id=pv.id_responsavel
          LEFT JOIN consulta cr ON cr.id_consulta=pv.id_consulta_reagendada
          LEFT JOIN agenda ar ON ar.id_agenda=cr.id_agenda
          LEFT JOIN LATERAL (
            SELECT min(criado_em) FILTER(WHERE tipo='contato') AS primeiro_contato,
              max(criado_em) AS ultima_interacao,count(*) FILTER(WHERE tipo='contato') AS tentativas
            FROM pos_venda_evento WHERE id_caso=pv.id
          ) ev ON true
          WHERE pv.data_falta>=CAST(? AS date) AND pv.data_falta<CAST(? AS date)
            AND pv.status<>'registro_corrigido'
            AND (CAST(? AS INTEGER) IS NULL OR a.id_medico=?)
        )
        """;

    public PosVendaIndicadoresService(JdbcTemplate db, AccessControlService access) {
        this.db = db;
        this.access = access;
    }

    static void validarPeriodo(LocalDate inicio, LocalDate fim) {
        if (inicio == null || fim == null || fim.isBefore(inicio) || ChronoUnit.DAYS.between(inicio, fim) > 365) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Escolha um período de até 366 dias, com início anterior ou igual ao fim");
        }
    }

    static void validarMetrica(String metrica) {
        if (!METRICAS.contains(metrica)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indicador inválido");
        }
    }

    static void validarPaginacao(int pagina, int tamanho) {
        if (pagina < 0 || tamanho < 1 || tamanho > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Paginação inválida");
        }
    }

    public Map<String, Object> calcular(LocalDate inicio, LocalDate fim) {
        Periodo periodo = periodo(inicio, fim);
        Object[] params = parametros(periodo);
        var resumo = db.queryForMap(BASE + """
            SELECT count(*) AS faltas,count(DISTINCT id_paciente) AS pacientes_unicos,
              count(*) FILTER(WHERE primeiro_contato IS NOT NULL) AS contatados,
              count(DISTINCT id_paciente) FILTER(WHERE primeiro_contato IS NOT NULL) AS contatados_pacientes,
              count(*) FILTER(WHERE ja_reagendou) AS reagendados,
              count(DISTINCT id_paciente) FILTER(WHERE ja_reagendou) AS reagendados_pacientes,
              count(*) FILTER(WHERE status='recuperado') AS recuperados,
              count(DISTINCT id_paciente) FILTER(WHERE status='recuperado') AS recuperados_pacientes,
              count(*) FILTER(WHERE status='reagendado') AS aguardando_comparecimento,
              count(*) FILTER(WHERE status='nova_falta') AS novas_faltas,
              count(*) FILTER(WHERE status='nao_deseja') AS nao_deseja,
              count(*) FILTER(WHERE status='nao_contatar') AS nao_contatar,
              count(*) FILTER(WHERE status IN ('pendente','em_contato','sem_resposta','nova_falta','reagendado')) AS abertos,
              count(*) FILTER(WHERE status IN ('pendente','em_contato','sem_resposta','nova_falta','reagendado')
                AND proxima_acao<now()) AS pendencias_vencidas,
              count(*) FILTER(WHERE status IN ('pendente','em_contato','sem_resposta','nova_falta','reagendado')
                AND id_responsavel IS NULL) AS sem_responsavel,
              coalesce(sum(tentativas),0) AS tentativas,
              round(100.0*count(*) FILTER(WHERE status='recuperado')/nullif(count(*),0),1) AS taxa_recuperacao,
              round(100.0*count(DISTINCT id_paciente) FILTER(WHERE status='recuperado')
                /nullif(count(DISTINCT id_paciente),0),1) AS taxa_recuperacao_pacientes,
              round(avg(greatest(0,extract(epoch FROM (primeiro_contato-(data_falta AT TIME ZONE ?)))/3600))
                FILTER(WHERE primeiro_contato IS NOT NULL),1) AS horas_primeiro_contato,
              max(greatest(atualizado_em,coalesce(ultima_interacao,atualizado_em))) AS ultima_atualizacao
            FROM base
            """, append(params, clinicTimeZone));
        var motivos = db.queryForList(BASE + """
            SELECT coalesce(nullif(trim(motivo_falta),''),'Não informado') AS motivo,count(*) AS quantidade
            FROM base GROUP BY 1 ORDER BY quantidade DESC,motivo LIMIT 10
            """, params);
        var equipe = db.queryForList(BASE + """
            SELECT id_responsavel,coalesce(responsavel,'Sem responsável') AS responsavel,
              count(*) AS casos,count(*) FILTER(WHERE primeiro_contato IS NOT NULL) AS contatados,
              count(*) FILTER(WHERE status='recuperado') AS recuperados,
              count(*) FILTER(WHERE status IN ('pendente','em_contato','sem_resposta','nova_falta','reagendado')) AS abertos,
              count(*) FILTER(WHERE status IN ('pendente','em_contato','sem_resposta','nova_falta','reagendado')
                AND proxima_acao<now()) AS vencidos
            FROM base GROUP BY id_responsavel,responsavel ORDER BY casos DESC,responsavel
            """, params);

        var result = new LinkedHashMap<String, Object>();
        result.put("inicio", periodo.inicio());
        result.put("fim", periodo.fim());
        result.put("fuso", clinicTimeZone);
        result.put("recorte", "casos_iniciados_no_periodo");
        result.put("geradoEm", OffsetDateTime.now(ZoneId.of(clinicTimeZone)));
        result.put("resumo", resumo);
        result.put("motivos", motivos);
        result.put("equipe", equipe);
        result.put("definicoes", definicoes());
        return result;
    }

    public Map<String, Object> casos(LocalDate inicio, LocalDate fim, String metrica, int pagina, int tamanho) {
        validarMetrica(metrica);
        validarPaginacao(pagina, tamanho);
        Periodo periodo = periodo(inicio, fim);
        Object[] params = parametros(periodo);
        String filtro = filtro(metrica);
        long total = db.queryForObject(BASE + "SELECT count(*) FROM base WHERE " + filtro, Long.class, params);
        var argumentos = new ArrayList<>(Arrays.asList(params));
        argumentos.add(tamanho);
        argumentos.add(pagina * tamanho);
        var itens = db.queryForList(BASE + selecaoCasos() + " WHERE " + filtro
                + " ORDER BY data_falta DESC,id_caso DESC LIMIT ? OFFSET ?", argumentos.toArray());
        var result = new LinkedHashMap<String, Object>();
        result.put("metrica", metrica);
        result.put("inicio", periodo.inicio());
        result.put("fim", periodo.fim());
        result.put("itens", itens);
        result.put("total", total);
        result.put("pagina", pagina);
        result.put("tamanho", tamanho);
        result.put("totalPaginas", (total + tamanho - 1) / tamanho);
        return result;
    }

    public String exportar(LocalDate inicio, LocalDate fim, String metrica) {
        validarMetrica(metrica);
        Periodo periodo = periodo(inicio, fim);
        Object[] params = parametros(periodo);
        var argumentos = new ArrayList<>(Arrays.asList(params));
        argumentos.add(10_001);
        var itens = db.queryForList(BASE + selecaoCasos() + " WHERE " + filtro(metrica)
                + " ORDER BY data_falta DESC,id_caso DESC LIMIT ?", argumentos.toArray());
        if (itens.size() > 10_000) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "A exportação excede 10.000 casos. Reduza o período.");
        }
        StringBuilder csv = new StringBuilder("caso;paciente_id;paciente;telefone;status;data_falta;responsavel;proxima_acao;tentativas;consulta_origem;consulta_reagendada\r\n");
        for (var item : itens) {
            csv.append(csv(item.get("id"))).append(';').append(csv(item.get("pacienteId"))).append(';')
                    .append(csv(item.get("paciente"))).append(';').append(csv(item.get("telefone"))).append(';')
                    .append(csv(item.get("status"))).append(';').append(csv(item.get("dataFalta"))).append(';')
                    .append(csv(item.get("responsavel"))).append(';').append(csv(item.get("proximaAcao"))).append(';')
                    .append(csv(item.get("tentativas"))).append(';').append(csv(item.get("consultaOrigem"))).append(';')
                    .append(csv(item.get("consultaReagendada"))).append("\r\n");
        }
        return csv.toString();
    }

    private Periodo periodo(LocalDate inicio, LocalDate fim) {
        LocalDate hoje = LocalDate.now(ZoneId.of(clinicTimeZone));
        LocalDate de = inicio == null ? hoje.withDayOfMonth(1) : inicio;
        LocalDate ate = fim == null ? hoje : fim;
        validarPeriodo(de, ate);
        return new Periodo(de, ate);
    }

    private Object[] parametros(Periodo periodo) {
        Integer medico = access.clinicalDoctorId();
        return new Object[]{periodo.inicio(), periodo.fim().plusDays(1), medico, medico};
    }

    private static String selecaoCasos() {
        return """
            SELECT id_caso AS "id",id_paciente AS "pacienteId",paciente,telefone,status,
              id_responsavel AS "responsavelId",coalesce(responsavel,'Sem responsável') AS responsavel,
              data_falta::text AS "dataFalta",proxima_acao::text AS "proximaAcao",motivo_falta AS "motivoFalta",
              profissional,especialidade,id_consulta_origem AS "consultaOrigem",
              id_consulta_reagendada AS "consultaReagendada",status_consulta_reagendada AS "statusConsultaReagendada",
              data_reagendada::text AS "dataReagendada",hora_reagendada::text AS "horaReagendada",
              primeiro_contato::text AS "primeiroContato",ultima_interacao::text AS "ultimaInteracao",
              tentativas,ja_reagendou AS reagendado,(status='recuperado') AS recuperado,
              (status IN ('pendente','em_contato','sem_resposta','nova_falta','reagendado') AND proxima_acao<now()) AS "pendenciaVencida"
            FROM base
            """;
    }

    private static String filtro(String metrica) {
        return switch (metrica) {
            case "faltas" -> "true";
            case "contatados" -> "primeiro_contato IS NOT NULL";
            case "reagendados" -> "ja_reagendou";
            case "recuperados" -> "status='recuperado'";
            case "abertos" -> "status IN ('pendente','em_contato','sem_resposta','nova_falta','reagendado')";
            case "novas_faltas" -> "status='nova_falta'";
            case "pendencias_vencidas" -> "status IN ('pendente','em_contato','sem_resposta','nova_falta','reagendado') AND proxima_acao<now()";
            case "sem_responsavel" -> "status IN ('pendente','em_contato','sem_resposta','nova_falta','reagendado') AND id_responsavel IS NULL";
            default -> throw new IllegalArgumentException("Indicador não validado");
        };
    }

    private static List<Map<String, String>> definicoes() {
        return List.of(
                definicao("faltas", "Casos de falta", "Casos não corrigidos cuja falta ocorreu no período selecionado.", "pos_venda_caso + agenda"),
                definicao("pacientes_unicos", "Pacientes únicos", "Pacientes distintos entre os casos de falta; várias faltas da mesma pessoa contam uma vez.", "consulta.id_paciente"),
                definicao("contatados", "Casos contatados", "Casos com pelo menos um evento de contato; tentativas repetidas não duplicam o caso.", "pos_venda_evento.tipo=contato"),
                definicao("reagendados", "Casos reagendados", "Casos com outra consulta vinculada. Reagendamento não significa comparecimento.", "pos_venda_consulta"),
                definicao("recuperados", "Casos recuperados", "Casos cuja consulta reagendada foi registrada como realizada pela agenda.", "consulta.status_consulta=Realizada"),
                definicao("taxa_recuperacao", "Taxa de recuperação", "Casos recuperados ÷ casos de falta da coorte. O resultado atual pode ocorrer depois do período.", "pos_venda_caso.status"),
                definicao("pendencias_vencidas", "Pendências vencidas", "Casos abertos com próxima ação anterior ao instante atual.", "pos_venda_caso.proxima_acao"),
                definicao("receita", "Receita recuperada", "Não calculada neste recorte. Exige comparecimento e evento financeiro verificável.", "indisponível")
        );
    }

    private static Map<String, String> definicao(String chave, String nome, String formula, String fonte) {
        return Map.of("chave", chave, "nome", nome, "formula", formula, "fonte", fonte);
    }

    private static Object[] append(Object[] values, Object value) {
        Object[] result = Arrays.copyOf(values, values.length + 1);
        result[values.length] = value;
        return result;
    }

    private static String csv(Object value) {
        String text = Objects.toString(value, "").replace("\r", " ").replace("\n", " ");
        if (!text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0) text = "'" + text;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private record Periodo(LocalDate inicio, LocalDate fim) {}
}
