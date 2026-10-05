package br.com.clinica.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class TenantDatabaseSafety {
    @Bean ApplicationRunner validateTenantDatabase(JdbcTemplate db) {
        return args -> {
            if(Boolean.TRUE.equals(db.queryForObject("SELECT rolsuper OR rolbypassrls FROM pg_roles WHERE rolname=current_user",Boolean.class)))
                throw new IllegalStateException("Use um usuario PostgreSQL sem SUPERUSER e sem BYPASSRLS para preservar o isolamento dos tenants.");
            if(!Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM auth_schema_version WHERE version=12)",Boolean.class)))
                throw new IllegalStateException("Aplique a migracao 012_tenant_identity_isolation.sql antes de iniciar a API.");
            var unsafe=db.queryForList("SELECT tablename FROM pg_tables t JOIN pg_class c ON c.relname=t.tablename JOIN pg_namespace n ON n.oid=c.relnamespace AND n.nspname=t.schemaname WHERE t.schemaname='public' AND t.tablename NOT LIKE 'auth_%' AND t.tablename NOT LIKE 'tenant_%' AND t.tablename NOT IN ('usuario','clinica') AND NOT(c.relrowsecurity AND c.relforcerowsecurity)",String.class);
            if(!unsafe.isEmpty()) throw new IllegalStateException("Tabelas clinicas sem isolamento: "+unsafe);
        };
    }
}
