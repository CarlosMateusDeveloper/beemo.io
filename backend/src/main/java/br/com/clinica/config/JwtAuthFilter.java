package br.com.clinica.config;

import br.com.clinica.service.JwtService;
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

// Lê "Authorization: Bearer <token>", valida via JwtService e, se válido,
// autentica a request com o id do usuário como principal (SecurityConfig
// decide quais rotas exigem isso — este filtro só popula o contexto).
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain chain
    ) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            Optional<Claims> claims = jwtService.validar(token);
            if (claims.isPresent()) {
                Integer idUsuario = Integer.valueOf(claims.get().getSubject());
                String perfil = claims.get().get("perfil", String.class);
                var authentication = new UsernamePasswordAuthenticationToken(
                        idUsuario, null, List.of(new SimpleGrantedAuthority("ROLE_" + perfil.toUpperCase()))
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        chain.doFilter(request, response);
    }
}
