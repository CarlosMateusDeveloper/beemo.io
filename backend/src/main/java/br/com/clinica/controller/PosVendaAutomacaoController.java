package br.com.clinica.controller;

import br.com.clinica.dto.PosVendaAutomacaoDtos.Regra;
import br.com.clinica.service.PosVendaAutomacaoService;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pos-venda/automacoes")
public class PosVendaAutomacaoController {
    private final PosVendaAutomacaoService service;
    public PosVendaAutomacaoController(PosVendaAutomacaoService service) { this.service=service; }
    private int actor(Authentication auth) { return (Integer)auth.getPrincipal(); }

    @GetMapping("/catalogo") public Map<String,Object> catalogo() { return service.catalogo(); }
    @GetMapping("/regras") public List<Map<String,Object>> regras() { return service.regras(); }
    @PostMapping("/regras") public ResponseEntity<Map<String,Object>> criar(Authentication auth,@Valid @RequestBody Regra input) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(actor(auth),input));
    }
    @PutMapping("/regras/{id}") public Map<String,Object> atualizar(Authentication auth,@PathVariable long id,
            @Valid @RequestBody Regra input) { return service.atualizar(id,actor(auth),input); }
    @GetMapping("/eventos") public Map<String,Object> eventos(@RequestParam(defaultValue="todos") String status,
            @RequestParam(defaultValue="0") int pagina,@RequestParam(defaultValue="50") int tamanho) {
        return service.eventos(status,pagina,tamanho);
    }
    @GetMapping("/eventos/{id}") public Map<String,Object> evento(@PathVariable long id) { return service.evento(id); }
    @PostMapping("/eventos/{id}/reprocessar") public Map<String,Object> reprocessar(@PathVariable long id) {
        return service.reprocessar(id);
    }
}
