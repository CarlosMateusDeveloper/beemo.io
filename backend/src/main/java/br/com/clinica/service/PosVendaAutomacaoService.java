package br.com.clinica.service;

import br.com.clinica.dto.PosVendaAutomacaoDtos.Regra;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PosVendaAutomacaoService {
    static final Set<String> EVENTOS = Set.of(
            "CONSULTA_CRIADA","CONSULTA_CONFIRMADA","CONSULTA_CANCELADA","CONSULTA_FALTOU",
            "CONSULTA_REALIZADA","PRONTUARIO_PROCEDIMENTO_CONCLUIDO","PRONTUARIO_RETORNO_INDICADO");
    static final Set<String> ACOES = Set.of(
            "criar_oportunidade","atualizar_oportunidade","criar_tarefa","inscrever_jornada",
            "avancar_jornada","pausar_jornada","concluir_jornada");
    static final Set<String> STATUS_EVENTO = Set.of("pendente","processando","processado","ignorado","erro");

    private final JdbcTemplate db;
    private final ObjectMapper json;

    public PosVendaAutomacaoService(JdbcTemplate db, ObjectMapper json) {
        this.db = db; this.json = json;
    }

    public Map<String,Object> catalogo() {
        return Map.of("eventos", EVENTOS.stream().sorted().toList(), "acoes", ACOES.stream().sorted().toList());
    }

    public List<Map<String,Object>> regras() {
        return db.queryForList("""
                SELECT r.id_regra AS id,r.nome,r.evento_tipo AS "eventoTipo",r.acao,
                    r.id_jornada AS "jornadaId",j.nome AS jornada,r.id_responsavel AS "responsavelId",
                    u.nome AS responsavel,r.configuracao,r.prioridade,r.ativa,
                    r.criado_em AS "criadoEm",r.atualizado_em AS "atualizadoEm",r.versao
                FROM pos_venda_automacao_regra r
                LEFT JOIN pos_venda_jornada j ON j.id_jornada=r.id_jornada
                LEFT JOIN usuario u ON u.id=r.id_responsavel
                ORDER BY r.prioridade,r.id_regra
                """);
    }

    @Transactional
    public Map<String,Object> criar(int actor, Regra input) {
        validar(input, false);
        long id = db.queryForObject("""
                INSERT INTO pos_venda_automacao_regra(
                  nome,evento_tipo,acao,id_jornada,id_responsavel,configuracao,prioridade,ativa,criado_por,atualizado_por
                ) VALUES (?,?,?,?,?,CAST(? AS jsonb),?,?,?,?) RETURNING id_regra
                """, Long.class, input.nome().strip(),input.eventoTipo(),input.acao(),input.jornadaId(),
                input.responsavelId(),json(input.configuracao()),input.prioridade(),input.ativa(),actor,actor);
        return regra(id);
    }

    @Transactional
    public Map<String,Object> atualizar(long id, int actor, Regra input) {
        validar(input, true);
        int changed = db.update("""
                UPDATE pos_venda_automacao_regra SET nome=?,evento_tipo=?,acao=?,id_jornada=?,
                  id_responsavel=?,configuracao=CAST(? AS jsonb),prioridade=?,ativa=?,atualizado_por=?
                WHERE id_regra=? AND versao=?
                """,input.nome().strip(),input.eventoTipo(),input.acao(),input.jornadaId(),input.responsavelId(),
                json(input.configuracao()),input.prioridade(),input.ativa(),actor,id,input.versao());
        if (changed==0) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "A regra foi alterada por outra pessoa. Atualize a página.");
        return regra(id);
    }

    public Map<String,Object> eventos(String status, int pagina, int tamanho) {
        if (pagina<0 || tamanho<1 || tamanho>100) bad("Paginação inválida.");
        String state = status==null || status.isBlank() ? "todos" : status;
        if (!state.equals("todos") && !STATUS_EVENTO.contains(state)) bad("Status de evento inválido.");
        long total = db.queryForObject("SELECT count(*) FROM pos_venda_evento_clinico WHERE (?='todos' OR status=?)",
                Long.class,state,state);
        var items = db.queryForList("""
                SELECT e.id_evento AS id,e.identificador,e.tipo,e.versao,e.ocorrido_em AS "ocorridoEm",
                    e.origem_tipo AS "origemTipo",e.origem_id AS "origemId",e.origem_versao AS "origemVersao",
                    e.id_paciente AS "pacienteId",p.nome AS paciente,e.payload,e.status,e.tentativas,e.erro,
                    e.recebido_em AS "recebidoEm",e.processado_em AS "processadoEm",
                    count(x.id_execucao) AS execucoes,
                    count(x.id_execucao) FILTER(WHERE x.status='erro') AS "execucoesComErro"
                FROM pos_venda_evento_clinico e LEFT JOIN paciente p ON p.id_paciente=e.id_paciente
                LEFT JOIN pos_venda_automacao_execucao x ON x.id_evento=e.id_evento
                WHERE (?='todos' OR e.status=?) GROUP BY e.id_evento,p.nome
                ORDER BY e.id_evento DESC LIMIT ? OFFSET ?
                """,state,state,tamanho,pagina*tamanho);
        return Map.of("itens",items,"total",total,"pagina",pagina,"tamanho",tamanho);
    }

    public Map<String,Object> evento(long id) {
        var event = db.queryForList("""
                SELECT e.id_evento AS id,e.identificador,e.tipo,e.versao,e.ocorrido_em AS "ocorridoEm",
                    e.origem_tipo AS "origemTipo",e.origem_id AS "origemId",e.origem_versao AS "origemVersao",
                    e.id_paciente AS "pacienteId",p.nome AS paciente,e.payload,e.status,e.tentativas,e.erro,
                    e.recebido_em AS "recebidoEm",e.processado_em AS "processadoEm"
                FROM pos_venda_evento_clinico e LEFT JOIN paciente p ON p.id_paciente=e.id_paciente
                WHERE e.id_evento=?
                """,id).stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Evento não encontrado."));
        event.put("execucoes",db.queryForList("""
                SELECT x.id_execucao AS id,x.status,x.tentativas,x.explicacao,x.id_caso AS "casoId",
                    x.id_tarefa AS "tarefaId",x.id_inscricao AS "inscricaoId",x.erro,
                    x.iniciado_em AS "iniciadoEm",x.concluido_em AS "concluidoEm",
                    r.id_regra AS "regraId",r.nome AS regra,r.acao
                FROM pos_venda_automacao_execucao x JOIN pos_venda_automacao_regra r ON r.id_regra=x.id_regra
                WHERE x.id_evento=? ORDER BY x.id_execucao
                """,id));
        return event;
    }

    @Transactional
    public Map<String,Object> reprocessar(long id) {
        String status = db.queryForList("SELECT status FROM pos_venda_evento_clinico WHERE id_evento=? FOR UPDATE",
                String.class,id).stream().findFirst().orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND,"Evento não encontrado."));
        if (!status.equals("erro")) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Somente eventos com erro podem ser reprocessados.");
        db.update("UPDATE pos_venda_evento_clinico SET status='pendente',erro=NULL,processado_em=NULL WHERE id_evento=?",id);
        db.queryForList("SELECT pos_venda_processar_evento(?)",id);
        return evento(id);
    }

    private Map<String,Object> regra(long id) {
        return db.queryForList("""
                SELECT id_regra AS id,nome,evento_tipo AS "eventoTipo",acao,id_jornada AS "jornadaId",
                  id_responsavel AS "responsavelId",configuracao,prioridade,ativa,
                  criado_em AS "criadoEm",atualizado_em AS "atualizadoEm",versao
                FROM pos_venda_automacao_regra WHERE id_regra=?
                """,id).stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private void validar(Regra input, boolean update) {
        if (!EVENTOS.contains(input.eventoTipo())) bad("Tipo de evento inválido.");
        if (!ACOES.contains(input.acao())) bad("Ação automática inválida.");
        if (input.acao().equals("inscrever_jornada") && input.jornadaId()==null)
            bad("Selecione a jornada para a regra de inscrição.");
        if (input.jornadaId()!=null && !Boolean.TRUE.equals(db.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM pos_venda_jornada WHERE id_jornada=?)",Boolean.class,input.jornadaId())))
            bad("Jornada não encontrada.");
        if (input.responsavelId()!=null && !Boolean.TRUE.equals(db.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM tenant_membro WHERE id_usuario=? AND ativo)",Boolean.class,input.responsavelId())))
            bad("Responsável não pertence à clínica.");
        if (update && input.versao()==null) bad("Informe a versão atual da regra.");
    }

    private String json(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JacksonException exception) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Configuração inválida."); }
    }
    private static void bad(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
}
