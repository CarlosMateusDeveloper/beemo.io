package br.com.clinica.repository;

import br.com.clinica.model.GrupoRetorno;
import br.com.clinica.model.ReguaRetorno;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReguaRetornoRepository extends JpaRepository<ReguaRetorno, Integer> {
    List<ReguaRetorno> findAllByOrderByGrupo();

    Optional<ReguaRetorno> findByGrupo(GrupoRetorno grupo);
}
