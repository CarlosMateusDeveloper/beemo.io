package br.com.clinica.repository;

import br.com.clinica.model.RecursoGlosaDocumento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecursoGlosaDocumentoRepository extends JpaRepository<RecursoGlosaDocumento, Integer> {

    List<RecursoGlosaDocumento> findByIdRecurso(Integer idRecurso);
}
