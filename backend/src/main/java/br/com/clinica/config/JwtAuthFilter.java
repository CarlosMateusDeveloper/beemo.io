package br.com.clinica.config;

import br.com.clinica.service.*;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    private final SessionService sessions;
    private final TenantService tenants;
    private final br.com.clinica.repository.UsuarioRepository usuarios;
    public JwtAuthFilter(SessionService sessions,TenantService tenants,br.com.clinica.repository.UsuarioRepository usuarios) { this.sessions=sessions;this.tenants=tenants;this.usuarios=usuarios; }
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
        TenantContext.clear();
        try {
            var claims=sessions.validar(SessionCookies.token(req));
            if(claims.isPresent()) {
                int user=Integer.parseInt(claims.get().getSubject());
                if(usuarios.findById(user).isPresent()) {
                    var tenantAtual=tenants.atual(claims.get().getId(),user);
                    var tenant=tenants.resolver(user);
                    if(tenantAtual==null || !tenantAtual.id().equals(tenant.id())) {
                        tenants.vincularSessao(claims.get().getId(),user,tenant.id());
                    }
                    TenantContext.set(tenant);
                    var authorities=new ArrayList<SimpleGrantedAuthority>();
                    authorities.add(new SimpleGrantedAuthority("TENANT_ACCESS"));
                    tenant.papeis().forEach(role -> authorities.add(
                            new SimpleGrantedAuthority("ROLE_"+role.toUpperCase(Locale.ROOT))));
                    tenant.permissoes().forEach(permission -> authorities.add(
                            new SimpleGrantedAuthority("PERM_"+permission)));
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(user,null,authorities));
                }
            }
            chain.doFilter(req,res);
        } finally { TenantContext.clear(); }
    }
}
