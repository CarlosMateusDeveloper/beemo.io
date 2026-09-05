package br.com.clinica.repository;

import br.com.clinica.model.LoteFaturamento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoteFaturamentoRepository extends JpaRepository<LoteFaturamento, Integer> {

    @Query("SELECT l FROM LoteFaturamento l " +
            "WHERE (:status IS NULL OR l.status = :status) " +
            "AND (:idConvenio IS NULL OR l.idConvenio = :idConvenio) " +
            "ORDER BY l.criadoEm DESC")
    Page<LoteFaturamento> buscar(@Param("status") String status, @Param("idConvenio") Integer idConvenio, Pageable pageable);
}
