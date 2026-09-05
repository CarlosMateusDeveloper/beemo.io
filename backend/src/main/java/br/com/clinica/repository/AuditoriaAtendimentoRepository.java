package br.com.clinica.repository;

import br.com.clinica.model.AuditoriaAtendimento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AuditoriaAtendimentoRepository extends JpaRepository<AuditoriaAtendimento, Integer> {

    Optional<AuditoriaAtendimento> findByIdConsulta(Integer idConsulta);

    // status é opcional — filtro só entra quando informado. CAST(a.status AS
    // string): a.status é enum nativo do Postgres (status_auditoria_atendimento).
    @Query("SELECT a FROM AuditoriaAtendimento a WHERE " +
            "(:status IS NULL OR CAST(a.status AS string) = :status)")
    Page<AuditoriaAtendimento> buscar(@Param("status") String status, Pageable pageable);
}
