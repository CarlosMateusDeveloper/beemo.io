package br.com.clinica.controller;

import br.com.clinica.dto.*;
import br.com.clinica.repository.UsuarioRepository;
import br.com.clinica.service.*;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
import java.util.Locale;

@RestController @RequestMapping({"/api/auth", "/api/v1/auth"})
public class AuthController {
    private final AuthService auth;
    private final UsuarioRepository usuarios;
    private final MagicLinkService magic;
    private final AuthRateLimiter limiter;
    private final SessionService sessions;
    private final SessionCookies cookies;
    public AuthController(AuthService auth,UsuarioRepository usuarios,MagicLinkService magic,AuthRateLimiter limiter,
            SessionService sessions,SessionCookies cookies) {
        this.auth=auth;this.usuarios=usuarios;this.magic=magic;this.limiter=limiter;this.sessions=sessions;this.cookies=cookies;
    }
    @GetMapping("/csrf") public Map<String,String> csrf(CsrfToken token) { return Map.of("token",token.getToken(),"headerName",token.getHeaderName()); }
    @PostMapping("/login") public LoginResponse login(@Valid @RequestBody LoginRequest request,HttpServletRequest req,HttpServletResponse res) {
        limitar("senha",request.email(),req,15);
        var result=auth.login(request);
        cookies.emitir(req,res,result.token());
        return result;
    }
    public record MagicRequest(@NotBlank @Email @Size(max=100) String email) {}
    public record VerifyRequest(@NotBlank @Size(max=100) String token) {}
    @PostMapping("/magic-link") public ResponseEntity<Map<String,String>> solicitar(@Valid @RequestBody MagicRequest request,HttpServletRequest req) {
        limitar("link",request.email(),req,3);
        magic.solicitar(request.email());
        return ResponseEntity.accepted().body(Map.of("message","Enviamos um link de acesso para seu e-mail. Confira também a pasta de spam."));
    }
    @PostMapping("/magic-link/verify") public LoginResponse verificar(@Valid @RequestBody VerifyRequest request,HttpServletRequest req,HttpServletResponse res) {
        limiter.verificar("verify-ip:"+req.getRemoteAddr(),30);
        var result=magic.verificar(request.token());
        cookies.emitir(req,res,result.token());
        return result;
    }
    @PostMapping("/logout") public ResponseEntity<Void> logout(HttpServletRequest req,HttpServletResponse res) {
        sessions.revogar(SessionCookies.token(req));
        cookies.limpar(req,res);
        var session=req.getSession(false);
        if(session!=null) session.invalidate();
        return ResponseEntity.noContent().build();
    }
    @GetMapping("/me") public UsuarioDto me(Authentication authentication) {
        if(authentication==null || !(authentication.getPrincipal() instanceof Integer id)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return auth.paraDto(usuarios.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED)),TenantContext.get());
    }
    @RequestMapping(value="/session/check",method={RequestMethod.GET,RequestMethod.POST})
    public UsuarioDto check(Authentication authentication) { return me(authentication); }
    private void limitar(String fluxo,String email,HttpServletRequest req,int maximo) {
        limiter.verificar(fluxo+"-ip:"+req.getRemoteAddr(),50);
        limiter.verificar(fluxo+"-email:"+email.trim().toLowerCase(Locale.ROOT),maximo);
    }
    @ExceptionHandler(ResponseStatusException.class) public ResponseEntity<Map<String,Object>> erro(ResponseStatusException e) {
        String message=e.getReason()==null?"Não foi possível autenticar.":e.getReason();
        String code=e.getStatusCode().value()==429?"rate_limited":"authentication_failed";
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("message",message,"error",Map.of("code",code,"message",message)));
    }
    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String,Object>> dadosInvalidos() {
        String message="Confira os campos informados e tente novamente.";
        return ResponseEntity.badRequest().body(Map.of("message",message,"error",Map.of("code","invalid_request","message",message)));
    }
}
