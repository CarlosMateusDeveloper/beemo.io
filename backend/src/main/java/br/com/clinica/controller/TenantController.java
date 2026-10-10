package br.com.clinica.controller;

import br.com.clinica.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/tenants")
public class TenantController {
    private final TenantService tenants;
    private final SessionService sessions;
    private final br.com.clinica.config.OAuthClients clients;
    private final AccessAuditService audit;
    public TenantController(TenantService tenants,SessionService sessions,br.com.clinica.config.OAuthClients clients,
            AccessAuditService audit) { this.tenants=tenants;this.sessions=sessions;this.clients=clients;this.audit=audit; }
    public record Invite(@NotBlank @Email @Size(max=100) String email,@NotBlank String perfil) {}
    public record Accept(@NotBlank @Size(max=100) String token) {}
    @GetMapping("/{id}/members") public List<Map<String,Object>> members(Authentication auth,@PathVariable int id) { return tenants.membros((Integer)auth.getPrincipal(),id); }
    @GetMapping("/{id}/access-model") public Map<String,Object> accessModel(Authentication auth,@PathVariable int id) {
        return tenants.accessModel((Integer)auth.getPrincipal(),id);
    }
    @GetMapping("/{id}/audit") public List<Map<String,Object>> audit(Authentication auth,@PathVariable int id,
            @RequestParam(defaultValue="100") int limit) { return audit.list((Integer)auth.getPrincipal(),id,limit); }
    @PostMapping("/{id}/invites") public Map<String,String> invite(Authentication auth,@PathVariable int id,@Valid @RequestBody Invite request) {
        String token=tenants.convidar((Integer)auth.getPrincipal(),id,request.email(),request.perfil());
        return Map.of("url",clients.frontend()+"/convite#token="+token);
    }
    @PostMapping("/invites/accept") public TenantService.Access accept(Authentication auth,@Valid @RequestBody Accept request,HttpServletRequest req) {
        int user=(Integer)auth.getPrincipal();
        var access=tenants.aceitar(user,request.token());
        var claims=sessions.validar(SessionCookies.token(req)).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        tenants.vincularSessao(claims.getId(),user,access.id());
        return access;
    }
    @PatchMapping("/{id}/members/{user}") public TenantService.Access updateMember(Authentication auth,
            @PathVariable int id,@PathVariable int user,@RequestBody TenantService.MemberUpdate request) {
        return tenants.atualizarMembro((Integer)auth.getPrincipal(),id,user,request);
    }
    @DeleteMapping("/{id}/members/{user}") public ResponseEntity<Void> revoke(Authentication auth,@PathVariable int id,@PathVariable int user) { tenants.revogar((Integer)auth.getPrincipal(),id,user);return ResponseEntity.noContent().build(); }
    @ExceptionHandler(ResponseStatusException.class) public ResponseEntity<Map<String,String>> error(ResponseStatusException e) { return ResponseEntity.status(e.getStatusCode()).body(Map.of("message",e.getReason()==null?"Não foi possível concluir a operação.":e.getReason())); }
}
