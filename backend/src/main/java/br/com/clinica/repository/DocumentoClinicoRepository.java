package br.com.clinica.repository;

import br.com.clinica.model.DocumentoClinico;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentoClinicoRepository extends JpaRepository<DocumentoClinico, Integer> {

    List<DocumentoClinico> findByProntuarioId(Integer idProntuario);
}
