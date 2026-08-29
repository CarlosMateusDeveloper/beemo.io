package br.com.clinica.repository;

import br.com.clinica.model.ResultadoExame;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ResultadoExameRepository extends JpaRepository<ResultadoExame, Integer> {

    Optional<ResultadoExame> findByIdExame(Integer idExame);
}
