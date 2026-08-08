package br.com.clinica.repository;

import br.com.clinica.model.SolicitacaoExame;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SolicitacaoExameRepository extends JpaRepository<SolicitacaoExame, Integer> {

    List<SolicitacaoExame> findByProntuarioId(Integer idProntuario);
}
