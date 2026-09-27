package br.com.clinica.service;

import br.com.clinica.dto.LoginResponse;
import br.com.clinica.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.net.URI;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class MagicLinkService {
    private final JdbcTemplate db;
    private final UsuarioRepository usuarios;
    private final ResendEmailService email;
    private final SessionService jwt;
    private final AuthService auth;
    private final String frontend;
    private final SecureRandom random=new SecureRandom();
    public MagicLinkService(JdbcTemplate db, UsuarioRepository usuarios, ResendEmailService email,
            SessionService jwt, AuthService auth, @Value("${app.auth.frontend-url:http://localhost:5173}") String frontend) {
        this.db=db; this.usuarios=usuarios; this.email=email; this.jwt=jwt; this.auth=auth;
        URI uri=URI.create(frontend);
        boolean local="http".equals(uri.getScheme()) && ("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost()));
        if ((!"https".equals(uri.getScheme()) && !local) || uri.getHost()==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null)
            throw new IllegalArgumentException("APP_FRONTEND_URL deve ser uma URL HTTPS (HTTP permitido apenas em localhost)");
        this.frontend=frontend.replaceAll("/+$", "");
    }
    @Transactional
    public void solicitar(String endereco) {
        email.verificarConfiguracao();
        var usuario=usuarios.findByEmailIgnoreCase(endereco.trim());
        if (usuario.isEmpty()) return;
        byte[] bytes=new byte[32]; random.nextBytes(bytes);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        db.update("DELETE FROM auth_magic_token WHERE expira_em < now() - interval '1 day'");
        db.update("INSERT INTO auth_magic_token(hash,id_usuario,email,expira_em) VALUES (?,?,?,now()+interval '15 minutes')",
            hash(token),usuario.get().getId(),usuario.get().getEmail());
        // Fragmento não é enviado ao servidor web nem em cabeçalhos Referer.
        email.enviar(usuario.get().getEmail(),frontend+"/login/magic#token="+token,UUID.randomUUID().toString());
    }
    @Transactional
    public LoginResponse verificar(String token) {
        if (token==null || !token.matches("[A-Za-z0-9_-]{43}")) throw invalido();
        var rows=db.queryForList("""
            UPDATE auth_magic_token SET usado_em=now()
            WHERE hash=? AND usado_em IS NULL AND expira_em>now()
            RETURNING id_usuario,email
            """,hash(token));
        if (rows.isEmpty()) throw invalido();
        var usuario=usuarios.findById(((Number) rows.getFirst().get("id_usuario")).intValue())
            .filter(u -> u.getEmail().equalsIgnoreCase((String) rows.getFirst().get("email")))
            .orElseThrow(this::invalido);
        return new LoginResponse(jwt.gerar(usuario),auth.paraDto(usuario));
    }
    static String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private ResponseStatusException invalido() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Link inválido, expirado ou já utilizado. Solicite um novo link.");
    }
}
