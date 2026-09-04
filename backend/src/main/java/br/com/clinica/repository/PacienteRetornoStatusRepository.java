package br.com.clinica.repository;

import br.com.clinica.model.PacienteRetornoStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PacienteRetornoStatusRepository extends JpaRepository<PacienteRetornoStatus, Integer> {
}
