package br.com.clinica.repository;

import br.com.clinica.model.Fatura;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FaturaRepository extends JpaRepository<Fatura, Integer> {
}
