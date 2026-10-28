package br.com.clinica.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SuporteService {
    private static final Set<String> CATEGORIAS=Set.of("implantacao","agenda","mensagens","acesso","financeiro","outro");
    private static final Set<String> PRIORIDADES=Set.of("critica","alta","normal","baixa");
    private static final Set<String> STATUS=Set.of("ABERTO","EM_ATENDIMENTO","AGUARDANDO_CLINICA","RESOLVIDO","FECHADO");
    private final JdbcTemplate db;
    private final ObjectMapper json;
    public SuporteService(JdbcTemplate db,ObjectMapper json) { this.db=db;this.json=json; }

    public List<Map<String,Object>> artigos() {
        return List.of(
            article("primeiros-passos","Primeiros passos","implantacao","Configure a clínica, convide a equipe e confira os papéis antes de importar os pacientes."),
            article("agenda","Organizar a agenda","agenda","Revise profissionais, horários e bloqueios. Use um paciente de teste antes de liberar a operação."),
            article("mensagens","Mensagens e WhatsApp","mensagens","Valide consentimento, modelo e destinatário. Nunca inclua diagnóstico ou dado clínico desnecessário."),
            article("acesso","Acesso e permissões","acesso","Cada pessoa deve usar sua própria conta. Administradores concedem somente as permissões necessárias."),
            article("financeiro","Conferência financeira","financeiro","Confira lançamentos e responsáveis. Não envie cartão, senha, prontuário ou documento sensível em chamados."),
            article("diagnostico","Diagnóstico seguro","outro","Informe tela, horário, ação e mensagem de erro. Remova nomes de pacientes e dados clínicos de imagens e anexos.")
        );
    }
    private Map<String,Object> article(String id,String title,String category,String body) {
        return Map.of("id",id,"titulo",title,"categoria",category,"conteudo",body);
    }

    public List<Map<String,Object>> chamados() {
        return db.queryForList("""
            SELECT c.id_chamado,c.protocolo,c.titulo,c.categoria,c.prioridade,c.status,c.versao,
              c.criado_em,c.atualizado_em,c.primeira_resposta_em,c.resolvido_em,c.satisfacao,
              u.nome solicitante,r.nome responsavel,
              c.criado_em + CASE c.prioridade WHEN 'critica' THEN interval '30 minutes'
                WHEN 'alta' THEN interval '2 hours' WHEN 'normal' THEN interval '8 hours' ELSE interval '24 hours' END sla_resposta_em
            FROM suporte_chamado c JOIN usuario u ON u.id=c.id_solicitante
              LEFT JOIN usuario r ON r.id=c.id_responsavel
            ORDER BY CASE c.status WHEN 'ABERTO' THEN 0 WHEN 'EM_ATENDIMENTO' THEN 1 WHEN 'AGUARDANDO_CLINICA' THEN 2 ELSE 3 END,
              CASE c.prioridade WHEN 'critica' THEN 0 WHEN 'alta' THEN 1 WHEN 'normal' THEN 2 ELSE 3 END,c.criado_em
            """);
    }

    public Map<String,Object> chamado(long id) {
        var rows=db.queryForList("""
            SELECT c.*,u.nome solicitante,r.nome responsavel FROM suporte_chamado c
            JOIN usuario u ON u.id=c.id_solicitante LEFT JOIN usuario r ON r.id=c.id_responsavel
            WHERE c.id_chamado=?
            """,id);
        if(rows.isEmpty()) throw missing();
        var result=new java.util.LinkedHashMap<String,Object>(rows.getFirst());
        boolean support=TenantContext.get()!=null && TenantContext.get().suporteTemporario();
        result.put("mensagens",db.queryForList("""
            SELECT m.id_mensagem,m.mensagem,m.interna,m.criado_em,u.nome autor
            FROM suporte_chamado_mensagem m JOIN usuario u ON u.id=m.id_autor
            WHERE m.id_chamado=? AND (? OR NOT m.interna) ORDER BY m.criado_em
            """,id,support));
        result.put("historico",db.queryForList("""
            SELECT e.tipo,e.detalhes,e.criado_em,u.nome autor FROM suporte_chamado_evento e
            JOIN usuario u ON u.id=e.id_autor WHERE e.id_chamado=? ORDER BY e.criado_em
            """,id));
        return result;
    }

    @Transactional
    public Map<String,Object> criar(int actor,String titulo,String descricao,String categoria,String prioridade) {
        if(TenantContext.get()!=null && TenantContext.get().suporteTemporario())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"O suporte não pode abrir chamados em nome da clínica.");
        validate(titulo,descricao,categoria,prioridade);
        String protocol="SUP-"+java.time.LocalDate.now().toString().replace("-","")+"-"+UUID.randomUUID().toString().substring(0,6).toUpperCase(Locale.ROOT);
        Long id=db.queryForObject("""
            INSERT INTO suporte_chamado(protocolo,titulo,descricao,categoria,prioridade,id_solicitante)
            VALUES (?,?,?,?,?,?) RETURNING id_chamado
            """,Long.class,protocol,titulo.strip(),descricao.strip(),categoria,prioridade,actor);
        event(id,actor,"CRIADO",Map.of("prioridade",prioridade,"categoria",categoria));
        return chamado(id);
    }

    @Transactional
    public Map<String,Object> mensagem(int actor,long id,String message,boolean internal) {
        if(message==null || message.isBlank() || message.length()>4000) bad("A mensagem deve ter entre 1 e 4000 caracteres.");
        boolean support=TenantContext.get()!=null && TenantContext.get().suporteTemporario();
        if(internal && !support) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Mensagem interna é exclusiva do suporte.");
        ensure(id);
        db.update("INSERT INTO suporte_chamado_mensagem(id_chamado,id_autor,mensagem,interna) VALUES (?,?,?,?)",id,actor,message.strip(),internal);
        if(support) db.update("UPDATE suporte_chamado SET primeira_resposta_em=coalesce(primeira_resposta_em,now()),status=CASE WHEN status='ABERTO' THEN 'EM_ATENDIMENTO' ELSE status END,id_responsavel=coalesce(id_responsavel,?) WHERE id_chamado=?",actor,id);
        event(id,actor,"MENSAGEM",Map.of("interna",internal));
        return chamado(id);
    }

    @Transactional
    public Map<String,Object> status(int actor,long id,String value,int version,Integer responsible,Integer effort) {
        value=value==null?"":value.toUpperCase(Locale.ROOT);
        if(!STATUS.contains(value)) bad("Status inválido.");
        int changed=db.update("""
            UPDATE suporte_chamado SET status=?,id_responsavel=coalesce(?,id_responsavel),
              esforco_minutos=esforco_minutos+coalesce(?,0),
              resolvido_em=CASE WHEN ? IN ('RESOLVIDO','FECHADO') THEN coalesce(resolvido_em,now()) ELSE NULL END,
              reaberturas=reaberturas+CASE WHEN status IN ('RESOLVIDO','FECHADO') AND ? IN ('ABERTO','EM_ATENDIMENTO') THEN 1 ELSE 0 END
            WHERE id_chamado=? AND versao=?
            """,value,responsible,effort,value,value,id,version);
        if(changed!=1) throw new ResponseStatusException(HttpStatus.CONFLICT,"O chamado foi alterado. Atualize a tela.");
        event(id,actor,"STATUS",Map.of("status",value));
        return chamado(id);
    }

    @Transactional
    public Map<String,Object> satisfacao(int actor,long id,int rating,int version) {
        if(rating<1 || rating>5) bad("A nota deve estar entre 1 e 5.");
        if(db.update("UPDATE suporte_chamado SET satisfacao=? WHERE id_chamado=? AND versao=? AND status IN ('RESOLVIDO','FECHADO')",rating,id,version)!=1)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Somente chamados concluídos e atualizados podem ser avaliados.");
        event(id,actor,"SATISFACAO",Map.of("nota",rating));
        return chamado(id);
    }

    public Map<String,Object> metricas() {
        return db.queryForMap("""
            SELECT count(*) total,count(*) FILTER(WHERE status NOT IN ('RESOLVIDO','FECHADO')) abertos,
              count(*) FILTER(WHERE reaberturas>0) reabertos,
              round(avg(extract(epoch FROM (primeira_resposta_em-criado_em))/60) FILTER(WHERE primeira_resposta_em IS NOT NULL),1) minutos_primeira_resposta,
              round(avg(extract(epoch FROM (resolvido_em-criado_em))/3600) FILTER(WHERE resolvido_em IS NOT NULL),1) horas_resolucao,
              round(avg(satisfacao),1) satisfacao_media,coalesce(sum(esforco_minutos),0) esforco_minutos
            FROM suporte_chamado
            """);
    }

    public List<Map<String,Object>> acessos() {
        return db.queryForList("""
            SELECT a.id_acesso,a.motivo,a.escopos,a.status,a.solicitado_em,a.autorizado_em,a.expira_em,a.revogado_em,
              ag.nome agente,ag.email agente_email,s.nome solicitante,z.nome autorizador
            FROM auth_suporte_acesso a JOIN usuario ag ON ag.id=a.id_agente JOIN usuario s ON s.id=a.id_solicitante
              LEFT JOIN usuario z ON z.id=a.id_autorizador WHERE a.id_clinica=? ORDER BY a.solicitado_em DESC
            """,TenantContext.id());
    }

    @Transactional
    public Map<String,Object> solicitarAcesso(int actor,String email,String reason,Set<String> scopes,OffsetDateTime expires) {
        if(email==null || email.isBlank() || reason==null || reason.isBlank()) bad("Informe agente e motivo.");
        if(scopes==null || scopes.isEmpty() || !scopes.stream().allMatch(SupportAccessPolicy::valid)) bad("Escopo de suporte inválido.");
        var now=OffsetDateTime.now();
        if(expires==null || expires.isBefore(now.plusMinutes(15)) || expires.isAfter(now.plusHours(8))) bad("O acesso deve durar entre 15 minutos e 8 horas.");
        var users=db.queryForList("SELECT id FROM usuario WHERE lower(email)=lower(?)",Integer.class,email.strip());
        if(users.isEmpty()) bad("O agente precisa entrar no Clinisis ao menos uma vez antes da solicitação.");
        int agent=users.getFirst();
        if(agent==actor) bad("O solicitante não pode ser o agente de suporte.");
        Long id=db.queryForObject("""
            INSERT INTO auth_suporte_acesso(id_clinica,id_agente,id_solicitante,motivo,escopos,expira_em)
            VALUES (?,?,?,?,?::jsonb,?) RETURNING id_acesso
            """,Long.class,TenantContext.id(),agent,actor,reason.strip(),encode(scopes),expires);
        grantEvent(id,actor,"SOLICITADO",Map.of("escopos",scopes,"motivo",reason));
        return acesso(id);
    }

    @Transactional
    public Map<String,Object> decidirAcesso(int actor,long id,boolean approve) {
        var access=acesso(id);
        int agent=((Number)access.get("id_agente")).intValue();
        int requester=((Number)access.get("id_solicitante")).intValue();
        if(actor==agent || actor==requester) bad("A solicitação exige autorização de outra pessoa responsável pela clínica.");
        String status=approve?"APROVADO":"NEGADO";
        int changed=db.update("""
            UPDATE auth_suporte_acesso SET status=?,id_autorizador=?,autorizado_em=CASE WHEN ? THEN now() ELSE NULL END
            WHERE id_acesso=? AND id_clinica=? AND status='PENDENTE' AND expira_em>now()
            """,status,actor,approve,id,TenantContext.id());
        if(changed!=1) throw new ResponseStatusException(HttpStatus.CONFLICT,"A solicitação não está mais disponível.");
        grantEvent(id,actor,status,Map.of());
        return acesso(id);
    }

    @Transactional
    public void revogarAcesso(int actor,long id) {
        var access=acesso(id);
        int agent=((Number)access.get("id_agente")).intValue();
        if(db.update("UPDATE auth_suporte_acesso SET status='REVOGADO',revogado_em=now() WHERE id_acesso=? AND id_clinica=? AND status='APROVADO' AND revogado_em IS NULL",id,TenantContext.id())!=1)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"O acesso já foi encerrado.");
        db.update("UPDATE auth_session SET id_clinica=NULL WHERE id_usuario=? AND id_clinica=?",agent,TenantContext.id());
        grantEvent(id,actor,"REVOGADO",Map.of());
    }

    public Map<String,Object> modeloAcesso() { return Map.of("escopos",SupportAccessPolicy.matrix(),"duracaoMaximaHoras",8); }

    private Map<String,Object> acesso(long id) {
        var rows=db.queryForList("SELECT * FROM auth_suporte_acesso WHERE id_acesso=? AND id_clinica=?",id,TenantContext.id());
        if(rows.isEmpty()) throw missing();
        return rows.getFirst();
    }
    private void ensure(long id) { if(Boolean.FALSE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM suporte_chamado WHERE id_chamado=?)",Boolean.class,id))) throw missing(); }
    private void event(long id,int actor,String type,Map<String,Object> details) { db.update("INSERT INTO suporte_chamado_evento(id_chamado,id_autor,tipo,detalhes) VALUES (?,?,?,?::jsonb)",id,actor,type,encode(details)); }
    private void grantEvent(long id,int actor,String type,Map<String,Object> details) { db.update("INSERT INTO auth_suporte_acesso_evento(id_acesso,id_clinica,id_autor,tipo,detalhes) VALUES (?,?,?,?,?::jsonb)",id,TenantContext.id(),actor,type,encode(details)); }
    private String encode(Object value) { try { return json.writeValueAsString(value); } catch(JacksonException e) { throw new IllegalStateException(e); } }
    private void validate(String title,String description,String category,String priority) {
        if(title==null || title.isBlank() || title.length()>160 || description==null || description.isBlank() || description.length()>3000) bad("Título ou descrição inválidos.");
        if(!CATEGORIAS.contains(category) || !PRIORIDADES.contains(priority)) bad("Categoria ou prioridade inválida.");
    }
    private static void bad(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
    private static ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND,"Registro de suporte não encontrado."); }
}
