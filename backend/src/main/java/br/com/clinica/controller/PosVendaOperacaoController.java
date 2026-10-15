package br.com.clinica.controller;

import br.com.clinica.dto.PosVendaOperacaoDtos.*;
import br.com.clinica.service.PosVendaOperacaoService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/pos-venda")
public class PosVendaOperacaoController {
    private final PosVendaOperacaoService service;
    public PosVendaOperacaoController(PosVendaOperacaoService service) { this.service=service; }
    private int actor(Authentication auth) { return (Integer)auth.getPrincipal(); }

    @GetMapping("/tarefas") public Map<String,Object> tarefas(Authentication auth,
            @RequestParam(defaultValue="todas") String status,@RequestParam(defaultValue="") String busca,
            @RequestParam(defaultValue="0") int pagina,@RequestParam(defaultValue="50") int tamanho) {
        return service.tarefas(actor(auth),status,busca,pagina,tamanho);
    }
    @PostMapping("/tarefas") public ResponseEntity<Map<String,Object>> criarTarefa(Authentication auth,@Valid @RequestBody Tarefa input) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criarTarefa(actor(auth),input));
    }
    @PutMapping("/tarefas/{id}") public Map<String,Object> atualizarTarefa(Authentication auth,@PathVariable long id,@Valid @RequestBody Tarefa input) {
        return service.atualizarTarefa(id,actor(auth),input);
    }
    @PatchMapping("/tarefas/{id}/status") public Map<String,Object> statusTarefa(Authentication auth,@PathVariable long id,@Valid @RequestBody Status input) {
        return service.statusTarefa(id,actor(auth),input);
    }

    @GetMapping("/jornadas") public Map<String,Object> jornadas(@RequestParam(defaultValue="todas") String status,
            @RequestParam(defaultValue="") String busca,@RequestParam(defaultValue="0") int pagina,@RequestParam(defaultValue="50") int tamanho) {
        return service.jornadas(status,busca,pagina,tamanho);
    }
    @PostMapping("/jornadas") public ResponseEntity<Map<String,Object>> criarJornada(Authentication auth,@Valid @RequestBody Jornada input) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criarJornada(actor(auth),input));
    }
    @PutMapping("/jornadas/{id}") public Map<String,Object> atualizarJornada(Authentication auth,@PathVariable long id,@Valid @RequestBody Jornada input) {
        return service.atualizarJornada(id,actor(auth),input);
    }
    @PatchMapping("/jornadas/{id}/status") public Map<String,Object> statusJornada(Authentication auth,@PathVariable long id,@Valid @RequestBody Status input) {
        return service.statusJornada(id,actor(auth),input);
    }
    @PostMapping("/jornadas/{id}/inscricoes") public ResponseEntity<Map<String,Object>> inscrever(Authentication auth,@PathVariable long id,@Valid @RequestBody Inscricao input) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.inscrever(id,actor(auth),input));
    }

    @GetMapping("/campanhas") public Map<String,Object> campanhas(@RequestParam(defaultValue="todas") String status,
            @RequestParam(defaultValue="") String busca,@RequestParam(defaultValue="0") int pagina,@RequestParam(defaultValue="50") int tamanho) {
        return service.campanhas(status,busca,pagina,tamanho);
    }
    @PostMapping("/campanhas") public ResponseEntity<Map<String,Object>> criarCampanha(Authentication auth,@Valid @RequestBody Campanha input) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criarCampanha(actor(auth),input));
    }
    @PutMapping("/campanhas/{id}") public Map<String,Object> atualizarCampanha(Authentication auth,@PathVariable long id,@Valid @RequestBody Campanha input) {
        return service.atualizarCampanha(id,actor(auth),input);
    }
    @PatchMapping("/campanhas/{id}/status") public Map<String,Object> statusCampanha(Authentication auth,@PathVariable long id,@Valid @RequestBody Status input) {
        return service.statusCampanha(id,actor(auth),input);
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String,Object>> error(ResponseStatusException exception) {
        String message=exception.getReason()==null?"Não foi possível concluir a operação.":exception.getReason();
        String code=exception.getStatusCode().value()==409?"version_conflict":"invalid_request";
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of("code",code,"message",message));
    }
}
