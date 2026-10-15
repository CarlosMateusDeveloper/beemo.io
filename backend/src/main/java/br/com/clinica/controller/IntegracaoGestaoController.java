package br.com.clinica.controller;

import br.com.clinica.dto.IntegracaoCredencialRequest;
import br.com.clinica.service.IntegracaoApiService;
import br.com.clinica.service.IntegracaoCredencialService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/pos-venda/integracoes")
public class IntegracaoGestaoController {
    private final IntegracaoCredencialService credentials;
    private final IntegracaoApiService integrations;
    public IntegracaoGestaoController(IntegracaoCredencialService credentials, IntegracaoApiService integrations) {
        this.credentials = credentials; this.integrations = integrations;
    }

    @GetMapping
    public Map<String,Object> list() {
        return Map.of("fontes", integrations.fontes(), "credenciais", credentials.listar());
    }

    @PostMapping("/credenciais")
    public Map<String,Object> create(@Valid @RequestBody IntegracaoCredencialRequest request,
                                     Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Integer author)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Faça login para configurar a integração");
        }
        return credentials.criar(request, author);
    }

    @DeleteMapping("/credenciais/{id}")
    public ResponseEntity<Void> revoke(@PathVariable long id) {
        credentials.revogar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/fontes/{id}/eventos")
    public List<Map<String,Object>> events(@PathVariable long id,
                                           @RequestParam(required = false) String resultado) {
        return integrations.eventos(id, resultado);
    }

    @PostMapping("/eventos/{id}/reprocessar")
    public Map<String,Object> reprocess(@PathVariable long id) { return integrations.reprocessar(id); }
}
