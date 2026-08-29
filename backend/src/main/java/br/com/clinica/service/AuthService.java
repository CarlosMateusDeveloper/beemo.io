package br.com.clinica.service;

import br.com.clinica.dto.LoginRequest;
import br.com.clinica.dto.LoginResponse;
import br.com.clinica.dto.UsuarioDto;
import br.com.clinica.model.Usuario;
import br.com.clinica.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UsuarioRepository repository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public LoginResponse login(LoginRequest request) {
        Usuario usuario = repository.findByEmail(request.email())
                .filter(u -> passwordEncoder.matches(request.senha(), u.getSenha()))
                // Mesma mensagem pra e-mail inexistente e senha errada — não dar
                // pista de qual dos dois está incorreto.
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "E-mail ou senha inválidos"));

        return new LoginResponse(jwtService.gerar(usuario), paraDto(usuario));
    }

    public UsuarioDto paraDto(Usuario usuario) {
        return new UsuarioDto(usuario.getId(), usuario.getNome(), usuario.getEmail(), usuario.getPerfil().name());
    }
}
