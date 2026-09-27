package br.com.clinica.service;

import br.com.clinica.model.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Service
public class JwtService {

    private final SecretKey chave;
    private final Duration expiracao;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-minutes}") long expiracaoMinutos
    ) {
        this.chave = secret.isBlank() ? Jwts.SIG.HS256.key().build() : Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiracao = Duration.ofMinutes(expiracaoMinutos);
    }

    public String gerar(Usuario usuario) {
        Instant agora = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(usuario.getId()))
                .id(java.util.UUID.randomUUID().toString())
                .issuer("clinicos")
                .audience().add("clinicos-api").and()
                .issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plus(expiracao)))
                .signWith(chave)
                .compact();
    }

    // vazio = token ausente, expirado ou inválido — quem chama trata como não autenticado.
    public Optional<Claims> validar(String token) {
        if(token==null || token.length()>4096) return Optional.empty();
        try {
            Claims claims = Jwts.parser().verifyWith(chave).requireIssuer("clinicos").build().parseSignedClaims(token).getPayload();
            if(claims.getExpiration()==null || claims.getIssuedAt()==null || claims.getId()==null ||
                claims.getSubject()==null || claims.getAudience()==null || !claims.getAudience().contains("clinicos-api") ||
                claims.getIssuedAt().toInstant().isAfter(Instant.now().plusSeconds(30))) return Optional.empty();
            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
