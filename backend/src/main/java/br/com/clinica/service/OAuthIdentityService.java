package br.com.clinica.service;

import br.com.clinica.model.Usuario;
import br.com.clinica.repository.UsuarioRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OAuthIdentityService {
    private final JdbcTemplate db;
    private final UsuarioRepository usuarios;
    public OAuthIdentityService(JdbcTemplate db,UsuarioRepository usuarios) { this.db=db;this.usuarios=usuarios; }
    public Usuario localizar(String issuer,String subject) {
        var ids=db.queryForList("SELECT id_usuario FROM auth_oauth_identity WHERE issuer=? AND subject=?",Integer.class,issuer,subject);
        if(ids.isEmpty()) throw new OAuth2AuthenticationException("oauth_unlinked");
        return usuarios.findById(ids.getFirst()).orElseThrow(()->new OAuth2AuthenticationException("oauth_unlinked"));
    }
    @Transactional
    public void vincular(Integer id,String provider,String issuer,String subject) {
        if(!usuarios.existsById(id)) throw new OAuth2AuthenticationException("oauth_unlinked");
        Integer dono=db.queryForObject("""
            INSERT INTO auth_oauth_identity(id,id_usuario,provider,issuer,subject) VALUES (?,?,?,?,?)
            ON CONFLICT(issuer,subject) DO UPDATE SET id_usuario=auth_oauth_identity.id_usuario
            RETURNING id_usuario
            """,Integer.class,UUID.randomUUID().toString(),id,provider,issuer,subject);
        if(!id.equals(dono)) throw new OAuth2AuthenticationException("oauth_link_failed");
    }
    public List<Map<String,Object>> listar(Integer id) {
        return db.queryForList("SELECT provider FROM auth_oauth_identity WHERE id_usuario=?",id);
    }
}
