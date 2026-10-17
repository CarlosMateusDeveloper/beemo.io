package br.com.clinica.service;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

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
}
