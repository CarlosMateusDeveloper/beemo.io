package br.com.clinica.service;

import br.com.clinica.dto.PosVendaAcaoRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class PosVendaService {
    private final JdbcTemplate db;
    @Value("${app.clinic.time-zone:America/Fortaleza}")
    private String clinicTimeZone = "America/Fortaleza";
    private static final Set<String> FECHADOS = Set.of("recuperado", "nao_deseja", "nao_contatar", "registro_corrigido");
    private static final Set<String> STATUS = Set.of("pendente", "em_contato", "sem_resposta", "reagendado",
            "nova_falta", "recuperado", "nao_deseja", "nao_contatar", "registro_corrigido");
    private static final String FROM = """
            FROM pos_venda_caso pv
            JOIN consulta c ON c.id_consulta = pv.id_consulta_origem
            JOIN paciente p ON p.id_paciente = c.id_paciente
            JOIN agenda a ON a.id_agenda = c.id_agenda
            JOIN medico m ON m.id_medico = a.id_medico
            JOIN especialidade e ON e.id_especialidade = m.id_especialidade
            LEFT JOIN usuario u ON u.id = pv.id_responsavel
            LEFT JOIN consulta cr ON cr.id_consulta = pv.id_consulta_reagendada
            LEFT JOIN agenda ar ON ar.id_agenda = cr.id_agenda
            LEFT JOIN paciente_retorno_status prs ON prs.id_paciente = p.id_paciente
            """;
    private static final String SELECT = """
            SELECT pv.id, pv.versao, pv.status, pv.id_responsavel, pv.motivo_falta, pv.recorrente,
                pv.id_consulta_origem, pv.id_consulta_reagendada,
                pv.data_falta::text, pv.proxima_acao::text, pv.atualizado_em::text,
                c.id_paciente, p.nome AS paciente, concat(p.ddd,p.numero) AS telefone,
                m.nome AS profissional, e.nome AS especialidade, c.tipo::text AS tipo,
                u.nome AS responsavel, cr.status_consulta::text AS status_consulta,
                ar.data_slot::text AS data_reagendada, ar.hora_slot::text AS hora_reagendada,
                coalesce(prs.status = 'nao_contatar',false) AS nao_contatar,
                (SELECT count(*) FROM pos_venda_evento ev WHERE ev.id_caso = pv.id AND ev.tipo = 'contato') AS tentativas
            """;

    public PosVendaService(JdbcTemplate db) { this.db = db; }

    public Map<String, Object> listar(String status, String busca, Integer responsavel, int pagina, String fila) {
        if (pagina < 0 || pagina > 100000) throw erro("Página inválida");
        if (!status.equals("abertos") && !status.equals("todos") && !STATUS.contains(status)) throw erro("Status inválido");
        String filtro = " WHERE (? = 'todos' OR (? = 'abertos' AND pv.status NOT IN " +
                "('recuperado','nao_deseja','nao_contatar','registro_corrigido')) OR pv.status = ?) " +
                "AND (? = '' OR strpos(lower(p.nome), lower(?)) > 0 OR strpos(concat(p.ddd,p.numero), ?) > 0) " +
                "AND (? = 0 OR pv.id_responsavel = ?) ";
        String ativos = "pv.status IN ('pendente','em_contato','sem_resposta','nova_falta','reagendado')";
        filtro += switch (fila) {
            case "todos" -> "";
            case "hoje" -> " AND " + ativos + " AND (pv.proxima_acao<=now() OR (pv.proxima_acao IS NULL AND pv.status<>'reagendado'))";
            case "sem_responsavel" -> " AND " + ativos + " AND pv.id_responsavel IS NULL";
            case "conferir_agenda" -> " AND pv.status='reagendado' AND ar.data_slot+ar.hora_slot < (CURRENT_TIMESTAMP AT TIME ZONE ?)";
            default -> throw erro("Fila inválida");
        };
        String termo = busca == null ? "" : busca.trim();
        int dono = responsavel == null ? 0 : responsavel;
        var params = new java.util.ArrayList<Object>(List.of(status,status,status,termo,termo,termo,dono,dono));
        if (fila.equals("conferir_agenda")) params.add(clinicTimeZone);
        Long total = db.queryForObject("SELECT count(*) " + FROM + filtro, Long.class, params.toArray());
        params.add(pagina * 50);
        List<Map<String,Object>> itens = db.queryForList(SELECT + FROM + filtro +
                " ORDER BY CASE WHEN pv.status='reagendado' AND pv.proxima_acao IS NULL THEN 1 ELSE 0 END, " +
                "pv.proxima_acao ASC NULLS FIRST, pv.data_falta, pv.id LIMIT 50 OFFSET ?", params.toArray());
        Map<String,Object> resumo = db.queryForMap("""
                SELECT count(*) FILTER (WHERE status NOT IN ('recuperado','nao_deseja','nao_contatar','registro_corrigido')) AS abertos,
                    count(*) FILTER (WHERE status = 'reagendado') AS reagendados,
                    count(*) FILTER (WHERE status = 'recuperado') AS recuperados,
                    count(*) FILTER (WHERE status = 'nova_falta') AS novas_faltas,
                    count(*) FILTER (WHERE status IN ('pendente','em_contato','sem_resposta','nova_falta','reagendado')
                        AND (proxima_acao <= now() OR (proxima_acao IS NULL AND status<>'reagendado'))) AS a_contatar
                FROM pos_venda_caso
                """);
        return Map.of("itens", itens, "total", total, "pagina", pagina, "resumo", resumo);
    }

    public Map<String,Object> detalhe(long id) {
        Map<String,Object> caso = db.queryForList(SELECT + FROM + " WHERE pv.id = ?", id).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Caso não encontrado"));
        List<Map<String,Object>> historico = db.queryForList("""
                SELECT ev.id, ev.criado_em::text, ev.tipo, ev.canal, ev.descricao, u.nome AS autor
                FROM pos_venda_evento ev LEFT JOIN usuario u ON u.id = ev.id_usuario
                WHERE ev.id_caso = ? ORDER BY ev.id DESC
                """, id);
        List<Map<String,Object>> consultas = db.queryForList("""
                SELECT c.id_consulta AS id, a.data_slot::text AS data, a.hora_slot::text AS hora,
                    m.nome AS profissional, e.nome AS especialidade, c.tipo::text AS tipo
                FROM consulta c JOIN agenda a ON a.id_agenda = c.id_agenda
                JOIN medico m ON m.id_medico = a.id_medico JOIN especialidade e ON e.id_especialidade = m.id_especialidade
                JOIN pos_venda_caso pv ON pv.id = ?
                JOIN consulta origem ON origem.id_consulta = pv.id_consulta_origem
                JOIN agenda ao ON ao.id_agenda = origem.id_agenda
                JOIN medico mo ON mo.id_medico = ao.id_medico
                WHERE c.id_paciente = origem.id_paciente AND m.id_especialidade = mo.id_especialidade
                    AND c.tipo = origem.tipo AND c.status_consulta IN ('Agendada','Confirmada')
                    AND a.data_slot + a.hora_slot > pv.data_falta
                    AND a.data_slot + a.hora_slot >= (CURRENT_TIMESTAMP AT TIME ZONE ?)
                    AND NOT EXISTS (SELECT 1 FROM pos_venda_consulta v WHERE v.id_consulta = c.id_consulta)
                ORDER BY a.data_slot, a.hora_slot LIMIT 100
                """, id, clinicTimeZone);
        return Map.of("caso",caso,"historico",historico,"consultas",consultas);
    }

    public List<Map<String,Object>> responsaveis() {
        return db.queryForList("SELECT u.id,u.nome FROM usuario u JOIN tenant_membro m ON m.id_usuario=u.id WHERE m.id_clinica=nullif(current_setting('app.tenant_id',true),'')::integer AND m.ativo AND m.perfil='administrador' ORDER BY u.nome");
    }

    @Transactional
    public void agir(long id, PosVendaAcaoRequest r, Integer autor) {
        // Mesma ordem de locks do gatilho da agenda: consulta, depois caso.
        if ("reagendar".equals(r.acao()) && r.idConsulta() != null) {
            db.queryForList("SELECT id_consulta FROM consulta WHERE id_consulta = ? FOR UPDATE", r.idConsulta());
        }
        Map<String,Object> caso = db.queryForList("SELECT * FROM pos_venda_caso WHERE id = ? FOR UPDATE", id)
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Caso não encontrado"));
        if (!Objects.equals(caso.get("versao"), r.versao())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "O caso foi atualizado. Reabra os detalhes antes de salvar.");
        }
        if (FECHADOS.contains(caso.get("status"))) throw erro("Este caso está encerrado");
        Integer paciente = db.queryForObject("SELECT id_paciente FROM consulta WHERE id_consulta = ?", Integer.class, caso.get("id_consulta_origem"));
        boolean bloqueado = Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS (SELECT 1 FROM paciente_retorno_status WHERE id_paciente = ? AND status = 'nao_contatar')", Boolean.class, paciente));
        if (bloqueado && !"nao_contatar".equals(r.acao())) throw erro("Paciente marcado como não contatar");
        String descricao;
        String canal = null;
        switch (r.acao()) {
            case "organizar", "contato" -> {
                validarResponsavel(r.idResponsavel());
                if (r.proximaAcao() == null || !r.proximaAcao().isAfter(OffsetDateTime.now())) {
                    throw erro("Informe uma próxima ação futura");
                }
                String status = (String) caso.get("status");
                if (r.acao().equals("contato")) {
                    if (!Set.of("telefone","whatsapp","presencial").contains(Objects.toString(r.canal(),""))) throw erro("Canal inválido");
                    if (!Set.of("em_contato","sem_resposta").contains(Objects.toString(r.resultado(),""))) throw erro("Resultado inválido");
                    if (!status.equals("reagendado")) status = r.resultado();
                    canal = r.canal();
                    descricao = "Contato registrado: " + r.resultado() + ". " + textoObrigatorio(r.observacao());
                } else descricao = "Responsável e próxima ação atualizados.";
                db.update("""
                        UPDATE pos_venda_caso SET status = ?, id_responsavel = ?, proxima_acao = ?,
                            motivo_falta = ?, recorrente = ? WHERE id = ?
                        """, status, r.idResponsavel(), Timestamp.from(r.proximaAcao().toInstant()),
                        r.motivoFalta(), Boolean.TRUE.equals(r.recorrente()), id);
            }
            case "reagendar" -> {
                if (r.idConsulta() == null) throw erro("Selecione uma nova consulta da agenda");
                if ("reagendado".equals(caso.get("status"))) throw erro("Já existe uma consulta em acompanhamento");
                boolean elegivel = ((List<?>) detalhe(id).get("consultas")).stream()
                        .anyMatch(c -> Objects.equals(((Map<?,?>) c).get("id"),r.idConsulta()));
                if (!elegivel) throw erro("Escolha uma consulta futura do mesmo paciente, tipo e especialidade, ainda não vinculada");
                // Serializa concorrência entre dois casos tentando usar a mesma consulta.
                int vinculado = db.update("INSERT INTO pos_venda_consulta(id_consulta,id_caso) VALUES (?,?) ON CONFLICT DO NOTHING",r.idConsulta(),id);
                if (vinculado == 0) throw new ResponseStatusException(HttpStatus.CONFLICT,"Consulta já vinculada a outro caso");
                validarResponsavel(r.idResponsavel());
                db.update("UPDATE pos_venda_caso SET status = 'reagendado', id_consulta_reagendada = ?, id_responsavel = ?, proxima_acao = NULL WHERE id = ?",
                        r.idConsulta(),r.idResponsavel(),id);
                descricao = "Reagendado na consulta #" + r.idConsulta() + ". Aguardando atendimento realizado na agenda.";
            }
            case "nao_deseja", "nao_contatar" -> {
                descricao = textoObrigatorio(r.observacao());
                db.update("UPDATE pos_venda_caso SET status = ?, proxima_acao = NULL WHERE id = ?",r.acao(),id);
                if (r.acao().equals("nao_contatar")) {
                    db.update("""
                            INSERT INTO paciente_retorno_status(id_paciente,status,motivo_nao_contatar,atualizado_em)
                            VALUES (?,'nao_contatar',?,now()) ON CONFLICT (id_paciente) DO UPDATE
                            SET status = 'nao_contatar', motivo_nao_contatar = excluded.motivo_nao_contatar, atualizado_em = now()
                            """,paciente,descricao);
                }
            }
            default -> throw erro("Ação inválida");
        }
        db.update("INSERT INTO pos_venda_evento(id_caso,id_usuario,tipo,canal,descricao) VALUES (?,?,?,?,?)",
                id,autor,r.acao(),canal,descricao);
    }

    private void validarResponsavel(Integer id) {
        if (id == null || !Boolean.TRUE.equals(db.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM tenant_membro WHERE id_usuario=? AND id_clinica=nullif(current_setting('app.tenant_id',true),'')::integer AND ativo AND perfil='administrador')",Boolean.class,id))) {
            throw erro("Selecione um responsável administrativo");
        }
    }

    private String textoObrigatorio(String texto) {
        if (texto == null || texto.isBlank()) throw erro("Registre o resultado ou motivo da ação");
        return texto.trim();
    }

    private ResponseStatusException erro(String texto) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,texto); }
}
