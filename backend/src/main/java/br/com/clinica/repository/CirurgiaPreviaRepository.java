package br.com.clinica.repository;

import br.com.clinica.model.CirurgiaPrevia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CirurgiaPreviaRepository extends JpaRepository<CirurgiaPrevia, Integer> {

    List<CirurgiaPrevia> findByPacienteId(Integer idPaciente);
}
