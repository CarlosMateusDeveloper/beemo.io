package br.com.clinica.config;

import br.com.clinica.service.SessionService;
import br.com.clinica.service.SessionCookies;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

// Lê cookie HttpOnly ou Bearer, valida a sessão revogável via SessionService e, se válido,
// autentica a request com o id do usuário como principal (SecurityConfig
// decide quais rotas exigem isso — este filtro só popula o contexto).
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final SessionService jwtService;
    private final br.com.clinica.repository.UsuarioRepository usuarios;

    public JwtAuthFilter(SessionService jwtService, br.com.clinica.repository.UsuarioRepository usuarios) {
        this.jwtService = jwtService;
        this.usuarios=usuarios;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain chain
    ) throws ServletException, IOException {
        String token=SessionCookies.token(request);
        if (!token.isEmpty()) {
            Optional<Claims> claims = jwtService.validar(token);
            if (claims.isPresent()) {
                try {
                    usuarios.findById(Integer.valueOf(claims.get().getSubject())).ifPresent(u -> {
                        var authentication = new UsernamePasswordAuthenticationToken(u.getId(), null,
                            List.of(new SimpleGrantedAuthority("ROLE_"+u.getPerfil().name().toUpperCase(java.util.Locale.ROOT))));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    });
                } catch (NumberFormatException ignored) { SecurityContextHolder.clearContext(); }
            }
        }
        chain.doFilter(request, response);
    }
}
