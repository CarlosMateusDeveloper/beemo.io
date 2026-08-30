package br.com.clinica.repository;

import br.com.clinica.model.DocumentoObrigatorioConvenio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DocumentoObrigatorioConvenioRepository extends JpaRepository<DocumentoObrigatorioConvenio, Integer> {

    @Query("SELECT d FROM DocumentoObrigatorioConvenio d LEFT JOIN FETCH d.procedimento WHERE d.convenio.id = :idConvenio ORDER BY d.nomeDocumento")
    List<DocumentoObrigatorioConvenio> listarPorConvenio(Integer idConvenio);
}
