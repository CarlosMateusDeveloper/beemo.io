package br.com.clinica.service;

import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SupportAccessPolicyTest {
    @Test void leituraOperacionalNaoConcedeAlteracaoOuProntuario() {
        var permissions=SupportAccessPolicy.permissions(Set.of(SupportAccessPolicy.LEITURA_OPERACIONAL));
        assertTrue(permissions.contains("agenda.visualizar"));
        assertTrue(permissions.contains("suporte.chamado.visualizar"));
        assertFalse(permissions.contains("agenda.gerenciar"));
        assertFalse(permissions.contains("paciente.editar"));
        assertFalse(permissions.contains("prontuario.visualizar"));
    }

    @Test void escoposSensiveisSaoSeparadosEExplicitos() {
        var financial=SupportAccessPolicy.permissions(Set.of(SupportAccessPolicy.LEITURA_FINANCEIRA));
        assertTrue(financial.contains("caixa.visualizar"));
        assertFalse(financial.contains("caixa.receber"));
        assertFalse(financial.contains("convenio.gerenciar"));
        assertFalse(financial.contains("suporte.exportar"));
        assertTrue(SupportAccessPolicy.permissions(Set.of(SupportAccessPolicy.EXPORTACAO)).contains("suporte.exportar"));
        assertFalse(SupportAccessPolicy.valid("ACESSO_TOTAL"));
    }
}
