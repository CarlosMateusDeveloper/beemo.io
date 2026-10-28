package br.com.clinica.service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.time.OffsetDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TenantService {
    private record BaseAccess(int tenant, String nome, String perfil, Integer medico) {}
    public record Access(Integer id, String nome, String perfil, List<String> papeis,
                         Set<String> permissoes, Integer idMedico, boolean suporteTemporario,
                         Long suporteAcessoId, OffsetDateTime expiraEm) {
        /** Mantém compatibilidade com testes e consumidores da versão anterior. */
        public Access(Integer id, String nome, String perfil) {
            this(id, nome, perfil, List.of(perfil), PermissionCatalog.defaultsFor(Set.of(perfil)), null,
                    false,null,null);
        }
        public Access(Integer id,String nome,String perfil,List<String> papeis,Set<String> permissoes,Integer idMedico) {
            this(id,nome,perfil,papeis,permissoes,idMedico,false,null,null);
        }
    }

    public record MemberUpdate(List<String> papeis, Integer idMedico,
                               Set<String> permitir, Set<String> negar) {}

    private final JdbcTemplate db;
    public TenantService(JdbcTemplate db) { this.db = db; }

    public List<Access> listar(int user) {
        var result = new ArrayList<>(listarMembros(user));
        result.addAll(listarSuporte(user));
        return List.copyOf(result);
    }

    private List<Access> listarMembros(int user) {
        var rows = db.query("SELECT c.id_clinica,c.nome,m.perfil,m.id_medico FROM tenant_membro m "
                        + "JOIN clinica c USING(id_clinica) WHERE m.id_usuario=? AND m.ativo ORDER BY c.nome,c.id_clinica",
                (rs, n) -> new BaseAccess(rs.getInt(1), rs.getString(2), rs.getString(3),
                        (Integer) rs.getObject(4)), user);
        return rows.stream().map(row -> access(row.tenant(), row.nome(), row.perfil(), row.medico(), user)).toList();
    }

    private List<Access> listarSuporte(int user) {
        return db.queryForList("""
                SELECT a.id_acesso,c.id_clinica,c.nome,a.expira_em
                FROM auth_suporte_acesso a JOIN clinica c ON c.id_clinica=a.id_clinica
                WHERE a.id_agente=? AND a.status='APROVADO' AND a.revogado_em IS NULL AND a.expira_em>now()
                ORDER BY a.expira_em,c.nome
                """, user).stream().map(row -> supportAccess(
                        ((Number)row.get("id_acesso")).longValue(),
                        ((Number)row.get("id_clinica")).intValue(),
                        (String)row.get("nome"),
                toOffsetDateTime(row.get("expira_em")))).toList();
    }

    private Access access(int tenant, String nome, String legacyRole, Integer doctor, int user) {
        List<String> roles = db.queryForList(
                "SELECT papel FROM tenant_membro_papel WHERE id_clinica=? AND id_usuario=? ORDER BY papel",
                String.class, tenant, user);
        if (roles.isEmpty() && legacyRole != null) roles = List.of(legacyRole);
        Set<String> permissions = PermissionCatalog.defaultsFor(new LinkedHashSet<>(roles));
        db.query("SELECT permissao,permitido FROM tenant_membro_permissao WHERE id_clinica=? AND id_usuario=?",
                rs -> {
                    String permission = rs.getString(1);
                    if (rs.getBoolean(2)) permissions.add(permission); else permissions.remove(permission);
                }, tenant, user);
        String primary = roles.contains(PermissionCatalog.ADMINISTRADOR)
                ? PermissionCatalog.ADMINISTRADOR : roles.isEmpty() ? legacyRole : roles.getFirst();
        return new Access(tenant, nome, primary, List.copyOf(roles), Set.copyOf(permissions), doctor);
    }

    public Access unico(int user) {
        var accesses = listarMembros(user);
        if (accesses.size() > 1) throw new IllegalStateException("Uma conta não pode pertencer a mais de uma clínica ativa.");
        return accesses.isEmpty() ? null : accesses.getFirst();
    }

    public Access resolver(int user) {
        // Autenticar uma identidade nunca autoriza acesso a uma clínica.
        // O vínculo nasce exclusivamente de convite/provisionamento administrativo.
        Access member = unico(user);
        if (member != null) return member;
        var support = listarSuporte(user);
        return support.size() == 1 ? support.getFirst() : null;
    }

    public Access atual(String sessionId, int user) {
        var rows = db.query("SELECT c.id_clinica,c.nome,m.perfil,m.id_medico FROM auth_session s "
                        + "JOIN tenant_membro m ON m.id_clinica=s.id_clinica AND m.id_usuario=s.id_usuario "
                        + "JOIN clinica c ON c.id_clinica=m.id_clinica "
                        + "WHERE s.id=? AND s.id_usuario=? AND m.ativo AND s.revogada_em IS NULL AND s.expira_em>now()",
                (rs, n) -> new BaseAccess(rs.getInt(1), rs.getString(2), rs.getString(3),
                        (Integer) rs.getObject(4)), sessionId, user);
        if (!rows.isEmpty()) {
            var row = rows.getFirst();
            return access(row.tenant(), row.nome(), row.perfil(), row.medico(), user);
        }
        var support = db.queryForList("""
                SELECT a.id_acesso,c.id_clinica,c.nome,a.expira_em
                FROM auth_session s
                JOIN auth_suporte_acesso a ON a.id_clinica=s.id_clinica AND a.id_agente=s.id_usuario
                JOIN clinica c ON c.id_clinica=a.id_clinica
                WHERE s.id=? AND s.id_usuario=? AND s.revogada_em IS NULL AND s.expira_em>now()
                  AND a.status='APROVADO' AND a.revogado_em IS NULL AND a.expira_em>now()
                """, sessionId, user);
        if (support.isEmpty()) return null;
        var selected=support.getFirst();
        return supportAccess(((Number)selected.get("id_acesso")).longValue(),
                ((Number)selected.get("id_clinica")).intValue(),(String)selected.get("nome"),
                toOffsetDateTime(selected.get("expira_em")));
    }

    private static OffsetDateTime toOffsetDateTime(Object value) {
        if (value instanceof OffsetDateTime date) return date;
        if (value instanceof java.sql.Timestamp timestamp)
            return timestamp.toInstant().atOffset(java.time.ZoneOffset.UTC);
        throw new IllegalStateException("Data de expiração inválida.");
    }

    private Access supportAccess(long accessId,int tenant,String name,OffsetDateTime expires) {
        Set<String> scopes = new LinkedHashSet<>(db.queryForList(
                "SELECT jsonb_array_elements_text(escopos) FROM auth_suporte_acesso WHERE id_acesso=?",
                String.class,accessId));
        return new Access(tenant,name,"suporte",List.of("suporte"),SupportAccessPolicy.permissions(scopes),
                null,true,accessId,expires);
    }

    public Access acesso(int user, int tenant) {
        return listar(user).stream().filter(a -> a.id() == tenant).findFirst().orElseThrow(TenantService::denied);
    }

    @Transactional
    public void vincularSessao(String sessionId, int user, int tenant) {
        acesso(user, tenant);
        if (db.update("UPDATE auth_session SET id_clinica=? WHERE id=? AND id_usuario=? AND revogada_em IS NULL AND expira_em>now()",
                tenant, sessionId, user) != 1) throw denied();
    }

    public List<Map<String, Object>> membros(int user, int tenant) {
        require(user, tenant, "usuario.gerenciar");
        var rows = db.queryForList("SELECT u.id,u.nome,u.email,m.perfil,m.ativo,m.id_medico FROM tenant_membro m "
                + "JOIN usuario u ON u.id=m.id_usuario WHERE m.id_clinica=? ORDER BY u.nome", tenant);
        List<Map<String, Object>> result = new ArrayList<>();
        for (var row : rows) {
            int member = ((Number) row.get("id")).intValue();
            Access access = access(tenant, "", (String) row.get("perfil"),
                    row.get("id_medico") == null ? null : ((Number) row.get("id_medico")).intValue(), member);
            var item = new LinkedHashMap<String, Object>(row);
            item.put("papeis", access.papeis());
            item.put("permissoes", access.permissoes());
            result.add(item);
        }
        return result;
    }

    @Transactional
    public String convidar(int user, int tenant, String email, String role) {
        require(user, tenant, "usuario.gerenciar");
        validateRole(role);
        email = email.strip().toLowerCase(Locale.ROOT);
        byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        db.update("INSERT INTO tenant_convite(hash,id_clinica,email,perfil,expira_em,criado_por) "
                        + "VALUES (?,?,?,?,now()+interval '7 days',?)",
                MagicLinkService.hash(token), tenant, email, role, user);
        var ids = db.queryForList("SELECT id_usuario FROM auth_email_identity WHERE email=?", Integer.class, email);
        if (!ids.isEmpty()) adicionar(tenant, ids.getFirst(), role);
        return token;
    }

    @Transactional
    public Access aceitar(int user, String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw denied();
        var rows = db.queryForList("UPDATE tenant_convite SET usado_em=now() WHERE hash=? AND usado_em IS NULL "
                        + "AND expira_em>now() RETURNING id_clinica,email,perfil", MagicLinkService.hash(token));
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.GONE, "Convite inválido, expirado ou já utilizado.");
        var invite = rows.getFirst();
        String email = db.queryForObject("SELECT email FROM usuario WHERE id=?", String.class, user);
        if (email == null || !email.equalsIgnoreCase((String) invite.get("email")))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Entre com a identidade que recebeu este convite.");
        int tenant = ((Number) invite.get("id_clinica")).intValue();
        adicionar(tenant, user, (String) invite.get("perfil"));
        return acesso(user, tenant);
    }

    private void adicionar(int tenant, int user, String role) {
        validateRole(role);
        if (Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM tenant_membro WHERE id_usuario=? AND id_clinica<>? AND ativo)",
                Boolean.class, user, tenant)))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta conta já está vinculada a outra clínica.");
        db.update("INSERT INTO tenant_membro(id_clinica,id_usuario,perfil) VALUES (?,?,?) "
                        + "ON CONFLICT(id_clinica,id_usuario) DO UPDATE SET ativo=true", tenant, user, role);
        db.update("INSERT INTO tenant_membro_papel(id_clinica,id_usuario,papel) VALUES (?,?,?) ON CONFLICT DO NOTHING",
                tenant, user, role);
    }

    @Transactional
    public Access atualizarMembro(int actor, int tenant, int member, MemberUpdate update) {
        require(actor, tenant, "permissao.gerenciar");
        Access current = acesso(member, tenant);
        List<String> roles = update.papeis() == null ? current.papeis()
                : update.papeis().stream().distinct().toList();
        if (roles.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione ao menos um papel.");
        roles.forEach(TenantService::validateRole);
        Integer doctor = roles.contains(PermissionCatalog.MEDICO)
                ? update.idMedico() != null ? update.idMedico() : current.idMedico()
                : null;
        if (roles.contains(PermissionCatalog.MEDICO) && doctor == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Vincule o usuário médico ao respectivo profissional da clínica.");
        boolean removingLastAdmin = current.papeis().contains(PermissionCatalog.ADMINISTRADOR)
                && !roles.contains(PermissionCatalog.ADMINISTRADOR) && countAdmins(tenant) <= 1;
        if (removingLastAdmin) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Mantenha ao menos um administrador na clínica.");
        db.update("DELETE FROM tenant_membro_papel WHERE id_clinica=? AND id_usuario=?", tenant, member);
        roles.forEach(role -> db.update("INSERT INTO tenant_membro_papel(id_clinica,id_usuario,papel) VALUES (?,?,?)",
                tenant, member, role));
        String primary = roles.contains(PermissionCatalog.ADMINISTRADOR) ? PermissionCatalog.ADMINISTRADOR : roles.getFirst();
        db.update("UPDATE tenant_membro SET perfil=?,id_medico=? WHERE id_clinica=? AND id_usuario=?",
                primary, doctor, tenant, member);
        if (update.permitir() != null || update.negar() != null) {
            db.update("DELETE FROM tenant_membro_permissao WHERE id_clinica=? AND id_usuario=?", tenant, member);
            saveOverrides(tenant, member, update.permitir(), true);
            saveOverrides(tenant, member, update.negar(), false);
        }
        return acesso(member, tenant);
    }

    private void saveOverrides(int tenant, int member, Set<String> permissions, boolean allowed) {
        if (permissions == null) return;
        for (String permission : permissions) {
            if (!PermissionCatalog.validPermission(permission))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Permissão inválida: " + permission);
            db.update("INSERT INTO tenant_membro_permissao(id_clinica,id_usuario,permissao,permitido) VALUES (?,?,?,?)",
                    tenant, member, permission, allowed);
        }
    }

    @Transactional
    public void revogar(int actor, int tenant, int user) {
        require(actor, tenant, "usuario.gerenciar");
        db.queryForObject("SELECT id_clinica FROM clinica WHERE id_clinica=? FOR UPDATE", Integer.class, tenant);
        if (acesso(user, tenant).papeis().contains(PermissionCatalog.ADMINISTRADOR) && countAdmins(tenant) <= 1)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mantenha ao menos um administrador na clínica.");
        db.update("UPDATE tenant_membro SET ativo=false WHERE id_clinica=? AND id_usuario=?", tenant, user);
        db.update("UPDATE tenant_convite SET expira_em=now() WHERE id_clinica=? "
                + "AND email IN (SELECT email FROM usuario WHERE id=?) AND usado_em IS NULL", tenant, user);
    }

    public Map<String, Object> accessModel(int user, int tenant) {
        require(user, tenant, "permissao.gerenciar");
        return Map.of("papeis", PermissionCatalog.roles(), "permissoes", PermissionCatalog.permissions(),
                "padroes", PermissionCatalog.matrix());
    }

    private int countAdmins(int tenant) {
        return db.queryForObject("SELECT count(*) FROM tenant_membro_papel p JOIN tenant_membro m "
                + "USING(id_clinica,id_usuario) WHERE p.id_clinica=? AND p.papel='administrador' AND m.ativo",
                Integer.class, tenant);
    }

    private void require(int user, int tenant, String permission) {
        if (!acesso(user, tenant).permissoes().contains(permission)) throw denied();
    }

    private static void validateRole(String role) {
        if (!PermissionCatalog.validRole(role))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Papel inválido.");
    }
    private static ResponseStatusException denied() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "Você não tem autorização para esta clínica.");
    }
}
