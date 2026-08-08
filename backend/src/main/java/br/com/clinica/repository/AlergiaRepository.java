package br.com.clinica.repository;

import br.com.clinica.model.Alergia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlergiaRepository extends JpaRepository<Alergia, Integer> {

    List<Alergia> findByPacienteId(Integer idPaciente);
}
