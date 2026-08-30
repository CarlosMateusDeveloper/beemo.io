package br.com.clinica.repository;

import br.com.clinica.model.Exame;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExameRepository extends JpaRepository<Exame, Integer> {

    // idPaciente/status são opcionais — filtro só entra quando informado
    // (issue #13: "listagem paginada e filtrável por paciente/status").
    // CAST(e.status AS string): e.status mapeia pra um enum nativo do
    // Postgres (status_exame); comparar direto contra o parâmetro (bindado
    // como varchar) quebra com "operator does not exist" sem
    // stringtype=unspecified na URL JDBC. Cast enum->text é sempre permitido,
    // então comparar como texto dos dois lados evita o problema.
    @Query("SELECT e FROM Exame e WHERE " +
            "(:idPaciente IS NULL OR e.idPaciente = :idPaciente) " +
            "AND (:status IS NULL OR CAST(e.status AS string) = :status)")
    Page<Exame> buscar(@Param("idPaciente") Integer idPaciente, @Param("status") String status, Pageable pageable);
}
