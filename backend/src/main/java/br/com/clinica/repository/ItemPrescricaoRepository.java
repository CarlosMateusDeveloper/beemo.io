package br.com.clinica.repository;

import br.com.clinica.model.ItemPrescricao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ItemPrescricaoRepository extends JpaRepository<ItemPrescricao, Integer> {

    List<ItemPrescricao> findByProntuarioId(Integer idProntuario);
}
