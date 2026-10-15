package br.com.clinica.controller;

import br.com.clinica.dto.IntegracaoEventoRequest;
import br.com.clinica.integration.IntegracaoPrincipal;
import br.com.clinica.service.IntegracaoApiService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/integracoes/v1")
public class IntegracaoApiController {
    private final IntegracaoApiService service;
    public IntegracaoApiController(IntegracaoApiService service) { this.service = service; }

    @PostMapping("/eventos")
    public Map<String,Object> event(@Valid @RequestBody IntegracaoEventoRequest request,
                                    Authentication authentication) {
        return service.receber((IntegracaoPrincipal) authentication.getPrincipal(), request);
    }

    @GetMapping("/estado")
    public Map<String,Object> state(Authentication authentication) {
        return service.estado((IntegracaoPrincipal) authentication.getPrincipal());
    }

    @GetMapping(value = "/openapi.yaml", produces = "application/yaml")
    public ResponseEntity<Resource> contract() {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/yaml"))
                .body(new ClassPathResource("openapi/integracao-clinicas-v1.yaml"));
    }
}
