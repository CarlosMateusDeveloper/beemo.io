package br.com.clinica.repository;

import br.com.clinica.model.Comorbidade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComorbidadeRepository extends JpaRepository<Comorbidade, Integer> {

    List<Comorbidade> findByPacienteId(Integer idPaciente);
}
