package br.com.clinica.service;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class PosVendaIndicadoresServiceTest {
    private final LocalDate inicio = LocalDate.of(2026,9,1);
    @Test void aceitaUmDiaEPeriodoMaximo() {
        assertDoesNotThrow(() -> PosVendaIndicadoresService.validarPeriodo(inicio,inicio));
        assertDoesNotThrow(() -> PosVendaIndicadoresService.validarPeriodo(inicio,inicio.plusDays(365)));
    }
    @Test void rejeitaPeriodoInvertidoOuMuitoLongo() {
        for (var fim : new LocalDate[]{inicio.minusDays(1),inicio.plusDays(366)}) {
            var e=assertThrows(ResponseStatusException.class, () -> PosVendaIndicadoresService.validarPeriodo(inicio,fim));
            assertEquals(400,e.getStatusCode().value());
        }
    }
}
