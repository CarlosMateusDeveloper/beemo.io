package br.com.clinica.service;
import br.com.clinica.model.Usuario;
import io.jsonwebtoken.Claims;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.sql.Timestamp;
import java.util.Optional;

@Service
public class SessionService {
    private final JwtService jwt;
    private final JdbcTemplate db;
    private final TenantService tenants;
    public SessionService(JwtService jwt,JdbcTemplate db,TenantService tenants) { this.jwt=jwt;this.db=db;this.tenants=tenants; }
    @org.springframework.transaction.annotation.Transactional
    public String gerar(Usuario usuario) {
        String token=jwt.gerar(usuario);
        Claims claims=jwt.validar(token).orElseThrow();
        var tenant=tenants.resolver(usuario.getId());
        db.update("DELETE FROM auth_session WHERE expira_em<now()-interval '1 day'");
        db.update("INSERT INTO auth_session(id,id_usuario,id_clinica,expira_em) VALUES (?,?,?,?)",claims.getId(),usuario.getId(),tenant==null?null:tenant.id(),new Timestamp(claims.getExpiration().getTime()));
        return token;
    }
    public Optional<Claims> validar(String token) {
        return jwt.validar(token).filter(c -> Boolean.TRUE.equals(db.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM auth_session WHERE id=? AND id_usuario::text=? AND revogada_em IS NULL AND expira_em>now())",
            Boolean.class,c.getId(),c.getSubject())));
    }
    public void revogar(String token) {
        jwt.validar(token).ifPresent(c -> db.update("UPDATE auth_session SET revogada_em=now() WHERE id=?",c.getId()));
    }
}
