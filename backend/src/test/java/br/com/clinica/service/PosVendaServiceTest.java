package br.com.clinica.service;

import br.com.clinica.dto.PosVendaAcaoRequest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PosVendaServiceTest {
    private final JdbcTemplate db = mock(JdbcTemplate.class);
    private final PosVendaService service = new PosVendaService(db);
    private PosVendaAcaoRequest request(int versao, String acao) {
        return new PosVendaAcaoRequest(versao,acao,null,null,null,false,null,null,null,null);
    }
    private void caso(String status) {
        when(db.queryForList("SELECT * FROM pos_venda_caso WHERE id = ? FOR UPDATE",1L))
                .thenReturn(List.of(Map.of("versao",2,"status",status,"id_consulta_origem",10)));
    }
    @Test void rejeitaEdicaoDesatualizada() {
        caso("pendente");
        var e = assertThrows(ResponseStatusException.class, () -> service.agir(1,request(1,"contato"),null));
        assertEquals(409,e.getStatusCode().value());
    }
    @Test void naoPermiteConcluirManualmenteComoRecuperado() {
        caso("pendente");
        var e = assertThrows(ResponseStatusException.class, () -> service.agir(1,request(2,"recuperado"),null));
        assertEquals("Ação inválida",e.getReason());
    }
    @Test void bloqueiaAlteracaoDeCasoRecuperado() {
        caso("recuperado");
        var e = assertThrows(ResponseStatusException.class, () -> service.agir(1,request(2,"contato"),null));
        assertEquals("Este caso está encerrado",e.getReason());
    }
    @Test void exigeResponsavelNoContato() {
        caso("pendente");
        var e = assertThrows(ResponseStatusException.class, () -> service.agir(1,request(2,"contato"),null));
        assertEquals("Selecione um responsável administrativo",e.getReason());
    }
    @Test void exigeMotivoParaEncerrar() {
        caso("pendente");
        var e = assertThrows(ResponseStatusException.class, () -> service.agir(1,request(2,"nao_deseja"),null));
        assertEquals("Registre o resultado ou motivo da ação",e.getReason());
    }
    @Test void exigeConsultaParaReagendar() {
        caso("pendente");
        var e = assertThrows(ResponseStatusException.class, () -> service.agir(1,request(2,"reagendar"),null));
        assertEquals("Selecione uma nova consulta da agenda",e.getReason());
    }
}
