package br.com.clinica.service;

import br.com.clinica.controller.PosVendaController;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PosVendaIndicadoresControllerTest {
    @Test void exportacaoTemNomeETipoCsv() {
        PosVendaService casos=mock(PosVendaService.class);
        PosVendaIndicadoresService indicadores=mock(PosVendaIndicadoresService.class);
        when(indicadores.exportar(any(),any(),eq("recuperados"))).thenReturn("caso;paciente\r\n");
        var resposta=new PosVendaController(casos,indicadores).exportarIndicador(
                LocalDate.of(2026,9,1),LocalDate.of(2026,9,30),"recuperados");
        assertEquals(200,resposta.getStatusCode().value());
        assertEquals("text/csv;charset=UTF-8",resposta.getHeaders().getContentType().toString());
        assertEquals("attachment; filename=pos-venda-recuperados.csv",
                resposta.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        assertEquals("caso;paciente\r\n",resposta.getBody());
    }
}
