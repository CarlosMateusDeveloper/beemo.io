package br.com.clinica.repository;

import br.com.clinica.model.ConvenioProcedimento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ConvenioProcedimentoRepository extends JpaRepository<ConvenioProcedimento, Integer> {

    // JOIN FETCH em convenio/plano — mesmo motivo do padrão já usado em
    // MedicoRepository/PacienteRepository (LAZY sem fetch quebra a
    // serialização com open-in-view: false).
    @Query("SELECT pr FROM ConvenioProcedimento pr LEFT JOIN FETCH pr.plano WHERE pr.convenio.id = :idConvenio ORDER BY pr.descricao")
    List<ConvenioProcedimento> listarPorConvenio(Integer idConvenio);

    long countByConvenioId(Integer idConvenio);
}
