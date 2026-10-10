package br.com.clinica.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service("accessControl")
public class AccessControlService {
    public boolean has(String permission) {
        var access = TenantContext.get();
        return access != null && access.permissoes().contains(permission);
    }

    public void require(String permission) {
        if (!has(permission)) throw forbidden();
    }

    /** Escopo clínico: médico só enxerga registros associados ao seu cadastro. */
    public Integer clinicalDoctorId() {
        var access = requiredAccess();
        if (!access.papeis().contains(PermissionCatalog.MEDICO)) return null;
        if (access.idMedico() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Seu usuário médico ainda não está vinculado a um profissional da clínica.");
        }
        return access.idMedico();
    }

    /** Papéis operacionais mais amplos retiram apenas o escopo da lista cadastral, nunca o do prontuário. */
    public Integer patientDoctorId() {
        var access = requiredAccess();
        boolean broad = access.papeis().stream().anyMatch(p -> p.equals(PermissionCatalog.RECEPCIONISTA)
                || p.equals(PermissionCatalog.FINANCEIRO) || p.equals(PermissionCatalog.ADMINISTRADOR));
        return broad ? null : clinicalDoctorId();
    }

    public Integer forceDoctor(Integer requested) {
        Integer own = clinicalDoctorId();
        if (own == null) return requested;
        if (requested != null && !requested.equals(own)) throw forbidden();
        return own;
    }

    private TenantService.Access requiredAccess() {
        var access = TenantContext.get();
        if (access == null) throw forbidden();
        return access;
    }

    public static ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "Seu perfil não tem permissão para esta ação.");
    }
}
