package br.com.clinica.repository;

import br.com.clinica.model.Prontuario;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProntuarioRepository extends JpaRepository<Prontuario, Integer> {
}
