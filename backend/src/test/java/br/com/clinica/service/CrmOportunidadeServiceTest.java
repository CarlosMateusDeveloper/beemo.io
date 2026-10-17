package br.com.clinica.service;

import br.com.clinica.dto.CrmDtos.*;
import java.util.Map;
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
class CrmOportunidadeServiceTest {
    @Mock JdbcTemplate db;
    CrmOportunidadeService service;
    @BeforeEach void setup() { service=new CrmOportunidadeService(db,new ObjectMapper()); }

    @Test void perdaSemMotivoERejeitada() {
        var error=assertThrows(ResponseStatusException.class,()->service.transicionar(9,3,new Transicao("perdido",null,null,null,2)));
        assertEquals(400,error.getStatusCode().value());
        verify(db,never()).update(startsWith("UPDATE crm_oportunidade"),any(Object[].class));
    }

    @Test void versaoImpedeSobrescritaConcorrente() {
        when(db.queryForList(contains("FOR UPDATE"),eq(9L))).thenReturn(java.util.List.of(Map.of("id_oportunidade",9L,"etapa","novo","versao",4)));
        var error=assertThrows(ResponseStatusException.class,()->service.transicionar(9,3,new Transicao("em_contato",null,null,null,3)));
        assertEquals(409,error.getStatusCode().value());
        verify(db,never()).update(startsWith("UPDATE crm_oportunidade"),any(Object[].class));
    }

    @Test void interessadoNaoExigeCpfMasExigeMeioDeContato() {
        var request=new CriarOportunidade("Ana",null,null,"whatsapp",true,"indicacao","Dra. Ana","Dermatologia",null,null,null,"req-1",null);
        var error=assertThrows(ResponseStatusException.class,()->service.criar(3,request));
        assertEquals(400,error.getStatusCode().value());
        verifyNoInteractions(db);
    }
}
