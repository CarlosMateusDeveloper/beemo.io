package br.com.clinica.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;

@Service
public class ResendEmailService {
    private final String key, from;
    private final RestClient client;
    @org.springframework.beans.factory.annotation.Autowired
    public ResendEmailService(@Value("${app.resend.api-key:}") String key,
            @Value("${app.resend.from:}") String from) {
        this(key,from,criarCliente());
    }
    ResendEmailService(String key,String from,RestClient client) {
        this.key=key; this.from=from; this.client=client;
    }
    private static RestClient criarCliente() {
        var factory=new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000); factory.setReadTimeout(10000);
        return RestClient.builder().baseUrl("https://api.resend.com").requestFactory(factory).build();
    }
    public void verificarConfiguracao() {
        if (key.isBlank() || from.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
            "O acesso por e-mail ainda não foi ativado nesta instalação. Solicite a configuração à administração.");
    }
    public void enviar(String email, String link, String idempotencia) {
        verificarConfiguracao();
        try {
            client.post().uri("/emails").header("Authorization","Bearer "+key)
                .header("Idempotency-Key",idempotencia)
                .body(Map.of("from",from,"to",List.of(email),"subject","Seu link de acesso ao clinicOS",
                    "text","Acesse o clinicOS pelo link abaixo. Ele expira em 15 minutos e só pode ser usado uma vez.\n\n"
                        +link+"\n\nSe você não solicitou o acesso, ignore este e-mail."))
                .retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            // Nunca propaga resposta do provedor, destinatário ou token para logs/cliente.
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Não foi possível enviar o link agora. Tente novamente mais tarde.");
        }
    }
}
