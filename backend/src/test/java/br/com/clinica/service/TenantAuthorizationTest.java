package br.com.clinica.service;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TenantAuthorizationTest {
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test void autenticarUsuarioSemVinculoNaoCriaClinicaNemPermissao() {
        JdbcTemplate db=mock(JdbcTemplate.class);
        when(db.query(anyString(),any(RowMapper.class),eq(42))).thenReturn(List.of());
        assertNull(new TenantService(db).resolver(42));
        verify(db,never()).update(startsWith("INSERT INTO clinica"),(Object[])any());
        verify(db,never()).update(startsWith("INSERT INTO tenant_membro"),(Object[])any());
    }
}
