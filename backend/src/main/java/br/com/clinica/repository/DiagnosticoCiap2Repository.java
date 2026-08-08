package br.com.clinica.repository;

import br.com.clinica.model.DiagnosticoCiap2;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DiagnosticoCiap2Repository extends JpaRepository<DiagnosticoCiap2, Integer> {

    List<DiagnosticoCiap2> findByProntuarioId(Integer idProntuario);
}
