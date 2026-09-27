package br.com.clinica.service;

import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** Limite por instância; não confia em X-Forwarded-For enviado pelo cliente. */
@Component
public class AuthRateLimiter {
    private record Janela(Instant fim, int usos) {}
    private final Map<String,Janela> janelas = new HashMap<>();
    public synchronized void verificar(String chave, int maximo) {
        Instant agora=Instant.now();
        janelas.entrySet().removeIf(e -> !e.getValue().fim().isAfter(agora));
        var janela=janelas.getOrDefault(chave,new Janela(agora.plusSeconds(900),0));
        if (janela.usos()>=maximo || (janelas.size()>=10000 && !janelas.containsKey(chave)))
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Muitas tentativas. Aguarde 15 minutos antes de tentar novamente.");
        janelas.put(chave,new Janela(janela.fim(),janela.usos()+1));
    }
}
