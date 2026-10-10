package br.com.clinica.config;

import br.com.clinica.service.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/** JPA e JDBC usam a mesma transacao; SET LOCAL nunca vaza para outra conexao do pool. */
@Component
public class TenantDatabaseFilter extends OncePerRequestFilter {
    private final JdbcTemplate db;
    private final TransactionTemplate transaction;
    public TenantDatabaseFilter(JdbcTemplate db,PlatformTransactionManager manager) { this.db=db;this.transaction=new TransactionTemplate(manager); }
    @Override protected boolean shouldNotFilter(HttpServletRequest req) {
        String path=req.getServletPath();
        return !path.startsWith("/api/") || path.startsWith("/api/auth/") || path.startsWith("/api/v1/auth/")
            || path.equals("/api/tenants") || path.startsWith("/api/tenants/");
    }
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
        if(TenantContext.get()==null) { chain.doFilter(req,res);return; }
        var buffered=new ContentCachingResponseWrapper(res);
        try {
            transaction.executeWithoutResult(status -> {
                db.queryForObject("SELECT set_config('app.tenant_id',?,true)",String.class,Integer.toString(TenantContext.id()));
                try { chain.doFilter(req,buffered); }
                catch(ServletException|IOException e) { throw new FilterFailure(e); }
                if(buffered.getStatus()>=400) status.setRollbackOnly();
            });
            buffered.copyBodyToResponse();
        } catch(FilterFailure e) {
            if(e.getCause() instanceof IOException io) throw io;
            throw (ServletException)e.getCause();
        }
    }
    private static class FilterFailure extends RuntimeException { FilterFailure(Exception cause) { super(cause); } }
}
