package br.com.clinica.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
    @Test void aceitaSomenteMetricasRastreaveis() {
        for (String metrica : new String[]{"faltas","contatados","reagendados","recuperados","abertos",
                "novas_faltas","pendencias_vencidas","sem_responsavel"}) {
            assertDoesNotThrow(() -> PosVendaIndicadoresService.validarMetrica(metrica));
        }
        var erro=assertThrows(ResponseStatusException.class,
                () -> PosVendaIndicadoresService.validarMetrica("receita"));
        assertEquals(400,erro.getStatusCode().value());
    }
    @Test void limitaListagemInterativaAPaginasDeCemCasos() {
        assertDoesNotThrow(() -> PosVendaIndicadoresService.validarPaginacao(0,100));
        for (int tamanho : new int[]{0,101}) {
            var erro=assertThrows(ResponseStatusException.class,
                    () -> PosVendaIndicadoresService.validarPaginacao(0,tamanho));
            assertEquals(400,erro.getStatusCode().value());
        }
    }
    @Test void exportacaoNeutralizaFormulaDePlanilha() {
        JdbcTemplate db=mock(JdbcTemplate.class);
        AccessControlService access=mock(AccessControlService.class);
        when(access.clinicalDoctorId()).thenReturn(null);
        when(db.queryForList(anyString(),any(LocalDate.class),any(LocalDate.class),isNull(),isNull(),eq(10_001))).thenReturn(List.of(Map.of(
                "id",1,"pacienteId",2,"paciente","=HIPERLINK(malicioso)","telefone","+5585999999999",
                "status","recuperado","dataFalta","2026-09-01 10:00","responsavel","Equipe",
                "tentativas",1,"consultaOrigem",10)));
        String csv=new PosVendaIndicadoresService(db,access).exportar(inicio,inicio,"recuperados");
        assertTrue(csv.contains("\"'=HIPERLINK(malicioso)\""));
        assertTrue(csv.contains("\"'+5585999999999\""));
        assertTrue(csv.startsWith("caso;paciente_id;paciente_externo_id;paciente;"));
    }
}
