package br.com.clinica.controller;

import br.com.clinica.dto.CrmDtos.*;
import br.com.clinica.service.CrmOportunidadeService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/crm/oportunidades")
public class CrmOportunidadeController {
    private final CrmOportunidadeService service;
    public CrmOportunidadeController(CrmOportunidadeService service) { this.service=service; }
    private int actor(Authentication auth) { return (Integer)auth.getPrincipal(); }

    @GetMapping public Map<String,Object> listar(
            @RequestParam(defaultValue="abertas") String etapa,@RequestParam(defaultValue="") String busca,
            @RequestParam(defaultValue="") String origem,@RequestParam(required=false) Integer responsavel,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(defaultValue="0") int pagina,@RequestParam(defaultValue="50") int tamanho) {
        return service.listar(etapa,busca,origem,responsavel,inicio,fim,pagina,tamanho);
    }
    @GetMapping("/indicadores") public Map<String,Object> indicadores(
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate fim) {
        return service.indicadores(inicio,fim);
    }
    @GetMapping("/responsaveis") public List<Map<String,Object>> responsaveis() { return service.responsaveis(); }
    @GetMapping("/{id}") public Map<String,Object> detalhe(@PathVariable long id) { return service.detalhe(id); }
    @PostMapping public ResponseEntity<Map<String,Object>> criar(Authentication auth,@Valid @RequestBody CriarOportunidade input) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(actor(auth),input));
    }
    @PutMapping("/{id}") public Map<String,Object> atualizar(Authentication auth,@PathVariable long id,
            @Valid @RequestBody AtualizarOportunidade input) { return service.atualizar(id,actor(auth),input); }
    @PostMapping("/{id}/transicoes") public Map<String,Object> transicionar(Authentication auth,@PathVariable long id,
            @Valid @RequestBody Transicao input) { return service.transicionar(id,actor(auth),input); }
    @PostMapping("/{id}/interacoes") public Map<String,Object> interacao(Authentication auth,@PathVariable long id,
            @Valid @RequestBody Interacao input) { return service.interacao(id,actor(auth),input); }
    @PostMapping("/{id}/converter") public Map<String,Object> converter(Authentication auth,@PathVariable long id,
            @Valid @RequestBody Converter input) { return service.converter(id,actor(auth),input); }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String,String>> error(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("message",
                error.getReason()==null?"Não foi possível concluir a ação.":error.getReason()));
    }
}
