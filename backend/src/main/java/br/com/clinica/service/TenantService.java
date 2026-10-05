package br.com.clinica.service;

import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;

@Service
public class TenantService {
    public record Access(Integer id,String nome,String perfil) {}
    private final JdbcTemplate db;
    @Value("${app.tenant.default-id:1}") private int defaultTenant;
    public TenantService(JdbcTemplate db) { this.db=db; }
    public List<Access> listar(int user) {
        return db.query("SELECT c.id_clinica,c.nome,m.perfil FROM tenant_membro m JOIN clinica c USING(id_clinica) WHERE m.id_usuario=? AND m.ativo ORDER BY c.nome,c.id_clinica",
            (rs,n)->new Access(rs.getInt(1),rs.getString(2),rs.getString(3)),user);
    }
    public Access unico(int user) {
        var acessos=listar(user);
        if(acessos.size()>1) throw new IllegalStateException("Uma conta não pode pertencer a mais de uma clínica ativa.");
        return acessos.isEmpty()?null:acessos.getFirst();
    }
    @Transactional
    public Access resolver(int user) {
        db.queryForObject("SELECT pg_advisory_xact_lock(?)",Object.class,(long)user);
        var existente=unico(user);
        if(existente!=null && existente.id()==defaultTenant) return existente;
        var padrao=db.queryForList("SELECT nome FROM clinica WHERE id_clinica=?",String.class,defaultTenant);
        if(!padrao.isEmpty()) {
            if(existente!=null && !existente.nome().equals("ClinicOS #"+user)) return existente;
            if(existente!=null) db.update("UPDATE tenant_membro SET ativo=false WHERE id_usuario=? AND id_clinica=?",user,existente.id());
            db.update("INSERT INTO tenant_membro(id_clinica,id_usuario,perfil) VALUES (?,?,'administrador') ON CONFLICT(id_clinica,id_usuario) DO UPDATE SET perfil='administrador',ativo=true",defaultTenant,user);
            return new Access(defaultTenant,padrao.getFirst(),"administrador");
        }
        String nome="ClinicOS #"+user;
        int tenant=db.queryForObject("INSERT INTO clinica(nome) VALUES (?) RETURNING id_clinica",Integer.class,nome);
        db.update("INSERT INTO tenant_membro(id_clinica,id_usuario,perfil) VALUES (?,?,'administrador')",tenant,user);
        return new Access(tenant,nome,"administrador");
    }
    public Access atual(String sessionId,int user) {
        var rows=db.query("SELECT c.id_clinica,c.nome,m.perfil FROM auth_session s JOIN tenant_membro m ON m.id_clinica=s.id_clinica AND m.id_usuario=s.id_usuario JOIN clinica c ON c.id_clinica=m.id_clinica WHERE s.id=? AND s.id_usuario=? AND m.ativo AND s.revogada_em IS NULL AND s.expira_em>now()",
            (rs,n)->new Access(rs.getInt(1),rs.getString(2),rs.getString(3)),sessionId,user);
        return rows.isEmpty()?null:rows.getFirst();
    }
    public Access acesso(int user,int tenant) {
        return listar(user).stream().filter(a->a.id()==tenant).findFirst().orElseThrow(TenantService::denied);
    }
    @Transactional
    public void vincularSessao(String sessionId,int user,int tenant) {
        acesso(user,tenant);
        if(db.update("UPDATE auth_session SET id_clinica=? WHERE id=? AND id_usuario=? AND revogada_em IS NULL AND expira_em>now()",tenant,sessionId,user)!=1) throw denied();
    }
    public List<Map<String,Object>> membros(int user,int tenant) {
        admin(user,tenant);
        return db.queryForList("SELECT u.id,u.nome,u.email,m.perfil,m.ativo FROM tenant_membro m JOIN usuario u ON u.id=m.id_usuario WHERE m.id_clinica=? ORDER BY u.nome",tenant);
    }
    @Transactional
    public String convidar(int user,int tenant,String email,String perfil) {
        admin(user,tenant);perfil(perfil);
        email=email.strip().toLowerCase(Locale.ROOT);
        byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        db.update("INSERT INTO tenant_convite(hash,id_clinica,email,perfil,expira_em,criado_por) VALUES (?,?,?,?,now()+interval '7 days',?)",
            MagicLinkService.hash(token),tenant,email,perfil,user);
        // Autorizar um endereco comprovado nunca promove o usuario fora desta clinica.
        var ids=db.queryForList("SELECT id_usuario FROM auth_email_identity WHERE email=?",Integer.class,email);
        if(!ids.isEmpty()) adicionar(tenant,ids.getFirst(),perfil);
        return token;
    }
    @Transactional
    public Access aceitar(int user,String token) {
        if(token==null || !token.matches("[A-Za-z0-9_-]{43}")) throw denied();
        var rows=db.queryForList("UPDATE tenant_convite SET usado_em=now() WHERE hash=? AND usado_em IS NULL AND expira_em>now() RETURNING id_clinica,email,perfil",MagicLinkService.hash(token));
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.GONE,"Convite inválido, expirado ou já utilizado.");
        var invite=rows.getFirst();
        String email=db.queryForObject("SELECT email FROM usuario WHERE id=?",String.class,user);
        if(email==null || !email.equalsIgnoreCase((String)invite.get("email"))) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Entre com a identidade que recebeu este convite.");
        int tenant=((Number)invite.get("id_clinica")).intValue();
        adicionar(tenant,user,(String)invite.get("perfil"));
        return acesso(user,tenant);
    }
    private void adicionar(int tenant,int user,String perfil) {
        if(Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM tenant_membro WHERE id_usuario=? AND id_clinica<>? AND ativo)",Boolean.class,user,tenant)))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Esta conta já está vinculada a outra clínica.");
        db.update("INSERT INTO tenant_membro(id_clinica,id_usuario,perfil) VALUES (?,?,?) ON CONFLICT(id_clinica,id_usuario) DO UPDATE SET perfil=CASE WHEN tenant_membro.ativo THEN tenant_membro.perfil ELSE excluded.perfil END,ativo=true",tenant,user,perfil);
    }
    @Transactional
    public void revogar(int actor,int tenant,int user) {
        admin(actor,tenant);
        db.queryForObject("SELECT id_clinica FROM clinica WHERE id_clinica=? FOR UPDATE",Integer.class,tenant);
        if(Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM tenant_membro WHERE id_clinica=? AND id_usuario=? AND perfil='administrador' AND ativo)",Boolean.class,tenant,user))
            && db.queryForObject("SELECT count(*) FROM tenant_membro WHERE id_clinica=? AND ativo AND perfil='administrador'",Integer.class,tenant)<=1)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Mantenha ao menos um administrador na clínica.");
        db.update("UPDATE tenant_membro SET ativo=false WHERE id_clinica=? AND id_usuario=?",tenant,user);
        db.update("UPDATE tenant_convite SET expira_em=now() WHERE id_clinica=? AND email IN (SELECT email FROM usuario WHERE id=?) AND usado_em IS NULL",tenant,user);
    }
    private void admin(int user,int tenant) { if(!"administrador".equals(acesso(user,tenant).perfil())) throw denied(); }
    private static void perfil(String perfil) { if(!List.of("administrador","medico").contains(perfil)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Perfil inválido."); }
    private static ResponseStatusException denied() { return new ResponseStatusException(HttpStatus.FORBIDDEN,"Você não tem autorização para esta clínica."); }
}
