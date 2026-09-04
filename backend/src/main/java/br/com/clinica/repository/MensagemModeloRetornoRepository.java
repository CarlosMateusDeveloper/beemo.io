package br.com.clinica.repository;

import br.com.clinica.model.GrupoRetorno;
import br.com.clinica.model.MensagemModeloRetorno;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MensagemModeloRetornoRepository extends JpaRepository<MensagemModeloRetorno, Integer> {
    Optional<MensagemModeloRetorno> findByGrupo(GrupoRetorno grupo);
}
