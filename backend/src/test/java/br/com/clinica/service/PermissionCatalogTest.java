package br.com.clinica.service;

import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PermissionCatalogTest {
    @AfterEach void clear() { TenantContext.clear(); }

    @Test void papeisDoMvpSaoIndependentesECumulativos() {
        assertEquals(Set.of("recepcionista", "medico", "financeiro", "administrador"),
                Set.copyOf(PermissionCatalog.roles()));
        var reception = PermissionCatalog.defaultsFor(Set.of("recepcionista"));
        assertTrue(reception.contains("agenda.gerenciar"));
        assertFalse(reception.contains("prontuario.visualizar"));
        assertFalse(reception.contains("financeiro.dre.visualizar"));

        var combined = PermissionCatalog.defaultsFor(Set.of("recepcionista", "financeiro"));
        assertTrue(combined.contains("agenda.gerenciar"));
        assertTrue(combined.contains("financeiro.dre.visualizar"));
        assertFalse(combined.contains("glosa.aceitar_perda"));

        var financial = PermissionCatalog.defaultsFor(Set.of("financeiro"));
        assertTrue(financial.contains("paciente.financeiro.visualizar"));
        assertFalse(financial.contains("paciente.visualizar"));
        assertFalse(financial.contains("prontuario.visualizar"));
    }

    @Test void administradorNaoRecebeProntuarioAutomaticamente() {
        var permissions = PermissionCatalog.defaultsFor(Set.of("administrador"));
        assertTrue(permissions.contains("permissao.gerenciar"));
        assertTrue(permissions.contains("glosa.aceitar_perda"));
        assertFalse(permissions.contains("prontuario.visualizar"));
        assertFalse(permissions.contains("prontuario.editar"));
    }

    @Test void medicoSemVinculoFalhaFechado() {
        var permissions = PermissionCatalog.defaultsFor(Set.of("medico"));
        TenantContext.set(new TenantService.Access(1, "Clínica", "medico",
                java.util.List.of("medico"), permissions, null));
        var access = new AccessControlService();
        var error = assertThrows(org.springframework.web.server.ResponseStatusException.class, access::clinicalDoctorId);
        assertEquals(403, error.getStatusCode().value());
    }
}
