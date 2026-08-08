package br.com.clinica.repository;

import br.com.clinica.model.MedicamentoUsoContinuo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MedicamentoUsoContinuoRepository extends JpaRepository<MedicamentoUsoContinuo, Integer> {

    List<MedicamentoUsoContinuo> findByPacienteId(Integer idPaciente);
}
