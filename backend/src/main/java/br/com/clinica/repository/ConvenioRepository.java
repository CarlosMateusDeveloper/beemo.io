package br.com.clinica.repository;

import br.com.clinica.model.Convenio;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConvenioRepository extends JpaRepository<Convenio, Integer> {
}
