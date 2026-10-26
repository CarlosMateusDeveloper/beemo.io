package br.com.clinica.service;

import br.com.clinica.dto.PreAtendimentoDtos;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PreAtendimentoServiceTest {
    @Test void tokenEOpacoAleatorioEAPersistenciaUsaSomenteHash() {
        String first=PreAtendimentoService.randomToken();
        String second=PreAtendimentoService.randomToken();
        assertEquals(43,first.length());
        assertNotEquals(first,second);
        assertEquals(64,PreAtendimentoService.sha256(first).length());
        assertFalse(PreAtendimentoService.sha256(first).contains(first));
    }

    @Test void aceitaSomenteAssinaturasReaisDePdfJpegEPng() {
        assertEquals("application/pdf",PreAtendimentoService.validarArquivo("doc.pdf","application/pdf","%PDF-1.7\nconteudo".getBytes(StandardCharsets.ISO_8859_1)));
        assertEquals("image/jpeg",PreAtendimentoService.validarArquivo("foto.jpg","image/jpeg",new byte[]{(byte)0xff,(byte)0xd8,(byte)0xff,1}));
        assertEquals("image/png",PreAtendimentoService.validarArquivo("foto.png","image/png",new byte[]{(byte)0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a,1}));
        assertThrows(ResponseStatusException.class,() -> PreAtendimentoService.validarArquivo("falso.pdf","application/pdf","texto".getBytes()));
        assertThrows(ResponseStatusException.class,() -> PreAtendimentoService.validarArquivo("foto.jpg","image/png",new byte[]{(byte)0xff,(byte)0xd8,(byte)0xff,1}));
    }

    @Test void bloqueiaPdfAtivoEArquivoAcimaDoLimite() {
        assertThrows(ResponseStatusException.class,() -> PreAtendimentoService.validarArquivo("ativo.pdf","application/pdf","%PDF-1.7 /OpenAction".getBytes(StandardCharsets.ISO_8859_1)));
        assertThrows(ResponseStatusException.class,() -> PreAtendimentoService.validarArquivo("grande.png","image/png",new byte[8*1024*1024+1]));
    }

    @Test void tentativaInvalidaMantemContadorMesmoComResposta401() throws Exception {
        JdbcTemplate db=mock(JdbcTemplate.class);
        String token=PreAtendimentoService.randomToken();
        when(db.queryForList(contains("FOR UPDATE OF a"),eq(PreAtendimentoService.sha256(token))))
                .thenReturn(List.of(Map.of("estado","ativo","id_clinica",1,"id_consulta",7)));
        when(db.queryForObject(contains("SELECT EXISTS(SELECT 1 FROM consulta"),eq(Boolean.class),eq(7),any(),eq("1234")))
                .thenReturn(false);
        var service=new PreAtendimentoService(db,new ObjectMapper(),"http://localhost:5173");
        var error=assertThrows(ResponseStatusException.class,
                () -> service.verificar(token,new PreAtendimentoDtos.Verificar(LocalDate.of(1990,1,1),"1234")));
        assertEquals(401,error.getStatusCode().value());
        verify(db).update(contains("tentativas=tentativas+1"),eq(PreAtendimentoService.sha256(token)));
        var transaction=PreAtendimentoService.class.getMethod("verificar",String.class,PreAtendimentoDtos.Verificar.class)
                .getAnnotation(Transactional.class);
        assertArrayEquals(new Class<?>[]{ResponseStatusException.class},transaction.noRollbackFor());
    }

    @Test void linkSemEscopoDeCadastroNaoPodeEnviarDadosCadastrais() {
        JdbcTemplate db=mock(JdbcTemplate.class);
        String token=PreAtendimentoService.randomToken();
        String session=PreAtendimentoService.randomToken();
        when(db.queryForList(anyString(),eq(PreAtendimentoService.sha256(token))))
                .thenReturn(List.of(Map.of("estado","ativo","id_clinica",1,"id_pre_atendimento",4L,
                        "escopo",new String[]{"documentos"})));
        when(db.queryForObject(contains("FROM auth_pre_atendimento_sessao"),eq(Boolean.class),
                eq(PreAtendimentoService.sha256(session)),eq(PreAtendimentoService.sha256(token))))
                .thenReturn(true);
        var service=new PreAtendimentoService(db,new ObjectMapper(),"http://localhost:5173");
        var error=assertThrows(ResponseStatusException.class,() -> service.salvar(token,session,
                new PreAtendimentoDtos.Salvar(1,Map.of("nome","Outro paciente"),Map.of())));
        assertEquals(403,error.getStatusCode().value());
        verify(db,never()).update(contains("UPDATE pre_atendimento SET"),any(Object[].class));
    }

    @Test void anexoEnviadoNaoPodeSerAcrescentadoAposEnvio() {
        JdbcTemplate db=mock(JdbcTemplate.class);
        String token=PreAtendimentoService.randomToken();
        String session=PreAtendimentoService.randomToken();
        when(db.queryForList(anyString(),eq(PreAtendimentoService.sha256(token))))
                .thenReturn(List.of(Map.of("estado","ativo","id_clinica",1,"id_pre_atendimento",4L,
                        "escopo",new String[]{"documentos"})));
        when(db.queryForObject(contains("FROM auth_pre_atendimento_sessao"),eq(Boolean.class),
                eq(PreAtendimentoService.sha256(session)),eq(PreAtendimentoService.sha256(token))))
                .thenReturn(true);
        when(db.queryForObject(contains("FROM pre_atendimento WHERE id_pre_atendimento=? FOR UPDATE"),eq(String.class),eq(4L)))
                .thenReturn("enviado");
        var service=new PreAtendimentoService(db,new ObjectMapper(),"http://localhost:5173");
        var error=assertThrows(ResponseStatusException.class,
                () -> service.anexar(token,session,mock(org.springframework.web.multipart.MultipartFile.class)));
        assertEquals(409,error.getStatusCode().value());
        verify(db,never()).update(contains("INSERT INTO pre_atendimento_anexo"),any(Object[].class));
    }
}
