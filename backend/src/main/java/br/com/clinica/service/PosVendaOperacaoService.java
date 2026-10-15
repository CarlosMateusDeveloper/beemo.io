package br.com.clinica.service;

import br.com.clinica.dto.PosVendaOperacaoDtos.*;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PosVendaOperacaoService {
    private static final Set<String> PRIORIDADES = Set.of("alta", "media", "baixa");
    private static final Set<String> STATUS_TAREFA = Set.of("aberta", "concluida", "cancelada");
    private static final Set<String> TIPOS_JORNADA = Set.of("retorno", "pos_atendimento", "reativacao", "preventiva");
    private static final Set<String> STATUS_JORNADA = Set.of("rascunho", "ativa", "pausada", "arquivada");
    private static final Set<String> CANAIS_ETAPA = Set.of("whatsapp", "email", "ligacao", "tarefa");
    private static final Set<String> CANAIS_CAMPANHA = Set.of("whatsapp", "email", "multicanal");
    private static final Set<String> STATUS_CAMPANHA = Set.of("rascunho", "agendada", "em_andamento", "pausada", "concluida", "cancelada");

    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final ComunicacaoPolicyService communication;

    public PosVendaOperacaoService(JdbcTemplate db, ObjectMapper json, ComunicacaoPolicyService communication) {
        this.db = db;
        this.json = json;
        this.communication = communication;
    }
    PosVendaOperacaoService(JdbcTemplate db, ObjectMapper json) { this(db,json,new ComunicacaoPolicyService(db)); }

    public Map<String,Object> tarefas(int actor, String status, String busca, int pagina, int tamanho) {
        page(pagina, tamanho);
        String state = blank(status) ? "todas" : status;
        if (!state.equals("todas") && !STATUS_TAREFA.contains(state)) bad("Status de tarefa inválido.");
        String term = clean(busca);
        boolean all = broadOperationalScope();
        String where = " WHERE (?='todas' OR t.status=?) AND (?='' OR strpos(lower(t.titulo),lower(?))>0 "
                + "OR strpos(lower(coalesce(p.nome,t.paciente_referencia,'')),lower(?))>0) AND (? OR t.id_responsavel=?) ";
        Object[] params = {state,state,term,term,term,all,actor};
        long total = db.queryForObject("SELECT count(*) FROM pos_venda_tarefa t LEFT JOIN paciente p ON p.id_paciente=t.id_paciente" + where,
                Long.class, params);
        var args = new ArrayList<>(Arrays.asList(params)); args.add(tamanho); args.add(pagina*tamanho);
        var items = db.queryForList("""
                SELECT t.id_tarefa AS "id",t.titulo,t.descricao,t.id_paciente AS "pacienteId",
                       coalesce(p.nome,t.paciente_referencia) AS paciente,t.id_responsavel AS "responsavelId",
                       u.nome AS responsavel,t.prioridade,t.prazo,t.origem,t.status,t.concluida_em AS "concluidaEm",
                       t.criado_em AS "criadoEm",t.atualizado_em AS "atualizadoEm",t.versao
                FROM pos_venda_tarefa t LEFT JOIN paciente p ON p.id_paciente=t.id_paciente
                LEFT JOIN usuario u ON u.id=t.id_responsavel
                """ + where + " ORDER BY CASE WHEN t.status='aberta' THEN 0 ELSE 1 END,t.prazo,t.id_tarefa DESC LIMIT ? OFFSET ?",
                args.toArray());
        return page(items,total,pagina,tamanho);
    }

    @Transactional
    public Map<String,Object> criarTarefa(int actor, Tarefa input) {
        validateTask(input, false);
        boolean all = broadOperationalScope();
        Integer owner = all && input.responsavelId() != null ? input.responsavelId() : actor;
        member(owner); patient(input.pacienteId());
        long id = db.queryForObject("""
                INSERT INTO pos_venda_tarefa(id_paciente,paciente_referencia,titulo,descricao,id_responsavel,prioridade,
                    prazo,origem,criado_por,atualizado_por) VALUES (?,?,?,?,?,?,?,?,?,?) RETURNING id_tarefa
                """, Long.class, input.pacienteId(), cleanToNull(input.pacienteReferencia()), input.titulo().strip(),
                cleanToNull(input.descricao()), owner, input.prioridade(), Timestamp.from(input.prazo().toInstant()),
                input.origem().strip(), actor, actor);
        history("tarefa",id,"criada",actor,Map.of("titulo",input.titulo().strip()));
        return tarefa(id, actor);
    }

    @Transactional
    public Map<String,Object> atualizarTarefa(long id, int actor, Tarefa input) {
        validateTask(input, true);
        boolean all = broadOperationalScope();
        Integer owner = all && input.responsavelId() != null ? input.responsavelId() : actor;
        member(owner); patient(input.pacienteId());
        int changed = db.update("""
                UPDATE pos_venda_tarefa SET id_paciente=?,paciente_referencia=?,titulo=?,descricao=?,id_responsavel=?,
                    prioridade=?,prazo=?,origem=?,atualizado_por=? WHERE id_tarefa=? AND versao=?
                    AND (? OR id_responsavel=?)
                """, input.pacienteId(),cleanToNull(input.pacienteReferencia()),input.titulo().strip(),cleanToNull(input.descricao()),
                owner,input.prioridade(),Timestamp.from(input.prazo().toInstant()),input.origem().strip(),actor,id,input.versao(),all,actor);
        changed(changed);
        history("tarefa",id,"atualizada",actor,Map.of("versaoAnterior",input.versao()));
        return tarefa(id, actor);
    }

    @Transactional
    public Map<String,Object> statusTarefa(long id, int actor, Status input) {
        if (!STATUS_TAREFA.contains(input.status())) bad("Status de tarefa inválido.");
        boolean all = broadOperationalScope();
        int changed = db.update("UPDATE pos_venda_tarefa SET status=?,concluida_em=CASE WHEN ?='concluida' THEN now() ELSE NULL END,"
                + "atualizado_por=? WHERE id_tarefa=? AND versao=? AND (? OR id_responsavel=?)",
                input.status(),input.status(),actor,id,input.versao(),all,actor);
        changed(changed);
        history("tarefa",id,"status_alterado",actor,Map.of("status",input.status(),"versaoAnterior",input.versao()));
        return tarefa(id, actor);
    }

    private Map<String,Object> tarefa(long id, int actor) {
        boolean all = broadOperationalScope();
        return db.queryForList("""
                SELECT t.id_tarefa AS "id",t.titulo,t.descricao,t.id_paciente AS "pacienteId",
                       coalesce(p.nome,t.paciente_referencia) AS paciente,t.id_responsavel AS "responsavelId",
                       u.nome AS responsavel,t.prioridade,t.prazo,t.origem,t.status,t.concluida_em AS "concluidaEm",
                       t.criado_em AS "criadoEm",t.atualizado_em AS "atualizadoEm",t.versao
                FROM pos_venda_tarefa t LEFT JOIN paciente p ON p.id_paciente=t.id_paciente
                LEFT JOIN usuario u ON u.id=t.id_responsavel WHERE t.id_tarefa=? AND (? OR t.id_responsavel=?)
                """,id,all,actor).stream().findFirst().orElseThrow(PosVendaOperacaoService::notFound);
    }

    public Map<String,Object> jornadas(String status, String busca, int pagina, int tamanho) {
        page(pagina,tamanho);
        String state=blank(status)?"todas":status;
        if(!state.equals("todas")&&!STATUS_JORNADA.contains(state)) bad("Status de jornada inválido.");
        String term=clean(busca);
        String where=" WHERE (?='todas' OR j.status=?) AND (?='' OR strpos(lower(j.nome),lower(?))>0) ";
        long total=db.queryForObject("SELECT count(*) FROM pos_venda_jornada j"+where,Long.class,state,state,term,term);
        var items=db.queryForList("""
                SELECT j.id_jornada AS "id",j.nome,j.descricao,j.tipo,j.regra_entrada::text AS "regraEntrada",
                       j.status,j.id_responsavel AS "responsavelId",u.nome AS responsavel,j.criado_em AS "criadoEm",
                       j.atualizado_em AS "atualizadoEm",j.versao,
                       (SELECT count(*) FROM pos_venda_jornada_inscricao i WHERE i.id_jornada=j.id_jornada) AS inscritos,
                       (SELECT count(*) FROM pos_venda_jornada_inscricao i WHERE i.id_jornada=j.id_jornada AND i.status='concluida') AS concluidos
                FROM pos_venda_jornada j LEFT JOIN usuario u ON u.id=j.id_responsavel
                """+where+" ORDER BY j.atualizado_em DESC,j.id_jornada DESC LIMIT ? OFFSET ?",
                state,state,term,term,tamanho,pagina*tamanho);
        items.forEach(row->row.put("etapas",steps(((Number)row.get("id")).longValue())));
        return page(items,total,pagina,tamanho);
    }

    @Transactional
    public Map<String,Object> criarJornada(int actor, Jornada input) {
        validateJourney(input,false); memberNullable(input.responsavelId());
        long id=db.queryForObject("""
                INSERT INTO pos_venda_jornada(nome,descricao,tipo,regra_entrada,status,id_responsavel,criado_por,atualizado_por)
                VALUES (?,?,?,CAST(? AS jsonb),'rascunho',?,?,?) RETURNING id_jornada
                """,Long.class,input.nome().strip(),cleanToNull(input.descricao()),input.tipo(),json(input.regraEntrada()),
                input.responsavelId(),actor,actor);
        replaceSteps(id,input.etapas());
        history("jornada",id,"criada",actor,Map.of("nome",input.nome().strip()));
        return jornada(id);
    }

    @Transactional
    public Map<String,Object> atualizarJornada(long id,int actor,Jornada input) {
        validateJourney(input,true); memberNullable(input.responsavelId());
        int changed=db.update("UPDATE pos_venda_jornada SET nome=?,descricao=?,tipo=?,regra_entrada=CAST(? AS jsonb),"
                +"id_responsavel=?,atualizado_por=? WHERE id_jornada=? AND versao=?",input.nome().strip(),
                cleanToNull(input.descricao()),input.tipo(),json(input.regraEntrada()),input.responsavelId(),actor,id,input.versao());
        changed(changed); replaceSteps(id,input.etapas());
        history("jornada",id,"atualizada",actor,Map.of("versaoAnterior",input.versao()));
        return jornada(id);
    }

    @Transactional
    public Map<String,Object> statusJornada(long id,int actor,Status input) {
        if(!STATUS_JORNADA.contains(input.status())) bad("Status de jornada inválido.");
        if(input.status().equals("ativa")&&steps(id).isEmpty()) bad("Inclua ao menos uma etapa antes de ativar a jornada.");
        int changed=db.update("UPDATE pos_venda_jornada SET status=?,atualizado_por=? WHERE id_jornada=? AND versao=?",
                input.status(),actor,id,input.versao()); changed(changed);
        history("jornada",id,"status_alterado",actor,Map.of("status",input.status(),"versaoAnterior",input.versao()));
        return jornada(id);
    }

    @Transactional
    public Map<String,Object> inscrever(long journey,int actor,Inscricao input) {
        patient(input.pacienteId());
        String status=db.queryForList("SELECT status FROM pos_venda_jornada WHERE id_jornada=?",String.class,journey)
                .stream().findFirst().orElseThrow(PosVendaOperacaoService::notFound);
        if(!status.equals("ativa")) bad("Somente jornadas ativas recebem inscrições.");
        try {
            long id=db.queryForObject("INSERT INTO pos_venda_jornada_inscricao(id_jornada,id_paciente,criado_por) VALUES (?,?,?) RETURNING id_inscricao",
                    Long.class,journey,input.pacienteId(),actor);
            history("inscricao",id,"criada",actor,Map.of("jornadaId",journey,"pacienteId",input.pacienteId()));
            return db.queryForMap("SELECT id_inscricao AS \"id\",id_jornada AS \"jornadaId\",id_paciente AS \"pacienteId\","
                    +"etapa_atual AS \"etapaAtual\",status,inscrito_em AS \"inscritoEm\",versao FROM pos_venda_jornada_inscricao WHERE id_inscricao=?",id);
        } catch(org.springframework.dao.DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Paciente já inscrito nesta jornada.");
        }
    }

    private Map<String,Object> jornada(long id) {
        var row=db.queryForList("""
                SELECT j.id_jornada AS "id",j.nome,j.descricao,j.tipo,j.regra_entrada::text AS "regraEntrada",
                       j.status,j.id_responsavel AS "responsavelId",u.nome AS responsavel,j.criado_em AS "criadoEm",
                       j.atualizado_em AS "atualizadoEm",j.versao,
                       (SELECT count(*) FROM pos_venda_jornada_inscricao i WHERE i.id_jornada=j.id_jornada) AS inscritos,
                       (SELECT count(*) FROM pos_venda_jornada_inscricao i WHERE i.id_jornada=j.id_jornada AND i.status='concluida') AS concluidos
                FROM pos_venda_jornada j LEFT JOIN usuario u ON u.id=j.id_responsavel WHERE j.id_jornada=?
                """,id).stream().findFirst().orElseThrow(PosVendaOperacaoService::notFound);
        row.put("etapas",steps(id)); return row;
    }

    private List<Map<String,Object>> steps(long id) {
        return db.queryForList("SELECT id_etapa AS \"id\",ordem,atraso_dias AS \"atrasoDias\",titulo,canal,conteudo "
                +"FROM pos_venda_jornada_etapa WHERE id_jornada=? ORDER BY ordem,id_etapa",id);
    }

    private void replaceSteps(long id,List<Etapa> steps) {
        if(steps.stream().map(Etapa::ordem).distinct().count()!=steps.size()) bad("A ordem das etapas não pode se repetir.");
        db.update("DELETE FROM pos_venda_jornada_etapa WHERE id_jornada=?",id);
        steps.stream().sorted(Comparator.comparing(Etapa::ordem)).forEach(step->{
            if(!CANAIS_ETAPA.contains(step.canal())) bad("Canal de etapa inválido.");
            db.update("INSERT INTO pos_venda_jornada_etapa(id_jornada,ordem,atraso_dias,titulo,canal,conteudo) VALUES (?,?,?,?,?,?)",
                    id,step.ordem(),step.atrasoDias(),step.titulo().strip(),step.canal(),cleanToNull(step.conteudo()));
        });
    }

    public Map<String,Object> campanhas(String status,String busca,int pagina,int tamanho) {
        page(pagina,tamanho); String state=blank(status)?"todas":status;
        if(!state.equals("todas")&&!STATUS_CAMPANHA.contains(state)) bad("Status de campanha inválido.");
        String term=clean(busca); String where=" WHERE (?='todas' OR c.status=?) AND (?='' OR strpos(lower(c.nome),lower(?))>0) ";
        long total=db.queryForObject("SELECT count(*) FROM pos_venda_campanha c"+where,Long.class,state,state,term,term);
        var items=db.queryForList("""
                SELECT c.id_campanha AS "id",c.nome,c.objetivo,c.publico::text AS publico,c.canal,c.conteudo,
                       c.id_modelo_comunicacao AS "modeloId",m.codigo AS "modeloCodigo",m.versao AS "modeloVersao",
                       c.programado_para AS "programadoPara",c.status,c.id_responsavel AS "responsavelId",u.nome AS responsavel,
                       c.destinatarios,c.enviados,c.entregues,c.respostas,c.conversoes,c.criado_em AS "criadoEm",
                       c.atualizado_em AS "atualizadoEm",c.versao
                FROM pos_venda_campanha c LEFT JOIN usuario u ON u.id=c.id_responsavel
                LEFT JOIN comunicacao_modelo m ON m.id_modelo=c.id_modelo_comunicacao
                """+where+" ORDER BY c.atualizado_em DESC,c.id_campanha DESC LIMIT ? OFFSET ?",
                state,state,term,term,tamanho,pagina*tamanho);
        return page(items,total,pagina,tamanho);
    }

    @Transactional
    public Map<String,Object> criarCampanha(int actor,Campanha input) {
        validateCampaign(input,false); memberNullable(input.responsavelId()); patients(input.pacientes());
        long id=db.queryForObject("""
                INSERT INTO pos_venda_campanha(nome,objetivo,publico,canal,conteudo,programado_para,status,id_responsavel,
                    id_modelo_comunicacao,criado_por,atualizado_por) VALUES (?,?,CAST(? AS jsonb),?,?,?,? ,?,?,?,?) RETURNING id_campanha
                """,Long.class,input.nome().strip(),input.objetivo().strip(),json(input.publico()),input.canal(),input.conteudo().strip(),
                timestamp(input.programadoPara()),input.programadoPara()==null?"rascunho":"agendada",input.responsavelId(),input.modeloId(),actor,actor);
        replaceAudience(id,input.pacientes());
        if(input.programadoPara()!=null) prepararCampanha(id);
        history("campanha",id,"criada",actor,Map.of("nome",input.nome().strip()));
        return campanha(id);
    }

    @Transactional
    public Map<String,Object> atualizarCampanha(long id,int actor,Campanha input) {
        validateCampaign(input,true); memberNullable(input.responsavelId()); patients(input.pacientes());
        int changed=db.update("UPDATE pos_venda_campanha SET nome=?,objetivo=?,publico=CAST(? AS jsonb),canal=?,conteudo=?,"
                +"programado_para=?,status=CASE WHEN status='rascunho' AND ? IS NOT NULL THEN 'agendada' "
                +"WHEN status='agendada' AND ? IS NULL THEN 'rascunho' ELSE status END,id_responsavel=?,id_modelo_comunicacao=?,atualizado_por=? "
                +"WHERE id_campanha=? AND versao=?",input.nome().strip(),input.objetivo().strip(),json(input.publico()),
                input.canal(),input.conteudo().strip(),timestamp(input.programadoPara()),timestamp(input.programadoPara()),
                timestamp(input.programadoPara()),input.responsavelId(),input.modeloId(),actor,id,input.versao()); changed(changed);
        replaceAudience(id,input.pacientes());
        if(input.programadoPara()!=null) prepararCampanha(id);
        history("campanha",id,"atualizada",actor,Map.of("versaoAnterior",input.versao())); return campanha(id);
    }

    @Transactional
    public Map<String,Object> statusCampanha(long id,int actor,Status input) {
        if(!STATUS_CAMPANHA.contains(input.status())) bad("Status de campanha inválido.");
        if(Set.of("agendada","em_andamento").contains(input.status())) prepararCampanha(id);
        int changed=db.update("UPDATE pos_venda_campanha SET status=?,atualizado_por=? WHERE id_campanha=? AND versao=?",
                input.status(),actor,id,input.versao()); changed(changed);
        history("campanha",id,"status_alterado",actor,Map.of("status",input.status(),"versaoAnterior",input.versao()));
        return campanha(id);
    }

    private Map<String,Object> campanha(long id) {
        return db.queryForList("""
                SELECT c.id_campanha AS "id",c.nome,c.objetivo,c.publico::text AS publico,c.canal,c.conteudo,
                       c.id_modelo_comunicacao AS "modeloId",m.codigo AS "modeloCodigo",m.versao AS "modeloVersao",
                       c.programado_para AS "programadoPara",c.status,c.id_responsavel AS "responsavelId",u.nome AS responsavel,
                       c.destinatarios,c.enviados,c.entregues,c.respostas,c.conversoes,c.criado_em AS "criadoEm",
                       c.atualizado_em AS "atualizadoEm",c.versao
                FROM pos_venda_campanha c LEFT JOIN usuario u ON u.id=c.id_responsavel
                LEFT JOIN comunicacao_modelo m ON m.id_modelo=c.id_modelo_comunicacao WHERE c.id_campanha=?
                """,id).stream().findFirst().orElseThrow(PosVendaOperacaoService::notFound);
    }

    private void replaceAudience(long id,List<Integer> patients) {
        if(patients==null) return;
        db.update("DELETE FROM pos_venda_campanha_publico WHERE id_campanha=?",id);
        patients.stream().distinct().forEach(patient->db.update(
                "INSERT INTO pos_venda_campanha_publico(id_campanha,id_paciente) VALUES (?,?)",id,patient));
        db.update("UPDATE pos_venda_campanha SET destinatarios=? WHERE id_campanha=?",patients.stream().distinct().count(),id);
    }

    private void prepararCampanha(long id) {
        var campaign=db.queryForMap("""
                SELECT canal,finalidade,id_modelo_comunicacao FROM pos_venda_campanha WHERE id_campanha=?
                """,id);
        String channel=Objects.toString(campaign.get("canal"));
        if(channel.equals("multicanal")) bad("Defina um canal específico antes de iniciar a campanha.");
        Long model=campaign.get("id_modelo_comunicacao")==null?null:
                ((Number)campaign.get("id_modelo_comunicacao")).longValue();
        var audience=db.queryForList("SELECT id_paciente FROM pos_venda_campanha_publico WHERE id_campanha=?",id);
        int eligible=0;
        for(var row:audience) {
            int patient=((Number)row.get("id_paciente")).intValue();
            var decision=communication.avaliar(patient,null,channel,Objects.toString(campaign.get("finalidade")),
                    false,model,"campanha_preparacao",null,false);
            db.update("UPDATE pos_venda_campanha_publico SET status=?,motivo_exclusao=? WHERE id_campanha=? AND id_paciente=?",
                    decision.permitido()?"incluido":"excluido",decision.permitido()?null:decision.motivo(),id,patient);
            if(decision.permitido()) eligible++;
        }
        if(eligible==0) bad("A campanha não possui destinatários elegíveis pela política de comunicação.");
    }

    private boolean broadOperationalScope() {
        var access=TenantContext.get();
        return access!=null && access.papeis().stream().anyMatch(role->role.equals(PermissionCatalog.RECEPCIONISTA)
                || role.equals(PermissionCatalog.ADMINISTRADOR));
    }
    private void validateTask(Tarefa input,boolean update) {
        if(!PRIORIDADES.contains(input.prioridade())) bad("Prioridade inválida.");
        if(update&&input.versao()==null) bad("Informe a versão atual da tarefa.");
    }
    private void validateJourney(Jornada input,boolean update) {
        if(!TIPOS_JORNADA.contains(input.tipo())) bad("Tipo de jornada inválido.");
        if(update&&input.versao()==null) bad("Informe a versão atual da jornada.");
        input.etapas().forEach(step->{ if(!CANAIS_ETAPA.contains(step.canal())) bad("Canal de etapa inválido."); });
    }
    private void validateCampaign(Campanha input,boolean update) {
        if(!CANAIS_CAMPANHA.contains(input.canal())) bad("Canal de campanha inválido.");
        if(update&&input.versao()==null) bad("Informe a versão atual da campanha.");
    }
    private void member(Integer id) {
        if(id==null||!Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM tenant_membro WHERE id_clinica=? AND id_usuario=? AND ativo)",
                Boolean.class,TenantContext.id(),id))) bad("Responsável não pertence à clínica.");
    }
    private void memberNullable(Integer id) { if(id!=null) member(id); }
    private void patient(Integer id) {
        if(id!=null&&!Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM paciente WHERE id_paciente=?)",Boolean.class,id)))
            bad("Paciente não encontrado.");
    }
    private void patients(List<Integer> ids) { if(ids!=null) ids.stream().distinct().forEach(this::patient); }
    private void history(String resource,long id,String action,int actor,Map<String,?> data) {
        db.update("INSERT INTO pos_venda_historico(recurso,recurso_id,acao,id_usuario,dados) VALUES (?,?,?,?,CAST(? AS jsonb))",
                resource,id,action,actor,json(data));
    }
    private String json(Object value) { try { return json.writeValueAsString(value); } catch(JacksonException e) { throw new IllegalArgumentException(e); } }
    private static Timestamp timestamp(OffsetDateTime value) { return value==null?null:Timestamp.from(value.toInstant()); }
    private static String clean(String value) { return value==null?"":value.strip(); }
    private static String cleanToNull(String value) { String result=clean(value); return result.isEmpty()?null:result; }
    private static boolean blank(String value) { return value==null||value.isBlank(); }
    private static void page(int page,int size) { if(page<0||size<1||size>100) bad("Paginação inválida."); }
    private static Map<String,Object> page(List<Map<String,Object>> items,long total,int page,int size) {
        var result=new LinkedHashMap<String,Object>(); result.put("itens",items); result.put("total",total);
        result.put("pagina",page); result.put("tamanho",size); result.put("totalPaginas",(total+size-1)/size); return result;
    }
    private static void changed(int count) { if(count==0) throw new ResponseStatusException(HttpStatus.CONFLICT,"O registro foi alterado por outra pessoa. Atualize a página."); }
    private static void bad(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
    private static ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND,"Registro não encontrado."); }
}
