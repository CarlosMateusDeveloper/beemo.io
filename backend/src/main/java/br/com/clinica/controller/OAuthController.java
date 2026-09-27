package br.com.clinica.controller;

import br.com.clinica.config.OAuthClients;
import br.com.clinica.config.OAuthSecurityConfig;
import br.com.clinica.service.OAuthIdentityService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController @RequestMapping("/api/auth")
public class OAuthController {
    private final OAuthClients clients;
    private final OAuthIdentityService identities;
    public OAuthController(OAuthClients clients,OAuthIdentityService identities) { this.clients=clients;this.identities=identities; }
    @GetMapping("/providers") public List<Map<String,String>> providers() { return clients.available(); }
    @GetMapping("/oauth/identities") public List<Map<String,Object>> identities(Authentication auth) { return identities.listar((Integer)auth.getPrincipal()); }
    @PostMapping("/oauth/link/{provider}") public Map<String,String> link(@PathVariable String provider,Authentication auth,HttpServletRequest req) {
        String url=clients.authorization(provider);
        var old=req.getSession(false);
        if(old!=null) old.invalidate();
        var session=req.getSession(true);
        session.setMaxInactiveInterval(300);
        session.setAttribute(OAuthSecurityConfig.LINK_USER,(Integer)auth.getPrincipal());
        session.setAttribute(OAuthSecurityConfig.LINK_PROVIDER,provider);
        return Map.of("url",url);
    }
}
