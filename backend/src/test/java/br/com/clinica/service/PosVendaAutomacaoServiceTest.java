package br.com.clinica.service;

import br.com.clinica.dto.PosVendaAutomacaoDtos.Regra;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
class PosVendaAutomacaoServiceTest {
    @Mock JdbcTemplate db;
    PosVendaAutomacaoService service;

    @BeforeEach void setup() { service = new PosVendaAutomacaoService(db,new ObjectMapper()); }

    @Test void rejeitaEventoDesconhecidoAntesDeAlterarBanco() {
        var input = new Regra("Regra","EVENTO_INVENTADO","criar_tarefa",null,null,Map.of(),10,true,null);
        var error = assertThrows(ResponseStatusException.class,() -> service.criar(1,input));
        assertEquals(400,error.getStatusCode().value());
        verifyNoInteractions(db);
    }

    @Test void exigeJornadaNaRegraDeInscricao() {
        var input = new Regra("Regra","PRONTUARIO_RETORNO_INDICADO","inscrever_jornada",null,null,Map.of(),10,true,null);
        var error = assertThrows(ResponseStatusException.class,() -> service.criar(1,input));
        assertEquals(400,error.getStatusCode().value());
        verifyNoInteractions(db);
    }

    @Test void rejeitaPaginacaoExcessiva() {
        var error = assertThrows(ResponseStatusException.class,() -> service.eventos("todos",0,101));
        assertEquals(400,error.getStatusCode().value());
        verifyNoInteractions(db);
    }

    @Test void reprocessaSomenteEventoComErro() {
        when(db.queryForList(startsWith("SELECT status FROM pos_venda_evento_clinico"),eq(String.class),eq(8L)))
                .thenReturn(java.util.List.of("processado"));
        var error = assertThrows(ResponseStatusException.class,() -> service.reprocessar(8));
        assertEquals(409,error.getStatusCode().value());
        verify(db,never()).update(startsWith("UPDATE pos_venda_evento_clinico SET status='pendente'"),eq(8L));
    }
}
