package br.com.clinica.repository;

import br.com.clinica.model.Pagamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PagamentoRepository extends JpaRepository<Pagamento, Integer> {

    List<Pagamento> findByIdFaturaOrderByPagoEmDesc(Integer idFatura);
}
