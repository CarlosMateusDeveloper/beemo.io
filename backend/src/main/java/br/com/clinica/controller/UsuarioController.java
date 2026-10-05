package br.com.clinica.controller;

import br.com.clinica.service.TenantContext;
import br.com.clinica.service.TenantService;
import java.util.List;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Gerencia membros da clinica atual; nunca altera ou apaga a identidade global. */
@RestController @RequestMapping("/api/usuarios")
public class UsuarioController {
    private final TenantService tenants;
    public UsuarioController(TenantService tenants) { this.tenants=tenants; }
    @GetMapping public List<Map<String,Object>> list(Authentication auth) { return tenants.membros((Integer)auth.getPrincipal(),TenantContext.id()); }
    @GetMapping("/{id}") public Map<String,Object> get(Authentication auth,@PathVariable int id) {
        return list(auth).stream().filter(u->((Number)u.get("id")).intValue()==id).findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
    @PostMapping public ResponseEntity<Map<String,String>> create() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message","Autorize o e-mail em /api/tenants/{id}/invites. A identidade pertence ao usuário, não à clínica."));
    }
    @DeleteMapping("/{id}") public ResponseEntity<Void> remove(Authentication auth,@PathVariable int id) {
        tenants.revogar((Integer)auth.getPrincipal(),TenantContext.id(),id);
        return ResponseEntity.noContent().build();
    }
}
