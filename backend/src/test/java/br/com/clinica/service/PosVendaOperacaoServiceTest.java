package br.com.clinica.service;

import br.com.clinica.dto.PosVendaOperacaoDtos.Status;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PosVendaOperacaoServiceTest {
    @Mock JdbcTemplate db;
    PosVendaOperacaoService service;

    @BeforeEach void setup() {
        service = new PosVendaOperacaoService(db, new ObjectMapper());
        TenantContext.set(new TenantService.Access(7,"Clínica","administrador",List.of("administrador"),
                PermissionCatalog.defaultsFor(Set.of("administrador")),null));
    }
    @AfterEach void clear() { TenantContext.clear(); }

    @Test void rejeitaPaginacaoForaDoLimiteAntesDeConsultarBanco() {
        var error=assertThrows(ResponseStatusException.class,()->service.tarefas(1,"todas","",0,101));
        assertEquals(400,error.getStatusCode().value());
        verifyNoInteractions(db);
    }

    @Test void conflitoOtimistaNaoSobrescreveNemRegistraHistorico() {
        when(db.update(startsWith("UPDATE pos_venda_tarefa SET status"),
                any(),any(),any(),any(),any(),any(),any())).thenReturn(0);
        var error=assertThrows(ResponseStatusException.class,()->service.statusTarefa(9,3,new Status("concluida",4)));
        assertEquals(409,error.getStatusCode().value());
        verify(db,never()).update(startsWith("INSERT INTO pos_venda_historico"),any(),any(),any(),any(),any());
    }

    @Test void statusInvalidoFalhaSemAlterarBanco() {
        var error=assertThrows(ResponseStatusException.class,()->service.statusCampanha(9,3,new Status("publicada",0)));
        assertEquals(400,error.getStatusCode().value());
        verifyNoInteractions(db);
    }
}
