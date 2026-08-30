package br.com.clinica.repository;

import br.com.clinica.model.ConvenioPlano;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ConvenioPlanoRepository extends JpaRepository<ConvenioPlano, Integer> {

    @Query("SELECT p FROM ConvenioPlano p WHERE p.convenio.id = :idConvenio ORDER BY p.nome")
    List<ConvenioPlano> listarPorConvenio(Integer idConvenio);
}
