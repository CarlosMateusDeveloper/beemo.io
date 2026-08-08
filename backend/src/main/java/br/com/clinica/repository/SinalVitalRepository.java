package br.com.clinica.repository;

import br.com.clinica.model.SinalVital;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SinalVitalRepository extends JpaRepository<SinalVital, Integer> {

    List<SinalVital> findByConsultaId(Integer idConsulta);
}
