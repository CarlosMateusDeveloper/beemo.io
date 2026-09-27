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
    private final SessionService jwtService;
    private final String hashAusente;

    public AuthService(UsuarioRepository repository, PasswordEncoder passwordEncoder, SessionService jwtService) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.hashAusente=passwordEncoder.encode(java.util.UUID.randomUUID().toString());
    }

    @org.springframework.transaction.annotation.Transactional
    public LoginResponse login(LoginRequest request) {
        var encontrado = repository.findByEmailIgnoreCase(request.email().trim());
        boolean confere;
        try { confere=passwordEncoder.matches(request.senha(),encontrado.map(Usuario::getSenha).orElse(hashAusente)); }
        catch (IllegalArgumentException e) { confere=false; }
        if (!confere || encontrado.isEmpty()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"E-mail ou senha inválidos");
        Usuario usuario=encontrado.get();
        if(passwordEncoder.upgradeEncoding(usuario.getSenha())) {
            usuario.setSenha(passwordEncoder.encode(request.senha()));
            repository.save(usuario);
        }

        return new LoginResponse(jwtService.gerar(usuario), paraDto(usuario));
    }

    public UsuarioDto paraDto(Usuario usuario) {
        return new UsuarioDto(usuario.getId(), usuario.getNome(), usuario.getEmail(), usuario.getPerfil().name());
    }
}
