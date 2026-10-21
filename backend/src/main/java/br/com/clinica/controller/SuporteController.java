package br.com.clinica.controller;

import br.com.clinica.service.SuporteService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/suporte")
public class SuporteController {
    private final SuporteService service;
    public SuporteController(SuporteService service) { this.service=service; }
    public record Novo(@NotBlank @Size(max=160) String titulo,@NotBlank @Size(max=3000) String descricao,@NotBlank String categoria,@NotBlank String prioridade) {}
    public record Mensagem(@NotBlank @Size(max=4000) String mensagem,boolean interna) {}
    public record Estado(@NotBlank String status,@PositiveOrZero int versao,Integer responsavel,@PositiveOrZero Integer esforcoMinutos) {}
    public record Avaliacao(@Min(1) @Max(5) int nota,@PositiveOrZero int versao) {}
    public record Acesso(@NotBlank @Email String agenteEmail,@NotBlank @Size(max=500) String motivo,@NotEmpty Set<String> escopos,@NotNull OffsetDateTime expiraEm) {}
    private int user(Authentication auth) { return (Integer)auth.getPrincipal(); }
    @GetMapping("/artigos") public List<Map<String,Object>> articles() { return service.artigos(); }
    @GetMapping("/chamados") public List<Map<String,Object>> tickets() { return service.chamados(); }
    @GetMapping("/chamados/{id}") public Map<String,Object> ticket(@PathVariable long id) { return service.chamado(id); }
    @PostMapping("/chamados") public Map<String,Object> create(Authentication auth,@Valid @RequestBody Novo value) { return service.criar(user(auth),value.titulo(),value.descricao(),value.categoria(),value.prioridade()); }
    @PostMapping("/chamados/{id}/mensagens") public Map<String,Object> message(Authentication auth,@PathVariable long id,@Valid @RequestBody Mensagem value) { return service.mensagem(user(auth),id,value.mensagem(),value.interna()); }
    @PatchMapping("/chamados/{id}/status") public Map<String,Object> state(Authentication auth,@PathVariable long id,@Valid @RequestBody Estado value) { return service.status(user(auth),id,value.status(),value.versao(),value.responsavel(),value.esforcoMinutos()); }
    @PostMapping("/chamados/{id}/satisfacao") public Map<String,Object> rating(Authentication auth,@PathVariable long id,@Valid @RequestBody Avaliacao value) { return service.satisfacao(user(auth),id,value.nota(),value.versao()); }
    @GetMapping("/metricas") public Map<String,Object> metrics() { return service.metricas(); }
    @GetMapping("/acessos") public List<Map<String,Object>> grants() { return service.acessos(); }
    @GetMapping("/acessos/modelo") public Map<String,Object> model() { return service.modeloAcesso(); }
    @PostMapping("/acessos") public Map<String,Object> request(Authentication auth,@Valid @RequestBody Acesso value) { return service.solicitarAcesso(user(auth),value.agenteEmail(),value.motivo(),value.escopos(),value.expiraEm()); }
    @PostMapping("/acessos/{id}/aprovar") public Map<String,Object> approve(Authentication auth,@PathVariable long id) { return service.decidirAcesso(user(auth),id,true); }
    @PostMapping("/acessos/{id}/negar") public Map<String,Object> deny(Authentication auth,@PathVariable long id) { return service.decidirAcesso(user(auth),id,false); }
    @DeleteMapping("/acessos/{id}") public ResponseEntity<Void> revoke(Authentication auth,@PathVariable long id) { service.revogarAcesso(user(auth),id);return ResponseEntity.noContent().build(); }
}
