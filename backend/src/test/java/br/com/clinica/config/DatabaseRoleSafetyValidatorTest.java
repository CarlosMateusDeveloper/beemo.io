package br.com.clinica.config;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatabaseRoleSafetyValidatorTest {
    @Test
    void aceitaContaSemPrivilegiosQueAnulamRls() {
        var database = mock(JdbcTemplate.class);
        when(database.queryForMap(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Map.<String,Object>of("superuser", false, "bypassrls", false));
        assertDoesNotThrow(() -> DatabaseRoleSafetyValidator.validate(database));
    }

    @Test
    void recusaContaComBypassRlsOuSuperusuario() {
        for (var flags : java.util.List.of(
                Map.<String,Object>of("superuser", true, "bypassrls", false),
                Map.<String,Object>of("superuser", false, "bypassrls", true))) {
            var database = mock(JdbcTemplate.class);
            when(database.queryForMap(org.mockito.ArgumentMatchers.anyString())).thenReturn(flags);
            assertThrows(IllegalStateException.class, () -> DatabaseRoleSafetyValidator.validate(database));
        }
    }
}
