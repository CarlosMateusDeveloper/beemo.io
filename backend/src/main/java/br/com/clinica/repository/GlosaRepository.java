package br.com.clinica.repository;

import br.com.clinica.model.Glosa;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface GlosaRepository extends JpaRepository<Glosa, Integer> {

    // status/idConvenio/idUsuarioResponsavel/recorribilidade/prazoAte são
    // opcionais — filtro só entra quando informado (spec seção 3-5:
    // fila de glosas por prazo/status/responsável/convênio). CAST(g.status
    // AS string): g.status é enum nativo do Postgres (status_glosa); comparar
    // direto quebra sem stringtype=unspecified na URL JDBC. recorribilidade
    // não precisa do cast — é VARCHAR+CHECK, não enum nativo.
    @Query("SELECT g FROM Glosa g WHERE " +
            "(:status IS NULL OR CAST(g.status AS string) = :status) " +
            "AND (:idConvenio IS NULL OR g.idConvenio = :idConvenio) " +
            "AND (:idUsuarioResponsavel IS NULL OR g.idUsuarioResponsavel = :idUsuarioResponsavel) " +
            "AND (:recorribilidade IS NULL OR g.recorribilidade = :recorribilidade) " +
            "AND (:prazoAte IS NULL OR g.prazoRecurso <= :prazoAte)")
    Page<Glosa> buscar(
            @Param("status") String status, @Param("idConvenio") Integer idConvenio,
            @Param("idUsuarioResponsavel") Integer idUsuarioResponsavel,
            @Param("recorribilidade") String recorribilidade, @Param("prazoAte") LocalDate prazoAte,
            Pageable pageable
    );
}
