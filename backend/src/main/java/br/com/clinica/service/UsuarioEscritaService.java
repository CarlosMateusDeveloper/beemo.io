package br.com.clinica.service;

import br.com.clinica.model.PerfilUsuario;
import br.com.clinica.model.Usuario;
import br.com.clinica.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

// perfil é enum nativo do Postgres (perfil_usuario) com DEFAULT no banco —
// ao contrário de Alergia/DocumentoClinico/Encaminhamento/SolicitacaoExame,
// não precisa de INSERT inteiro em SQL nativo: o campo fica insertable=false
// na entidade (ver Usuario.java) e um UPDATE de acompanhamento cobre o caso
// em que o valor pedido não é o default.
@Service
public class UsuarioEscritaService {

    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final EntityManager entityManager;

    public UsuarioEscritaService(UsuarioRepository repository, PasswordEncoder passwordEncoder, EntityManager entityManager) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.entityManager = entityManager;
    }

    @Transactional
    public Usuario criar(Usuario usuario) {
        // perfil é insertable=false (ver Usuario.java) — Jackson ainda popula
        // o campo em memória a partir do JSON, só o save() é que ignora.
        String perfilSolicitado = usuario.getPerfil() == null ? null : usuario.getPerfil().name();
        usuario.setSenha(passwordEncoder.encode(usuario.getSenha()));
        Usuario salvo = repository.save(usuario);
        atualizarPerfil(salvo.getId(), perfilSolicitado);
        entityManager.clear();
        return repository.findById(salvo.getId()).orElseThrow();
    }

    private void atualizarPerfil(Integer id, String perfil) {
        if (perfil == null) return;
        try {
            PerfilUsuario.valueOf(perfil);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "perfil inválido: " + perfil);
        }
        entityManager.createNativeQuery(
                "UPDATE usuario SET perfil = CAST(:perfil AS perfil_usuario) WHERE id = :id"
        ).setParameter("perfil", perfil).setParameter("id", id).executeUpdate();
    }
}
