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
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class PosVendaService {
    private final JdbcTemplate db;
    private final AccessControlService access;
    @Value("${app.clinic.time-zone:America/Fortaleza}")
    private String clinicTimeZone = "America/Fortaleza";
    private static final Set<String> FECHADOS = Set.of("recuperado", "nao_deseja", "nao_contatar", "registro_corrigido");
    private static final Set<String> STATUS = Set.of("pendente", "em_contato", "sem_resposta", "reagendado",
            "nova_falta", "recuperado", "nao_deseja", "nao_contatar", "registro_corrigido");
    private static final String FROM = """
            FROM pos_venda_caso pv
            LEFT JOIN consulta c ON c.id_consulta = pv.id_consulta_origem
            LEFT JOIN paciente p ON p.id_paciente = c.id_paciente
            LEFT JOIN agenda a ON a.id_agenda = c.id_agenda
            LEFT JOIN medico m ON m.id_medico = a.id_medico
            LEFT JOIN especialidade e ON e.id_especialidade = m.id_especialidade
            LEFT JOIN integracao_atendimento_externo ae
                ON ae.id_atendimento_externo = pv.id_atendimento_externo_origem
            LEFT JOIN integracao_paciente_externo pe
                ON pe.id_paciente_externo = ae.id_paciente_externo
            LEFT JOIN integracao_fonte_dados fd ON fd.id_fonte = ae.id_fonte
            LEFT JOIN usuario u ON u.id = pv.id_responsavel
            LEFT JOIN consulta cr ON cr.id_consulta = pv.id_consulta_reagendada
            LEFT JOIN agenda ar ON ar.id_agenda = cr.id_agenda
            LEFT JOIN integracao_atendimento_externo aer
                ON aer.id_atendimento_externo = pv.id_atendimento_externo_reagendado
            LEFT JOIN paciente_retorno_status prs ON prs.id_paciente = p.id_paciente
            LEFT JOIN LATERAL (
                SELECT ev.criado_em,ev.tipo,ev.descricao
                FROM pos_venda_evento ev WHERE ev.id_caso=pv.id
                ORDER BY ev.id DESC LIMIT 1
            ) ui ON true
            """;
    private static final String SELECT = """
            SELECT pv.id, pv.versao, pv.status, pv.id_responsavel, pv.motivo_falta, pv.recorrente,
                pv.id_consulta_origem, pv.id_consulta_reagendada,pv.id_atendimento_externo_origem,
                pv.id_atendimento_externo_reagendado,pv.origem,
                pv.data_falta::text, pv.proxima_acao::text, pv.atualizado_em::text,
                c.id_paciente,pe.id_paciente_externo,coalesce(p.nome,pe.nome) AS paciente,
                coalesce(nullif(concat(p.ddd,p.numero),''),pe.telefone) AS telefone,
                coalesce(m.nome,ae.profissional) AS profissional,
                coalesce(e.nome,ae.especialidade) AS especialidade,coalesce(c.tipo::text,ae.tipo) AS tipo,
                fd.codigo AS fonte,ae.identificador_externo AS atendimento_externo,
                ae.situacao_origem,ae.situacao_normalizada,
                u.nome AS responsavel, cr.status_consulta::text AS status_consulta,
                ar.data_slot::text AS data_reagendada, ar.hora_slot::text AS hora_reagendada,
                aer.agendado_em::text AS reagendado_em,
                aer.identificador_externo AS atendimento_externo_reagendado,
                aer.situacao_origem AS situacao_reagendada_origem,
                aer.situacao_normalizada AS situacao_reagendada_normalizada,
                coalesce(prs.status = 'nao_contatar',pe.nao_contatar,false) AS nao_contatar,
                (SELECT count(*) FROM pos_venda_evento ev WHERE ev.id_caso = pv.id AND ev.tipo = 'contato') AS tentativas,
                ui.criado_em::text AS ultima_interacao,ui.tipo AS ultima_interacao_tipo,
                ui.descricao AS ultima_interacao_descricao,
                EXISTS(SELECT 1 FROM pos_venda_alerta al WHERE al.id_caso=pv.id AND al.status='aberto') AS possui_alerta,
                EXISTS(SELECT 1 FROM pos_venda_alerta al WHERE al.id_caso=pv.id AND al.status='aberto' AND al.tipo='sem_responsavel') AS alerta_sem_responsavel,
                EXISTS(SELECT 1 FROM pos_venda_alerta al WHERE al.id_caso=pv.id AND al.status='aberto' AND al.tipo='sem_proxima_acao') AS alerta_sem_proxima_acao,
                EXISTS(SELECT 1 FROM pos_venda_alerta al WHERE al.id_caso=pv.id AND al.status='aberto' AND al.tipo='prazo_vencido') AS alerta_prazo_vencido,
                EXISTS(SELECT 1 FROM pos_venda_alerta al WHERE al.id_caso=pv.id AND al.status='aberto' AND al.tipo='resposta_pendente') AS alerta_resposta_pendente,
                EXISTS(SELECT 1 FROM pos_venda_alerta al WHERE al.id_caso=pv.id AND al.status='aberto' AND al.tipo='conferir_presenca') AS alerta_conferir_presenca,
                EXISTS(SELECT 1 FROM pos_venda_alerta al WHERE al.id_caso=pv.id AND al.status='aberto' AND al.tipo='revisar_recorrencia') AS alerta_revisar_recorrencia,
                CASE
                    WHEN EXISTS(SELECT 1 FROM pos_venda_alerta al WHERE al.id_caso=pv.id AND al.status='aberto' AND al.tipo='resposta_pendente') THEN 'resposta_pendente'
                    WHEN EXISTS(SELECT 1 FROM pos_venda_alerta al WHERE al.id_caso=pv.id AND al.status='aberto' AND al.tipo='revisar_recorrencia') THEN 'revisar_recorrencia'
                    WHEN pv.status='reagendado' AND EXISTS(SELECT 1 FROM pos_venda_alerta al WHERE al.id_caso=pv.id AND al.status='aberto' AND al.tipo='conferir_presenca') THEN 'conferir_presenca'
                    WHEN pv.status='reagendado' THEN 'aguardando_consulta'
                    WHEN NOT EXISTS(SELECT 1 FROM pos_venda_evento ev WHERE ev.id_caso=pv.id AND ev.tipo='contato') THEN 'primeira_tentativa'
                    ELSE 'nova_tentativa'
                END AS etapa_fila
            """;

    public PosVendaService(JdbcTemplate db, AccessControlService access) { this.db = db; this.access = access; }

    @Transactional
    public Map<String, Object> listar(String status, String busca, Integer responsavel, int pagina, String fila) {
        sincronizarAlertas();
        if (pagina < 0 || pagina > 100000) throw erro("Página inválida");
        if (!status.equals("abertos") && !status.equals("todos") && !STATUS.contains(status)) throw erro("Status inválido");
        String filtro = " WHERE (? = 'todos' OR (? = 'abertos' AND pv.status NOT IN " +
                "('recuperado','nao_deseja','nao_contatar','registro_corrigido')) OR pv.status = ?) " +
                "AND (? = '' OR strpos(lower(coalesce(p.nome,pe.nome)), lower(?)) > 0 " +
                "OR strpos(coalesce(nullif(concat(p.ddd,p.numero),''),pe.telefone), ?) > 0) " +
                "AND (? = 0 OR pv.id_responsavel = ?) " +
                "AND (CAST(? AS INTEGER) IS NULL OR a.id_medico = ?) ";
        String ativos = "pv.status IN ('pendente','em_contato','sem_resposta','nova_falta','reagendado')";
        filtro += switch (fila) {
            case "todos" -> "";
            case "hoje" -> " AND " + ativos + " AND (pv.proxima_acao<=now() OR (pv.proxima_acao IS NULL AND pv.status<>'reagendado'))";
            case "sem_responsavel" -> " AND " + ativos + " AND pv.id_responsavel IS NULL";
            case "prioridades" -> " AND EXISTS(SELECT 1 FROM pos_venda_alerta al WHERE al.id_caso=pv.id AND al.status='aberto')";
            case "organizar" -> " AND EXISTS(SELECT 1 FROM pos_venda_alerta al WHERE al.id_caso=pv.id AND al.status='aberto' AND al.tipo IN ('sem_responsavel','sem_proxima_acao'))";
            case "primeira_tentativa" -> " AND " + ativos + " AND pv.status<>'reagendado' AND NOT EXISTS(SELECT 1 FROM pos_venda_evento ev WHERE ev.id_caso=pv.id AND ev.tipo='contato')";
            case "nova_tentativa" -> " AND " + ativos + " AND pv.status<>'reagendado' AND EXISTS(SELECT 1 FROM pos_venda_evento ev WHERE ev.id_caso=pv.id AND ev.tipo='contato') AND pv.proxima_acao<=now()";
            case "respostas" -> " AND EXISTS(SELECT 1 FROM pos_venda_alerta al WHERE al.id_caso=pv.id AND al.status='aberto' AND al.tipo='resposta_pendente')";
            case "conferir_agenda" -> " AND EXISTS(SELECT 1 FROM pos_venda_alerta al WHERE al.id_caso=pv.id AND al.status='aberto' AND al.tipo='conferir_presenca')";
            default -> throw erro("Fila inválida");
        };
        String termo = busca == null ? "" : busca.trim();
        int dono = responsavel == null ? 0 : responsavel;
        Integer medico = access.clinicalDoctorId();
        var params = new java.util.ArrayList<Object>();
        java.util.Collections.addAll(params,status,status,status,termo,termo,termo,dono,dono,medico,medico);
        Long total = db.queryForObject("SELECT count(*) " + FROM + filtro, Long.class, params.toArray());
        params.add(pagina * 50);
        List<Map<String,Object>> itens = db.queryForList(SELECT + FROM + filtro +
                " ORDER BY CASE WHEN EXISTS(SELECT 1 FROM pos_venda_alerta al WHERE al.id_caso=pv.id AND al.status='aberto' AND al.tipo='resposta_pendente') THEN 0 " +
                "WHEN EXISTS(SELECT 1 FROM pos_venda_alerta al WHERE al.id_caso=pv.id AND al.status='aberto' AND al.tipo='revisar_recorrencia') THEN 1 " +
                "WHEN EXISTS(SELECT 1 FROM pos_venda_alerta al WHERE al.id_caso=pv.id AND al.status='aberto' AND al.tipo IN ('sem_responsavel','sem_proxima_acao')) THEN 2 " +
                "WHEN EXISTS(SELECT 1 FROM pos_venda_alerta al WHERE al.id_caso=pv.id AND al.status='aberto' AND al.tipo='prazo_vencido') THEN 3 " +
                "WHEN EXISTS(SELECT 1 FROM pos_venda_alerta al WHERE al.id_caso=pv.id AND al.status='aberto' AND al.tipo='conferir_presenca') THEN 4 ELSE 5 END, " +
                "pv.proxima_acao ASC NULLS LAST,pv.data_falta,pv.id LIMIT 50 OFFSET ?", params.toArray());
        Map<String,Object> resumo = resumoFila(medico);
        return Map.of("itens", itens, "total", total, "pagina", pagina, "resumo", resumo);
    }

    @Transactional
    public Map<String,Object> detalhe(long id) {
        sincronizarAlertas();
        Integer medico = access.clinicalDoctorId();
        Map<String,Object> caso = db.queryForList(SELECT + FROM +
                        " WHERE pv.id = ? AND (CAST(? AS INTEGER) IS NULL OR a.id_medico=?)", id,medico,medico).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Caso não encontrado"));
        List<Map<String,Object>> historico = db.queryForList("""
                SELECT ev.id, ev.criado_em::text, ev.tipo, ev.canal, ev.descricao, u.nome AS autor
                FROM pos_venda_evento ev LEFT JOIN usuario u ON u.id = ev.id_usuario
                WHERE ev.id_caso = ? ORDER BY ev.id DESC
                """, id);
        List<Map<String,Object>> alertas = db.queryForList("""
                SELECT id_alerta AS id,tipo,mensagem,criado_em::text AS criado_em
                FROM pos_venda_alerta WHERE id_caso=? AND status='aberto'
                ORDER BY criado_em,id_alerta
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
        List<Map<String,Object>> atendimentosExternos = db.queryForList("""
                SELECT candidato.id_atendimento_externo AS id,
                    candidato.identificador_externo AS identificador,
                    candidato.agendado_em::text AS agendado_em,
                    candidato.profissional,candidato.especialidade,candidato.tipo,
                    candidato.situacao_origem,candidato.situacao_normalizada,
                    fd.codigo AS fonte
                FROM pos_venda_caso pv
                JOIN integracao_atendimento_externo origem
                    ON origem.id_atendimento_externo=pv.id_atendimento_externo_origem
                JOIN integracao_atendimento_externo candidato
                    ON candidato.id_fonte=origem.id_fonte
                    AND candidato.id_paciente_externo=origem.id_paciente_externo
                    AND candidato.tipo=origem.tipo
                    AND candidato.especialidade=origem.especialidade
                JOIN integracao_fonte_dados fd ON fd.id_fonte=candidato.id_fonte
                WHERE pv.id=?
                    AND candidato.id_atendimento_externo<>origem.id_atendimento_externo
                    AND candidato.agendado_em>origem.agendado_em
                    AND candidato.agendado_em>=now()
                    AND candidato.situacao_normalizada IN ('AGENDADO','CONFIRMADO','DESCONHECIDO')
                    AND NOT EXISTS(SELECT 1 FROM pos_venda_atendimento_externo v
                        WHERE v.id_atendimento_externo=candidato.id_atendimento_externo)
                ORDER BY candidato.agendado_em LIMIT 100
                """, id);
        return Map.of("caso",caso,"historico",historico,"consultas",consultas,
                "atendimentosExternos",atendimentosExternos,"alertas",alertas);
    }

    public List<Map<String,Object>> responsaveis() {
        return db.queryForList("SELECT DISTINCT u.id,u.nome FROM usuario u JOIN tenant_membro m ON m.id_usuario=u.id "
                + "JOIN tenant_membro_papel p USING(id_clinica,id_usuario) "
                + "WHERE m.id_clinica=nullif(current_setting('app.tenant_id',true),'')::integer AND m.ativo "
                + "AND p.papel IN ('recepcionista','administrador') ORDER BY u.nome");
    }

    private Map<String,Object> resumoFila(Integer medico) {
        return db.queryForMap("""
                SELECT
                    count(*) FILTER(WHERE pv.status NOT IN ('recuperado','nao_deseja','nao_contatar','registro_corrigido')) AS abertos,
                    count(*) FILTER(WHERE pv.status='reagendado') AS reagendados,
                    count(*) FILTER(WHERE pv.status='recuperado') AS recuperados,
                    count(*) FILTER(WHERE pv.status='nova_falta') AS novas_faltas,
                    count(*) FILTER(WHERE pv.status IN ('pendente','em_contato','sem_resposta','nova_falta')
                        AND (pv.proxima_acao<=now() OR pv.proxima_acao IS NULL)) AS a_contatar,
                    count(*) FILTER(WHERE pv.status IN ('pendente','em_contato','sem_resposta','nova_falta')
                        AND NOT EXISTS(SELECT 1 FROM pos_venda_evento ev WHERE ev.id_caso=pv.id AND ev.tipo='contato')) AS primeira_tentativa,
                    count(*) FILTER(WHERE pv.status IN ('pendente','em_contato','sem_resposta','nova_falta')
                        AND pv.proxima_acao<=now()
                        AND EXISTS(SELECT 1 FROM pos_venda_evento ev WHERE ev.id_caso=pv.id AND ev.tipo='contato')) AS nova_tentativa,
                    count(*) FILTER(WHERE EXISTS(SELECT 1 FROM pos_venda_alerta al
                        WHERE al.id_caso=pv.id AND al.status='aberto' AND al.tipo='resposta_pendente')) AS respostas_pendentes,
                    count(*) FILTER(WHERE EXISTS(SELECT 1 FROM pos_venda_alerta al
                        WHERE al.id_caso=pv.id AND al.status='aberto' AND al.tipo='conferir_presenca')) AS conferir_presenca,
                    count(*) FILTER(WHERE EXISTS(SELECT 1 FROM pos_venda_alerta al
                        WHERE al.id_caso=pv.id AND al.status='aberto' AND al.tipo='revisar_recorrencia')) AS revisar_recorrencia,
                    count(*) FILTER(WHERE EXISTS(SELECT 1 FROM pos_venda_alerta al
                        WHERE al.id_caso=pv.id AND al.status='aberto' AND al.tipo IN ('sem_responsavel','sem_proxima_acao'))) AS pendencias_organizacao,
                    count(*) FILTER(WHERE EXISTS(SELECT 1 FROM pos_venda_alerta al
                        WHERE al.id_caso=pv.id AND al.status='aberto')) AS prioridades
                FROM pos_venda_caso pv
                LEFT JOIN consulta c ON c.id_consulta=pv.id_consulta_origem
                LEFT JOIN agenda a ON a.id_agenda=c.id_agenda
                WHERE (CAST(? AS INTEGER) IS NULL OR a.id_medico=?)
                """, medico, medico);
    }

    private void sincronizarAlertas() {
        db.update("""
                INSERT INTO pos_venda_alerta(id_clinica,id_caso,tipo,mensagem)
                SELECT pv.id_clinica,pv.id,tipo,mensagem
                FROM pos_venda_caso pv
                CROSS JOIN LATERAL (
                    SELECT 'sem_responsavel', 'Defina um responsável para este caso.'
                        WHERE pv.status IN ('pendente','em_contato','sem_resposta','nova_falta') AND pv.id_responsavel IS NULL
                    UNION ALL
                    SELECT 'sem_proxima_acao', 'Defina a próxima ação deste acompanhamento.'
                        WHERE pv.status IN ('pendente','em_contato','sem_resposta','nova_falta') AND pv.proxima_acao IS NULL
                    UNION ALL
                    SELECT 'prazo_vencido', 'A próxima ação está vencida.'
                        WHERE pv.status IN ('pendente','em_contato','sem_resposta','nova_falta') AND pv.proxima_acao<=now()
                    UNION ALL
                    SELECT 'conferir_presenca', 'O horário reagendado passou; confira o resultado da consulta.'
                        WHERE pv.status='reagendado' AND (
                            EXISTS(
                                SELECT 1 FROM consulta cr JOIN agenda ar ON ar.id_agenda=cr.id_agenda
                                WHERE cr.id_consulta=pv.id_consulta_reagendada
                                  AND cr.status_consulta IN ('Agendada','Confirmada')
                                  AND ar.data_slot+ar.hora_slot<=(CURRENT_TIMESTAMP AT TIME ZONE ?)
                            ) OR EXISTS(
                                SELECT 1 FROM integracao_atendimento_externo aer
                                WHERE aer.id_atendimento_externo=pv.id_atendimento_externo_reagendado
                                  AND aer.situacao_normalizada IN ('AGENDADO','CONFIRMADO','DESCONHECIDO')
                                  AND aer.agendado_em<=now()
                            )
                        )
                ) pendencia(tipo,mensagem)
                ON CONFLICT(id_clinica,id_caso,tipo) WHERE status='aberto' DO NOTHING
                """, clinicTimeZone);
        db.update("""
                UPDATE pos_venda_alerta al SET status='resolvido',resolvido_em=now(),atualizado_em=now()
                WHERE al.status='aberto' AND al.tipo<>'resposta_pendente' AND NOT EXISTS(
                    SELECT 1 FROM pos_venda_caso pv WHERE pv.id=al.id_caso AND CASE al.tipo
                        WHEN 'sem_responsavel' THEN pv.status IN ('pendente','em_contato','sem_resposta','nova_falta') AND pv.id_responsavel IS NULL
                        WHEN 'sem_proxima_acao' THEN pv.status IN ('pendente','em_contato','sem_resposta','nova_falta') AND pv.proxima_acao IS NULL
                        WHEN 'prazo_vencido' THEN pv.status IN ('pendente','em_contato','sem_resposta','nova_falta') AND pv.proxima_acao<=now()
                        WHEN 'conferir_presenca' THEN pv.status='reagendado' AND (
                            EXISTS(
                                SELECT 1 FROM consulta cr JOIN agenda ar ON ar.id_agenda=cr.id_agenda
                                WHERE cr.id_consulta=pv.id_consulta_reagendada
                                  AND cr.status_consulta IN ('Agendada','Confirmada')
                                  AND ar.data_slot+ar.hora_slot<=(CURRENT_TIMESTAMP AT TIME ZONE ?)
                            ) OR EXISTS(
                                SELECT 1 FROM integracao_atendimento_externo aer
                                WHERE aer.id_atendimento_externo=pv.id_atendimento_externo_reagendado
                                  AND aer.situacao_normalizada IN ('AGENDADO','CONFIRMADO','DESCONHECIDO')
                                  AND aer.agendado_em<=now()
                            )
                        )
                        WHEN 'revisar_recorrencia' THEN pv.status='nova_falta' AND pv.recorrente
                        ELSE false END
                )
                """, clinicTimeZone);
    }

    @Transactional
    public void agir(long id, PosVendaAcaoRequest r, Integer autor) {
        // Mesma ordem de locks dos gatilhos: atendimento/consulta, depois caso.
        if ("reagendar".equals(r.acao()) && r.idConsulta() != null) {
            db.queryForList("SELECT id_consulta FROM consulta WHERE id_consulta = ? FOR UPDATE", r.idConsulta());
        }
        if ("reagendar".equals(r.acao()) && r.idAtendimentoExterno() != null) {
            db.queryForList("SELECT id_atendimento_externo FROM integracao_atendimento_externo "
                    + "WHERE id_atendimento_externo=? FOR UPDATE", r.idAtendimentoExterno());
        }
        Map<String,Object> caso = db.queryForList("SELECT * FROM pos_venda_caso WHERE id = ? FOR UPDATE", id)
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Caso não encontrado"));
        if (!Objects.equals(caso.get("versao"), r.versao())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "O caso foi atualizado. Reabra os detalhes antes de salvar.");
        }
        if (FECHADOS.contains(caso.get("status"))) throw erro("Este caso está encerrado");
        if ("reagendar".equals(r.acao())) {
            boolean externo = caso.get("id_atendimento_externo_origem") != null;
            if (externo && (r.idAtendimentoExterno() == null || r.idConsulta() != null)) {
                throw erro("Selecione um novo atendimento da mesma fonte externa");
            }
            if (!externo && (r.idConsulta() == null || r.idAtendimentoExterno() != null)) {
                throw erro("Selecione uma nova consulta da agenda");
            }
        }
        Integer paciente = null;
        Long pacienteExterno = null;
        if (caso.get("id_consulta_origem") != null) {
            paciente = db.queryForObject("SELECT id_paciente FROM consulta WHERE id_consulta = ?", Integer.class,
                    caso.get("id_consulta_origem"));
        } else {
            pacienteExterno = db.queryForObject("""
                    SELECT id_paciente_externo FROM integracao_atendimento_externo
                    WHERE id_atendimento_externo=?
                    """, Long.class, caso.get("id_atendimento_externo_origem"));
        }
        boolean bloqueado = paciente != null
                ? Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS (SELECT 1 FROM paciente_retorno_status WHERE id_paciente = ? AND status = 'nao_contatar')", Boolean.class, paciente))
                : Boolean.TRUE.equals(db.queryForObject("SELECT nao_contatar FROM integracao_paciente_externo WHERE id_paciente_externo=?", Boolean.class, pacienteExterno));
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
                    if (!Set.of("telefone","whatsapp","email","presencial").contains(Objects.toString(r.canal(),""))) throw erro("Canal inválido");
                    if (!Set.of("em_contato","sem_resposta").contains(Objects.toString(r.resultado(),""))) throw erro("Resultado inválido");
                    if (!status.equals("reagendado")) status = r.resultado();
                    canal = r.canal();
                    descricao = "Contato registrado: " + r.resultado() + ". " + textoObrigatorio(r.observacao())
                            + " " + auditoriaOrganizacao(caso, r.idResponsavel(), r.proximaAcao());
                } else descricao = "Organização atualizada. "
                        + auditoriaOrganizacao(caso, r.idResponsavel(), r.proximaAcao());
                db.update("""
                        UPDATE pos_venda_caso SET status = ?, id_responsavel = ?, proxima_acao = ?,
                            motivo_falta = ?, recorrente = ? WHERE id = ?
                        """, status, r.idResponsavel(), Timestamp.from(r.proximaAcao().toInstant()),
                        r.motivoFalta(), Boolean.TRUE.equals(r.recorrente()), id);
            }
            case "reagendar" -> {
                if ("reagendado".equals(caso.get("status"))) throw erro("Já existe uma consulta em acompanhamento");
                validarResponsavel(r.idResponsavel());
                Map<String,Object> detalhes = detalhe(id);
                if (caso.get("id_atendimento_externo_origem") != null) {
                    Map<?,?> candidato = ((List<?>) detalhes.get("atendimentosExternos")).stream()
                            .map(Map.class::cast)
                            .filter(item -> Objects.equals(((Number) item.get("id")).longValue(), r.idAtendimentoExterno()))
                            .findFirst().orElseThrow(() -> erro(
                                    "Escolha um atendimento futuro do mesmo paciente, fonte, tipo e especialidade, ainda não vinculado"));
                    int vinculado = db.update("""
                            INSERT INTO pos_venda_atendimento_externo(id_atendimento_externo,id_caso)
                            VALUES (?,?) ON CONFLICT DO NOTHING
                            """, r.idAtendimentoExterno(), id);
                    if (vinculado == 0) throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Atendimento externo já vinculado a outro caso");
                    db.update("""
                            UPDATE pos_venda_caso SET status='reagendado',
                                id_consulta_reagendada=NULL,id_atendimento_externo_reagendado=?,
                                id_responsavel=?,proxima_acao=NULL WHERE id=?
                            """, r.idAtendimentoExterno(), r.idResponsavel(), id);
                    descricao = "Reagendado no atendimento externo " + candidato.get("identificador")
                            + " da fonte " + candidato.get("fonte")
                            + ". Aguardando presença confirmada pela fonte. "
                            + auditoriaOrganizacao(caso, r.idResponsavel(), null);
                } else {
                    boolean elegivel = ((List<?>) detalhes.get("consultas")).stream()
                            .anyMatch(c -> Objects.equals(((Map<?,?>) c).get("id"),r.idConsulta()));
                    if (!elegivel) throw erro("Escolha uma consulta futura do mesmo paciente, tipo e especialidade, ainda não vinculada");
                    // Serializa concorrência entre dois casos tentando usar a mesma consulta.
                    int vinculado = db.update("INSERT INTO pos_venda_consulta(id_consulta,id_caso) VALUES (?,?) ON CONFLICT DO NOTHING",r.idConsulta(),id);
                    if (vinculado == 0) throw new ResponseStatusException(HttpStatus.CONFLICT,"Consulta já vinculada a outro caso");
                    db.update("""
                            UPDATE pos_venda_caso SET status='reagendado',id_consulta_reagendada=?,
                                id_atendimento_externo_reagendado=NULL,id_responsavel=?,proxima_acao=NULL WHERE id=?
                            """, r.idConsulta(),r.idResponsavel(),id);
                    descricao = "Reagendado na consulta #" + r.idConsulta()
                            + ". Aguardando atendimento realizado na agenda. "
                            + auditoriaOrganizacao(caso, r.idResponsavel(), null);
                }
            }
            case "nao_deseja", "nao_contatar" -> {
                descricao = textoObrigatorio(r.observacao());
                db.update("UPDATE pos_venda_caso SET status = ?, proxima_acao = NULL WHERE id = ?",r.acao(),id);
                if (r.acao().equals("nao_contatar")) {
                    if (paciente != null) {
                        db.update("""
                                INSERT INTO paciente_retorno_status(id_paciente,status,motivo_nao_contatar,atualizado_em)
                                VALUES (?,'nao_contatar',?,now()) ON CONFLICT (id_paciente) DO UPDATE
                                SET status = 'nao_contatar', motivo_nao_contatar = excluded.motivo_nao_contatar, atualizado_em = now()
                                """,paciente,descricao);
                    } else {
                        db.update("UPDATE integracao_paciente_externo SET nao_contatar=true,atualizado_em=now() WHERE id_paciente_externo=?",
                                pacienteExterno);
                    }
                }
            }
            case "resposta_recebida" -> {
                if (!Set.of("telefone","whatsapp","email","presencial").contains(Objects.toString(r.canal(),""))) {
                    throw erro("Canal inválido");
                }
                canal = r.canal();
                descricao = "Resposta recebida: " + textoObrigatorio(r.observacao());
                db.update("""
                        UPDATE pos_venda_caso SET
                            status=CASE WHEN status='reagendado' THEN status ELSE 'em_contato' END,
                            proxima_acao=CASE WHEN status='reagendado' THEN NULL ELSE now() END
                        WHERE id=?
                        """, id);
            }
            default -> throw erro("Ação inválida");
        }
        if (Set.of("resposta_recebida","reagendar","nao_deseja","nao_contatar").contains(r.acao())) {
            db.update("""
                    UPDATE mensageria_outbox SET status='cancelada',atualizado_em=now(),erro_categoria='fluxo',
                        erro_codigo=?,erro_mensagem='Envio cancelado após atualização do caso de pós-venda.'
                    WHERE id_pos_venda_caso=? AND status IN ('pendente','retentativa')
                    """, r.acao(),id);
            db.update("""
                    UPDATE mensagem m SET status_entrega='cancelada' FROM mensageria_outbox o
                    WHERE m.id_outbox=o.id_outbox AND o.id_pos_venda_caso=? AND o.status='cancelada'
                        AND m.status_entrega='pendente'
                    """, id);
        }
        db.update("INSERT INTO pos_venda_evento(id_caso,id_usuario,tipo,canal,descricao) VALUES (?,?,?,?,?)",
                id,autor,r.acao(),canal,descricao);
        if ("resposta_recebida".equals(r.acao())) {
            db.update("""
                    INSERT INTO pos_venda_alerta(id_caso,tipo,mensagem)
                    VALUES (?,'resposta_pendente','O paciente respondeu e aguarda uma ação da equipe.')
                    ON CONFLICT(id_clinica,id_caso,tipo) WHERE status='aberto' DO NOTHING
                    """, id);
        } else if (Set.of("contato","reagendar","nao_deseja","nao_contatar").contains(r.acao())) {
            db.update("""
                    UPDATE pos_venda_alerta SET status='resolvido',resolvido_em=now(),atualizado_em=now()
                    WHERE id_caso=? AND tipo='resposta_pendente' AND status='aberto'
                    """, id);
        }
        if (Set.of("organizar","contato","reagendar","nao_deseja","nao_contatar").contains(r.acao())) {
            db.update("""
                    UPDATE pos_venda_alerta SET status='resolvido',resolvido_em=now(),atualizado_em=now()
                    WHERE id_caso=? AND tipo='revisar_recorrencia' AND status='aberto'
                    """, id);
        }
        sincronizarAlertas();
    }

    private String auditoriaOrganizacao(Map<String,Object> caso, Integer novoResponsavel, OffsetDateTime novoPrazo) {
        return "Responsável: " + nomeResponsavel(caso.get("id_responsavel")) + " → " + nomeResponsavel(novoResponsavel)
                + ". Próxima ação: " + dataAuditoria(caso.get("proxima_acao")) + " → " + dataAuditoria(novoPrazo) + ".";
    }

    private String nomeResponsavel(Object id) {
        if (id == null) return "sem responsável";
        return db.queryForList("SELECT nome FROM usuario WHERE id=?", id).stream()
                .map(row -> Objects.toString(row.get("nome"), "usuário #" + id))
                .findFirst().orElse("usuário #" + id);
    }

    static String dataAuditoria(Object valor) {
        if (valor == null) return "não definida";
        if (valor instanceof Timestamp timestamp) return timestamp.toInstant().toString();
        if (valor instanceof OffsetDateTime data) return data.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        return valor.toString();
    }

    private void validarResponsavel(Integer id) {
        if (id == null || !Boolean.TRUE.equals(db.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM tenant_membro m JOIN tenant_membro_papel p USING(id_clinica,id_usuario) "
                        + "WHERE m.id_usuario=? AND m.id_clinica=nullif(current_setting('app.tenant_id',true),'')::integer "
                        + "AND m.ativo AND p.papel IN ('recepcionista','administrador'))",Boolean.class,id))) {
            throw erro("Selecione um responsável administrativo");
        }
    }

    private String textoObrigatorio(String texto) {
        if (texto == null || texto.isBlank()) throw erro("Registre o resultado ou motivo da ação");
        return texto.trim();
    }

    private ResponseStatusException erro(String texto) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,texto); }
}
