package br.com.clinica.controller;

import br.com.clinica.dto.ComunicacaoDtos;
import br.com.clinica.service.ComunicacaoPolicyService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pos-venda/comunicacao")
public class ComunicacaoController {
    private final ComunicacaoPolicyService service;
    public ComunicacaoController(ComunicacaoPolicyService service) { this.service = service; }
    private static int actor(Authentication auth) { return (Integer) auth.getPrincipal(); }

    @GetMapping("/pacientes/{id}")
    public Map<String,Object> patient(@PathVariable int id) { return service.paciente(id); }

    @PostMapping("/consentimentos")
    public ResponseEntity<Map<String,Object>> consent(@Valid @RequestBody ComunicacaoDtos.Consentimento input,
                                                       Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.registrar(input, actor(auth)));
    }

    @GetMapping("/modelos")
    public List<Map<String,Object>> models(@RequestParam(defaultValue="false") boolean somenteAprovados) {
        return service.modelos(somenteAprovados);
    }

    @GetMapping("/modelos/{codigo}/versoes")
    public List<Map<String,Object>> versions(@PathVariable String codigo) { return service.versoes(codigo); }

    @GetMapping("/modelos/{id}/historico")
    public List<Map<String,Object>> modelHistory(@PathVariable long id) { return service.historicoModelo(id); }

    @PostMapping("/modelos")
    public ResponseEntity<Map<String,Object>> model(@Valid @RequestBody ComunicacaoDtos.Modelo input,
                                                     Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criarModelo(input, actor(auth)));
    }

    @PostMapping("/modelos/{id}/status")
    public ResponseEntity<Map<String,Object>> modelStatus(@PathVariable long id,
            @Valid @RequestBody ComunicacaoDtos.ModeloStatus input, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.transicionarModelo(id, input, actor(auth)));
    }
}
