package br.com.clinica.repository;

import br.com.clinica.model.Agenda;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgendaRepository extends JpaRepository<Agenda, Integer> {

    List<Agenda> findByMedicoId(Integer idMedico);
}
