package br.com.clinica.repository;

import br.com.clinica.model.AutorizacaoConvenio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AutorizacaoConvenioRepository extends JpaRepository<AutorizacaoConvenio, Integer> {

    Optional<AutorizacaoConvenio> findByIdConsulta(Integer idConsulta);

    // status é opcional — filtro só entra quando informado (issue #15:
    // "listagem paginada e filtrável por status").
    @Query("SELECT a FROM AutorizacaoConvenio a WHERE (:status IS NULL OR a.status = :status)")
    Page<AutorizacaoConvenio> buscar(@Param("status") String status, Pageable pageable);
}
