package br.com.clinica.service;

import br.com.clinica.model.Usuario;
import br.com.clinica.model.PerfilUsuario;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class AuthSecurityTest {
    @Test void limitaSolicitacoes() {
        var limit=new AuthRateLimiter();
        for (int i=0;i<3;i++) limit.verificar("email:test",3);
        assertEquals(429,assertThrows(ResponseStatusException.class,()->limit.verificar("email:test",3)).getStatusCode().value());
        assertDoesNotThrow(()->limit.verificar("email:outro",3));
    }
    @Test void chaveNaoConfiguradaNaoUsaSegredoPublicoERejeitaExpiracao() {
        var u=new Usuario(); u.setId(1);u.setNome("Teste");u.setEmail("test@example.invalid");u.setPerfil(PerfilUsuario.administrador);
        var primeiro=new JwtService("",10); var outro=new JwtService("",10);
        String token=primeiro.gerar(u);
        assertTrue(primeiro.validar(token).isPresent());
        assertTrue(outro.validar(token).isEmpty());
        assertTrue(new JwtService("",-1).validar(token).isEmpty());
        var expirado=new JwtService("test-secret-32-bytes-long-123456789",-1);
        assertTrue(expirado.validar(expirado.gerar(u)).isEmpty());
    }
}
