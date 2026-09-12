package br.com.clinica.repository;

import br.com.clinica.model.Despesa;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface DespesaRepository extends JpaRepository<Despesa, Integer> {

    // status/categoria são VARCHAR simples (não enum nativo) — não é o bug
    // de enum nativo de Fatura/Glosa/AutorizacaoConvenio. O CAST aqui é por
    // outro motivo: com todos os filtros nulos (tela sem nenhum filtro
    // ativo, o caso mais comum), o Postgres não consegue inferir o tipo do
    // parâmetro só de "? IS NULL" sem contexto — CAST explícito no primeiro
    // uso de cada parâmetro resolve isso independente do valor.
    @Query("SELECT d FROM Despesa d WHERE " +
            "(CAST(:status AS string) IS NULL OR d.status = :status) " +
            "AND (CAST(:categoria AS string) IS NULL OR d.categoria = :categoria) " +
            "AND (CAST(:vencimentoDe AS date) IS NULL OR d.vencimento >= :vencimentoDe) " +
            "AND (CAST(:vencimentoAte AS date) IS NULL OR d.vencimento <= :vencimentoAte)")
    Page<Despesa> buscar(
            @Param("status") String status, @Param("categoria") String categoria,
            @Param("vencimentoDe") LocalDate vencimentoDe, @Param("vencimentoAte") LocalDate vencimentoAte,
            Pageable pageable
    );
}
