package br.com.clinica.service;

import br.com.clinica.dto.DashboardRequest;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DashboardServiceTest {
    final LocalDate hoje = LocalDate.of(2024, 2, 29);
    @Test void resolvePeriodosEIncluiDiaBissexto() {
        var mes = DashboardService.periodo(new DashboardRequest("Mês", null, null, null), hoje);
        assertEquals(LocalDate.of(2024, 2, 1), mes.inicio());
        assertEquals(hoje, mes.fim());
        assertTrue(mes.semanal());
        var semana = DashboardService.periodo(new DashboardRequest("7 dias", null, null, null), hoje);
        assertEquals(hoje.minusDays(6), semana.inicio());
        assertEquals(hoje, semana.fim());
        assertEquals(hoje, DashboardService.periodo(new DashboardRequest("Hoje", 1, null, null), hoje).inicio());
    }
    @Test void rejeitaFiltrosInvalidosAntesDeConsultarBanco() {
        var em = mock(EntityManager.class);
        var service = new DashboardService(em, Clock.systemUTC());
        for (var request : new DashboardRequest[]{
            new DashboardRequest("desconhecido", null, null, null),
            new DashboardRequest("Hoje", -1, null, null),
            new DashboardRequest("Personalizado", null, hoje, null),
            new DashboardRequest("Personalizado", null, hoje, hoje.minusDays(1)),
            new DashboardRequest("Personalizado", null, hoje, hoje.plusDays(366))
        }) assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.calcular(request)).getStatusCode().value());
        verifyNoInteractions(em);
        assertDoesNotThrow(() -> DashboardService.periodo(new DashboardRequest("Personalizado", null, hoje, hoje.plusDays(365)), hoje));
    }
}
