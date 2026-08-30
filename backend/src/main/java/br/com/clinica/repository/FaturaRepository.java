package br.com.clinica.repository;

import br.com.clinica.model.Fatura;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface FaturaRepository extends JpaRepository<Fatura, Integer> {

    Optional<Fatura> findByIdConsulta(Integer idConsulta);

    // status/inicio/fim são opcionais — filtro só entra quando o parâmetro
    // não é nulo (issue #14: "listagem paginada e filtrável por status/período").
    // CAST(f.status AS string): f.status é enum nativo do Postgres
    // (status_fatura); comparar direto quebra sem stringtype=unspecified na
    // URL JDBC — cast pra texto dos dois lados evita o problema.
    @Query("SELECT f FROM Fatura f WHERE " +
            "(:status IS NULL OR CAST(f.status AS string) = :status) " +
            "AND (:inicio IS NULL OR f.vencimento >= :inicio) " +
            "AND (:fim IS NULL OR f.vencimento <= :fim)")
    Page<Fatura> buscar(
            @Param("status") String status, @Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim,
            Pageable pageable
    );
}
