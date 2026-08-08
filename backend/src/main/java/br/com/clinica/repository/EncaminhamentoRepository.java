package br.com.clinica.repository;

import br.com.clinica.model.Encaminhamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EncaminhamentoRepository extends JpaRepository<Encaminhamento, Integer> {

    List<Encaminhamento> findByProntuarioId(Integer idProntuario);
}
