package br.com.clinica.repository;

import br.com.clinica.model.GlosaHistorico;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GlosaHistoricoRepository extends JpaRepository<GlosaHistorico, Integer> {

    List<GlosaHistorico> findByIdGlosaOrderByCriadoEmAsc(Integer idGlosa);
}
