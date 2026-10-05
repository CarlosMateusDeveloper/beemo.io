package br.com.clinica.repository;

import br.com.clinica.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {

    @org.springframework.data.jpa.repository.Query(value="SELECT u.* FROM usuario u JOIN auth_email_identity e ON e.id_usuario=u.id WHERE e.email=lower(:email)",nativeQuery=true)
    Optional<Usuario> findByEmailIgnoreCase(@org.springframework.data.repository.query.Param("email") String email);
}
