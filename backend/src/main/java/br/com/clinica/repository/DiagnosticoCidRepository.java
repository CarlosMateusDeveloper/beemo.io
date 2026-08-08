package br.com.clinica.repository;

import br.com.clinica.model.DiagnosticoCid;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DiagnosticoCidRepository extends JpaRepository<DiagnosticoCid, Integer> {

    List<DiagnosticoCid> findByProntuarioId(Integer idProntuario);
}
