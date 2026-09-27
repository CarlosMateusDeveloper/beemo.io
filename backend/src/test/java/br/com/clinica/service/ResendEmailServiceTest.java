package br.com.clinica.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class ResendEmailServiceTest {
    @Test void enviaLinkComIdempotenciaSemChamarProvedorReal() {
        var builder=RestClient.builder().baseUrl("https://api.resend.com");
        var server=MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.resend.com/emails"))
            .andExpect(header("Authorization","Bearer fake-test-key"))
            .andExpect(header("Idempotency-Key","test-send"))
            .andExpect(req -> {
                String body=((MockClientHttpRequest)req).getBodyAsString();
                assertTrue(body.contains("/login/magic#token=test"));
                assertTrue(body.contains("destino@example.invalid"));
            }).andRespond(withSuccess("{}",MediaType.APPLICATION_JSON));
        new ResendEmailService("fake-test-key","clinicOS <teste@example.invalid>",builder.build())
            .enviar("destino@example.invalid","http://localhost:5173/login/magic#token=test","test-send");
        server.verify();
    }
    @Test void erroDoProvedorNaoVazaRespostaNemFingeSucesso() {
        var builder=RestClient.builder().baseUrl("https://api.resend.com");
        var server=MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.resend.com/emails")).andRespond(withStatus(HttpStatus.BAD_GATEWAY).body("detalhe privado"));
        var service=new ResendEmailService("test-key","teste@example.invalid",builder.build());
        var e=assertThrows(ResponseStatusException.class,()->service.enviar("test@example.invalid","link","test"));
        assertEquals(503,e.getStatusCode().value());
        assertFalse(e.getReason().contains("privado"));
        server.verify();
    }
    @Test void semConfiguracaoNaoFingeEnvio() {
        assertEquals(503,assertThrows(ResponseStatusException.class,
            () -> new ResendEmailService("","").verificarConfiguracao()).getStatusCode().value());
    }
}
