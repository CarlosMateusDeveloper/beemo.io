package br.com.clinica.service;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Painel interno: somente metadados agregados, nunca prontuários ou conversas. */
@Service
public class SaasAdminService {
    private final JdbcTemplate db;
    public SaasAdminService(JdbcTemplate db) { this.db=db; }

    public record Staff(long id,String nome,String email,String papel,Set<String> permissoes) {}
    public Staff staff(int user) {
        var rows=db.queryForList("""
            SELECT s.id_funcionario,u.nome,u.email,s.papel FROM auth_saas_funcionario s
            JOIN usuario u ON u.id=s.id_usuario WHERE s.id_usuario=? AND s.ativo
            """,user);
        if(rows.isEmpty()) return null;
        var row=rows.getFirst(); String role=(String)row.get("papel");
        return new Staff(((Number)row.get("id_funcionario")).longValue(),(String)row.get("nome"),
                (String)row.get("email"),role,permissions(role));
    }
    public Set<String> permissions(String role) {
        return switch(role) {
            case "operador" -> Set.of("saas.dashboard.visualizar","saas.acao.gerenciar");
            case "gestor" -> Set.of("saas.dashboard.visualizar","saas.acao.gerenciar","saas.clinica.gerenciar");
            case "administrador" -> Set.of("saas.dashboard.visualizar","saas.acao.gerenciar","saas.clinica.gerenciar","saas.funcionario.gerenciar");
            default -> Set.of();
        };
    }
    public boolean isFresh(int user,String sessionId) {
        return Boolean.TRUE.equals(db.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM auth_saas_funcionario f JOIN auth_session s ON s.id_usuario=f.id_usuario
            WHERE f.id_usuario=? AND f.ativo AND s.id=? AND s.revogada_em IS NULL AND s.expira_em>now()
              AND (NOT f.exige_reautenticacao OR s.autenticado_em>now()-interval '8 hours'))
            """,Boolean.class,user,sessionId));
    }
    public Map<String,Object> me(int user) {
        Staff value=require(user,"saas.dashboard.visualizar");
        return Map.of("nome",value.nome(),"email",value.email(),"papel",value.papel(),"permissoes",value.permissoes());
    }
    public Map<String,Object> dashboard(int user,String search,String risk,int page,int size) {
        require(user,"saas.dashboard.visualizar");
        int safePage=Math.max(0,page), safeSize=Math.min(100,Math.max(1,size));
        String match=search==null?"":search.strip();
        var rows=db.queryForList("""
            SELECT c.id_clinica,c.nome,i.onboarding_status,i.onboarding_atualizado_em,i.integracao_estado,
              i.integracao_falha_em,i.chamados_abertos,i.atualizado_em indicador_atualizado_em,
              owner.nome responsavel_interno,activity.ultimo_uso,activity.acoes_30d
            FROM clinica c
            LEFT JOIN auth_saas_clinica_indicador i ON i.id_clinica=c.id_clinica
            LEFT JOIN auth_saas_clinica_responsavel assigned ON assigned.id_clinica=c.id_clinica
            LEFT JOIN auth_saas_funcionario sf ON sf.id_funcionario=assigned.id_funcionario
            LEFT JOIN usuario owner ON owner.id=sf.id_usuario
            LEFT JOIN LATERAL (
              SELECT max(criado_em) ultimo_uso,count(*) FILTER(WHERE criado_em>now()-interval '30 days') acoes_30d
              FROM auth_audit_log a WHERE a.id_clinica=c.id_clinica
            ) activity ON true
            WHERE (?='' OR lower(c.nome) LIKE lower('%' || ? || '%'))
            ORDER BY c.nome,c.id_clinica LIMIT 1000
            """,match,match);
        var filtered=rows.stream().map(this::present).filter(item -> risk==null || risk.isBlank() || !((List<?>)item.get("riscos")).isEmpty()).toList();
        long total=filtered.size(); int start=Math.min(filtered.size(),safePage*safeSize), end=Math.min(filtered.size(),start+safeSize);
        var items=filtered.subList(start,end);
        return Map.of("itens",items,"pagina",safePage,"tamanho",safeSize,"total",total,
                "fontes",Map.of("uso","auth_audit_log agregado","onboarding","projeção de onboarding","financeiro","indisponível até as issues #31 e #32"));
    }
    private Map<String,Object> present(Map<String,Object> row) {
        var risks=new java.util.ArrayList<Map<String,Object>>();
        String onboarding=(String)row.get("onboarding_status");
        Object last=row.get("ultimo_uso"); Object integration=row.get("integracao_estado");
        if(onboarding==null) risks.add(risk("onboarding_sem_dados","Configuração inicial ainda não registrou dados.","onboarding"));
        else if(!"CONCLUIDO".equals(onboarding)) risks.add(risk("onboarding_parado","Implantação ainda não foi concluída.","onboarding"));
        if(last==null) risks.add(risk("uso_sem_atividade","Não há atividade auditada para esta clínica.","uso"));
        else if(last instanceof Timestamp time && time.toInstant().isBefore(java.time.Instant.now().minus(java.time.Duration.ofDays(7))))
            risks.add(risk("queda_de_uso","A última atividade auditada ocorreu há mais de sete dias.","uso"));
        if("COM_PENDENCIAS".equals(integration) || row.get("integracao_falha_em")!=null)
            risks.add(risk("falha_integracao","A integração informou pendência ou falha recente.","integração"));
        Number tickets=(Number)row.get("chamados_abertos");
        if(tickets!=null && tickets.intValue()>0) risks.add(risk("suporte_aberto",tickets+" chamado(s) de suporte em aberto.","suporte"));
        var result=new LinkedHashMap<String,Object>(row);
        result.put("riscos",risks); result.put("plano",null); result.put("situacaoCobranca",null);
        return result;
    }
    private Map<String,Object> risk(String code,String reason,String origin) { return Map.of("codigo",code,"motivo",reason,"origem",origin); }

    public List<Map<String,Object>> actions(int user,Integer clinic) {
        require(user,"saas.dashboard.visualizar");
        return db.queryForList("""
            SELECT a.*,c.nome clinica,u.nome responsavel FROM auth_saas_acao a JOIN clinica c ON c.id_clinica=a.id_clinica
            LEFT JOIN auth_saas_funcionario f ON f.id_funcionario=a.id_responsavel LEFT JOIN usuario u ON u.id=f.id_usuario
            WHERE (? IS NULL OR a.id_clinica=?) ORDER BY CASE a.status WHEN 'ABERTA' THEN 0 ELSE 1 END,a.vence_em NULLS LAST,a.criado_em DESC
            """,clinic,clinic);
    }
    @Transactional public Map<String,Object> createAction(int user,int clinic,Long responsible,String title,String description,String type,OffsetDateTime due) {
        Staff staff=require(user,"saas.acao.gerenciar");
        if(title==null||title.isBlank()||title.length()>180) throw bad("Informe um título de até 180 caracteres.");
        if(!Set.of("onboarding","adocao","suporte","integracao","comercial","outro").contains(type)) throw bad("Tipo de ação inválido.");
        if(Boolean.FALSE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM clinica WHERE id_clinica=?)",Boolean.class,clinic))) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Clínica não encontrada.");
        Long id=db.queryForObject("""
            INSERT INTO auth_saas_acao(id_clinica,id_responsavel,titulo,descricao,tipo,vence_em,criado_por)
            VALUES (?,?,?,?,?,?,?) RETURNING id_acao
            """,Long.class,clinic,responsible,title.strip(),description==null?null:description.strip(),type,due,staff.id());
        audit(staff,"saas.acao.criar","/api/saas/acoes/"+id,201,null);
        return db.queryForMap("SELECT * FROM auth_saas_acao WHERE id_acao=?",id);
    }
    @Transactional public void completeAction(int user,long action) {
        Staff staff=require(user,"saas.acao.gerenciar");
        if(db.update("UPDATE auth_saas_acao SET status='CONCLUIDA',concluida_em=now(),atualizado_em=now() WHERE id_acao=? AND status='ABERTA'",action)!=1)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Ação não está disponível para conclusão.");
        audit(staff,"saas.acao.concluir","/api/saas/acoes/"+action,204,null);
    }
    @Transactional public void assign(int user,int clinic,long staffId) {
        Staff staff=require(user,"saas.clinica.gerenciar");
        db.update("INSERT INTO auth_saas_clinica_responsavel(id_clinica,id_funcionario) VALUES (?,?) ON CONFLICT(id_clinica) DO UPDATE SET id_funcionario=EXCLUDED.id_funcionario,atualizado_em=now()",clinic,staffId);
        audit(staff,"saas.clinica.atribuir","/api/saas/clinicas/"+clinic,204,null);
    }
    public List<Map<String,Object>> staffList(int user) {
        require(user,"saas.funcionario.gerenciar");
        return db.queryForList("SELECT f.id_funcionario,u.nome,u.email,f.papel,f.ativo,f.exige_reautenticacao,f.criado_em FROM auth_saas_funcionario f JOIN usuario u ON u.id=f.id_usuario ORDER BY u.nome");
    }
    @Transactional public Map<String,Object> saveStaff(int user,String email,String role,boolean active,boolean reauth) {
        Staff actor=require(user,"saas.funcionario.gerenciar");
        if(!Set.of("operador","gestor","administrador").contains(role)) throw bad("Papel interno inválido.");
        var users=db.queryForList("SELECT id FROM usuario WHERE lower(email)=lower(?)",Integer.class,email.strip());
        if(users.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"A pessoa deve entrar uma vez no Clinisis antes de receber acesso interno.");
        int target=users.getFirst();
        db.update("INSERT INTO auth_saas_funcionario(id_usuario,papel,ativo,exige_reautenticacao) VALUES (?,?,?,?) ON CONFLICT(id_usuario) DO UPDATE SET papel=EXCLUDED.papel,ativo=EXCLUDED.ativo,exige_reautenticacao=EXCLUDED.exige_reautenticacao,atualizado_em=now()",target,role,active,reauth);
        audit(actor,"saas.funcionario.gerenciar","/api/saas/funcionarios/"+target,204,null);
        return db.queryForMap("SELECT f.id_funcionario,u.nome,u.email,f.papel,f.ativo,f.exige_reautenticacao FROM auth_saas_funcionario f JOIN usuario u ON u.id=f.id_usuario WHERE u.id=?",target);
    }
    private Staff require(int user,String permission) { Staff staff=staff(user); if(staff==null||!staff.permissoes().contains(permission)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Seu acesso interno não permite esta ação."); return staff; }
    private void audit(Staff staff,String action,String resource,int result,String address) { db.update("INSERT INTO auth_saas_audit_log(id_funcionario,acao,recurso,resultado,endereco_ip) VALUES (?,?,?,?,?)",staff.id(),action,resource,result,address); }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
}
