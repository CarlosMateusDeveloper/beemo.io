package br.com.clinica.repository;

import br.com.clinica.model.EnvioRetorno;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface EnvioRetornoRepository extends JpaRepository<EnvioRetorno, Integer> {

    @Query("SELECT COUNT(e) FROM EnvioRetorno e WHERE e.paciente.id = :idPaciente AND e.criadoEm >= :desde")
    long contarDesde(@Param("idPaciente") Integer idPaciente, @Param("desde") OffsetDateTime desde);

    @Query("SELECT e FROM EnvioRetorno e WHERE e.criadoEm >= :desde ORDER BY e.criadoEm DESC")
    List<EnvioRetorno> listarDesde(@Param("desde") OffsetDateTime desde);
}
