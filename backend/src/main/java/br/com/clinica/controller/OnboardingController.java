package br.com.clinica.controller;

import br.com.clinica.dto.OnboardingConfigRequest;
import br.com.clinica.service.OnboardingService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/onboarding")
public class OnboardingController {
    private final OnboardingService service;

    public OnboardingController(OnboardingService service) { this.service = service; }

    @GetMapping
    public Map<String,Object> state() { return service.state(); }

    @PutMapping
    public Map<String,Object> update(@Valid @RequestBody OnboardingConfigRequest request,
            Authentication authentication) {
        return service.update(request, author(authentication));
    }

    @PostMapping("/concluir")
    public Map<String,Object> complete(Authentication authentication) {
        return service.complete(author(authentication));
    }

    @PostMapping("/reabrir")
    public Map<String,Object> reopen(Authentication authentication) {
        return service.reopen(author(authentication));
    }

    private static int author(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Integer value)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Faça login para configurar a clínica");
        }
        return value;
    }
}
