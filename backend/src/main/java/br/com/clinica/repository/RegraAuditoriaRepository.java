package br.com.clinica.repository;

import br.com.clinica.model.RegraAuditoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface RegraAuditoriaRepository extends JpaRepository<RegraAuditoria, Integer> {

    @Query("SELECT r FROM RegraAuditoria r LEFT JOIN FETCH r.procedimento WHERE r.convenio.id = :idConvenio ORDER BY r.severidade, r.descricao")
    List<RegraAuditoria> listarPorConvenio(Integer idConvenio);

    long countByConvenioId(Integer idConvenio);
}
