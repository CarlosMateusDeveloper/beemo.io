package br.com.clinica.controller;

import br.com.clinica.dto.LoginRequest;
import br.com.clinica.dto.LoginResponse;
import br.com.clinica.dto.UsuarioDto;
import br.com.clinica.model.Usuario;
import br.com.clinica.repository.UsuarioRepository;
import br.com.clinica.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UsuarioRepository usuarioRepository;

    public AuthController(AuthService authService, UsuarioRepository usuarioRepository) {
        this.authService = authService;
        this.usuarioRepository = usuarioRepository;
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        return authService.login(request);
    }

    // Usado pelo frontend pra restaurar a sessão a partir do token salvo,
    // sem pedir a senha de novo — se o token ainda for válido (JwtAuthFilter
    // já populou o contexto), devolve os dados atuais do usuário.
    @GetMapping("/me")
    public UsuarioDto me(Authentication authentication) {
        if (authentication == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        Integer id = (Integer) authentication.getPrincipal();
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return authService.paraDto(usuario);
    }
}
