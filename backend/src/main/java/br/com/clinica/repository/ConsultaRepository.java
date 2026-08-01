package br.com.clinica.repository;

import br.com.clinica.model.Consulta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConsultaRepository extends JpaRepository<Consulta, Integer> {

    List<Consulta> findByPacienteId(Integer idPaciente);
}
