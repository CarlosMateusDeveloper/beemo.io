package br.com.clinica.repository;

import br.com.clinica.model.AuditoriaItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditoriaItemRepository extends JpaRepository<AuditoriaItem, Integer> {

    List<AuditoriaItem> findByIdAuditoriaAtendimentoOrderByIdAsc(Integer idAuditoriaAtendimento);
}
