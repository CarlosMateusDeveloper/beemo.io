package br.com.clinica.repository;

import br.com.clinica.model.RecursoGlosa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RecursoGlosaRepository extends JpaRepository<RecursoGlosa, Integer> {

    // Mais de um recurso é possível por glosa (ex.: negado e recorrido de
    // novo) — o "atual" é o mais recente.
    List<RecursoGlosa> findByIdGlosaOrderByCriadoEmDesc(Integer idGlosa);

    Optional<RecursoGlosa> findFirstByIdGlosaOrderByCriadoEmDesc(Integer idGlosa);
}
