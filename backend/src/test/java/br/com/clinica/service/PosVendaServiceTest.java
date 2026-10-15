package br.com.clinica.service;

import br.com.clinica.dto.PosVendaAcaoRequest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;
import java.time.OffsetDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PosVendaServiceTest {
    private final JdbcTemplate db = mock(JdbcTemplate.class);
    private final AccessControlService access = mock(AccessControlService.class);
    private final PosVendaService service = new PosVendaService(db, access);
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

    @Test void respostaRecebidaAbreAlertaSemCriarPrazoArtificialParaReagendado() {
        when(db.queryForList("SELECT * FROM pos_venda_caso WHERE id = ? FOR UPDATE",1L))
                .thenReturn(List.of(Map.of("versao",2,"status","reagendado","id_consulta_origem",10,
                        "id_responsavel",20)));
        when(db.queryForObject("SELECT id_paciente FROM consulta WHERE id_consulta = ?",Integer.class,10))
                .thenReturn(30);
        when(db.queryForObject("SELECT EXISTS (SELECT 1 FROM paciente_retorno_status WHERE id_paciente = ? AND status = 'nao_contatar')",Boolean.class,30))
                .thenReturn(false);
        var request = new PosVendaAcaoRequest(2,"resposta_recebida",null,null,null,false,
                "whatsapp",null,"Paciente pediu confirmação do horário.",null);

        service.agir(1,request,99);

        verify(db).update(contains("proxima_acao=CASE WHEN status='reagendado' THEN NULL"),eq(1L));
        verify(db).update(contains("INSERT INTO pos_venda_alerta"),eq(1L));
        verify(db).update(contains("INSERT INTO pos_venda_evento"),eq(1L),eq(99),
                eq("resposta_recebida"),eq("whatsapp"),contains("Paciente pediu confirmação"));
    }

    @Test void dataDeAuditoriaPreservaInstanteComOffset() {
        assertEquals("2026-10-11T09:30:00-03:00",
                PosVendaService.dataAuditoria(OffsetDateTime.parse("2026-10-11T09:30:00-03:00")));
    }
}
