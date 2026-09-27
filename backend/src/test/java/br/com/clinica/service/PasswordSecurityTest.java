package br.com.clinica.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PasswordSecurityTest {
    @Test void argon2idComSaltEApenasHashesAceitos() {
        var encoder=new MigratingPasswordEncoder();
        var first=encoder.encode("test-password-only");
        assertTrue(first.startsWith("$argon2id$v=19$m=19456,t=2,p=1$"));
        assertNotEquals(first,encoder.encode("test-password-only"));
        assertTrue(encoder.matches("test-password-only",first));
        assertFalse(encoder.matches("incorrect",first));
        assertFalse(encoder.matches("plain","plain"));
        assertFalse(encoder.matches("plain","{noop}plain"));
        assertFalse(encoder.upgradeEncoding(first));
        var legacy=new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("legacy-test");
        assertTrue(encoder.matches("legacy-test",legacy));
        assertFalse(encoder.matches("incorrect",legacy));
        assertTrue(encoder.upgradeEncoding(legacy));
    }
    @Test void jwtRejeitaEmissorAudienciaAssinaturaAusenteEClaimsIncompletas() {
        String secret="test-only-32-bytes-secret-123456789";
        var service=new JwtService(secret,30);
        var key=io.jsonwebtoken.security.Keys.hmacShaKeyFor(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        for(String issuer:new String[]{"invalid","clinicos"}) {
            var token=io.jsonwebtoken.Jwts.builder().subject("1").id("test")
                .issuer(issuer).audience().add(issuer.equals("invalid")?"clinicos-api":"invalid").and()
                .issuedAt(new java.util.Date()).expiration(new java.util.Date(System.currentTimeMillis()+60000))
                .signWith(key).compact();
            assertTrue(service.validar(token).isEmpty());
        }
        assertTrue(service.validar(io.jsonwebtoken.Jwts.builder().subject("1").issuer("clinicos").signWith(key).compact()).isEmpty());
        assertTrue(service.validar(io.jsonwebtoken.Jwts.builder().subject("1").compact()).isEmpty());
    }
}
