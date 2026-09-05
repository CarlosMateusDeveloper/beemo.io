package br.com.clinica.repository;

import br.com.clinica.model.LoteItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LoteItemRepository extends JpaRepository<LoteItem, Integer> {
    List<LoteItem> findByIdLote(Integer idLote);
}
