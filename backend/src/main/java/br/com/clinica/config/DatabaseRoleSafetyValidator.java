package br.com.clinica.config;

import java.util.Map;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Impede que a conta da aplicação anule as políticas RLS entre clínicas. */
@Component
public class DatabaseRoleSafetyValidator implements ApplicationRunner {
    private final Environment environment;
    private final JdbcTemplate database;

    public DatabaseRoleSafetyValidator(Environment environment, JdbcTemplate database) {
        this.environment = environment;
        this.database = database;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (environment.getProperty("app.production", Boolean.class, false)) validate(database);
    }

    static void validate(JdbcTemplate database) {
        Map<String, Object> role = database.queryForMap("""
                SELECT rolsuper AS superuser, rolbypassrls AS bypassrls
                FROM pg_roles WHERE rolname = current_user
                """);
        if (truthy(role.get("superuser")) || truthy(role.get("bypassrls"))) {
            throw new IllegalStateException("A conta do banco da aplicação não pode ter SUPERUSER ou BYPASSRLS");
        }
    }

    private static boolean truthy(Object value) {
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
    }
}
