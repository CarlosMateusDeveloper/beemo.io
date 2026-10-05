package br.com.clinica.service;

import br.com.clinica.model.Usuario;
import br.com.clinica.repository.UsuarioRepository;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityAccountService {
    private final JdbcTemplate db;
    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    public IdentityAccountService(JdbcTemplate db,UsuarioRepository usuarios,PasswordEncoder encoder) {
        this.db=db;this.usuarios=usuarios;this.encoder=encoder;
    }
    /** Somente depois de prova de posse: magic link consumido ou e-mail atestado pelo provedor. */
    @Transactional
    public Usuario emailVerificado(String email,String nome) {
        email=email.strip().toLowerCase(Locale.ROOT);
        db.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,"email:"+email);
        var ids=db.queryForList("SELECT id_usuario FROM auth_email_identity WHERE email=?",Integer.class,email);
        if(!ids.isEmpty()) return usuarios.findById(ids.getFirst()).orElseThrow();
        var user=novaIdentidade(email,nome);
        db.update("INSERT INTO auth_email_identity(email,id_usuario) VALUES (?,?)",email,user.getId());
        return user;
    }
    @Transactional
    public Usuario novaIdentidade(String email,String nome) {
        String displayName=nome==null || nome.isBlank()?"Minha conta":nome.strip();
        Integer id=db.queryForObject("INSERT INTO usuario(nome,email,senha,perfil) VALUES (?,?,?,NULL) RETURNING id",Integer.class,
            displayName.substring(0,Math.min(100,displayName.length())),email,encoder.encode(UUID.randomUUID().toString()));
        return usuarios.findById(id).orElseThrow();
    }
}
