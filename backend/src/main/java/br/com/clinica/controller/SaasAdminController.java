package br.com.clinica.controller;

import br.com.clinica.service.SaasAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/saas")
public class SaasAdminController {
    private final SaasAdminService service;
    public SaasAdminController(SaasAdminService service) { this.service=service; }
    public record NewAction(@Positive int idClinica,Long idResponsavel,@NotBlank @Size(max=180) String titulo,@Size(max=1000) String descricao,@NotBlank String tipo,OffsetDateTime venceEm) {}
    public record StaffRequest(@NotBlank @Email String email,@NotBlank String papel,boolean ativo,boolean exigeReautenticacao) {}
    private int user(Authentication auth) { return (Integer)auth.getPrincipal(); }
    @GetMapping("/me") public Map<String,Object> me(Authentication auth) { return service.me(user(auth)); }
    @GetMapping("/clinicas") public Map<String,Object> clinics(Authentication auth,@RequestParam(required=false) String busca,@RequestParam(required=false) String risco,@RequestParam(defaultValue="0") int pagina,@RequestParam(defaultValue="25") int tamanho) { return service.dashboard(user(auth),busca,risco,pagina,tamanho); }
    @GetMapping("/acoes") public List<Map<String,Object>> actions(Authentication auth,@RequestParam(required=false) Integer clinica) { return service.actions(user(auth),clinica); }
    @PostMapping("/acoes") public Map<String,Object> create(Authentication auth,@Valid @RequestBody NewAction request) { return service.createAction(user(auth),request.idClinica(),request.idResponsavel(),request.titulo(),request.descricao(),request.tipo(),request.venceEm()); }
    @PostMapping("/acoes/{id}/concluir") public ResponseEntity<Void> complete(Authentication auth,@PathVariable long id) { service.completeAction(user(auth),id);return ResponseEntity.noContent().build(); }
    @PutMapping("/clinicas/{id}/responsavel/{staff}") public ResponseEntity<Void> assign(Authentication auth,@PathVariable int id,@PathVariable long staff) { service.assign(user(auth),id,staff);return ResponseEntity.noContent().build(); }
    @GetMapping("/funcionarios") public List<Map<String,Object>> staff(Authentication auth) { return service.staffList(user(auth)); }
    @PutMapping("/funcionarios") public Map<String,Object> saveStaff(Authentication auth,@Valid @RequestBody StaffRequest request) { return service.saveStaff(user(auth),request.email(),request.papel(),request.ativo(),request.exigeReautenticacao()); }
}
