package br.com.clinica.service;

import br.com.clinica.dto.LoteListagemItemDto;
import br.com.clinica.model.LoteFaturamento;
import br.com.clinica.repository.LoteFaturamentoRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class LoteListagemService {

    private static final DateTimeFormatter DIA_MES_ANO = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    // Só faz sentido falar em "divergência" (pagamento a menor sem glosa)
    // depois que o lote já devia estar liquidado.
    private static final Set<String> STATUS_LIQUIDAVEIS = Set.of("processando", "pago", "pago_parcial", "com_glosas");

    private final LoteFaturamentoRepository repository;
    private final EntityManager entityManager;

    public LoteListagemService(LoteFaturamentoRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    public Page<LoteListagemItemDto> listar(String status, Integer idConvenio, Pageable pageable) {
        Page<LoteFaturamento> pagina = repository.buscar(status, idConvenio, pageable);
        Map<Integer, Enriquecimento> enriquecimento = buscarEnriquecimento(pagina.getContent());

        return pagina.map(l -> {
            Enriquecimento e = enriquecimento.getOrDefault(l.getId(), new Enriquecimento());
            boolean divergencia = STATUS_LIQUIDAVEIS.contains(l.getStatus())
                    && e.valorPago.add(e.valorGlosado).compareTo(l.getValorTotal()) < 0;
            return new LoteListagemItemDto(
                    l.getId(), l.getCodigo(), e.convenioNome, l.getStatus(), e.quantidadeItens,
                    l.getValorTotal(), e.valorPago, e.valorGlosado,
                    l.getDataEnvio() == null ? "—" : l.getDataEnvio().format(DIA_MES_ANO),
                    l.getCriadoEm().toLocalDate().format(DIA_MES_ANO),
                    divergencia
            );
        });
    }

    @SuppressWarnings("unchecked")
    private Map<Integer, Enriquecimento> buscarEnriquecimento(List<LoteFaturamento> lotes) {
        Map<Integer, Enriquecimento> resultado = new HashMap<>();
        if (lotes.isEmpty()) return resultado;
        List<Integer> ids = lotes.stream().map(LoteFaturamento::getId).toList();

        Query query = entityManager.createNativeQuery(
                "SELECT l.id_lote, cv.nome, COUNT(DISTINCT li.id_lote_item), " +
                        "  COALESCE(SUM(pago_item.valor_pago), 0), COALESCE(SUM(glosado_item.valor_glosa), 0) " +
                        "FROM lote_faturamento l " +
                        "JOIN convenio cv ON cv.id_convenio = l.id_convenio " +
                        "LEFT JOIN lote_item li ON li.id_lote = l.id_lote " +
                        "LEFT JOIN LATERAL (SELECT COALESCE(SUM(pg.valor_pago), 0) AS valor_pago FROM pagamento pg WHERE pg.id_fatura = li.id_fatura) pago_item ON true " +
                        "LEFT JOIN LATERAL (SELECT COALESCE(SUM(g.valor), 0) AS valor_glosa FROM glosa g WHERE g.id_fatura = li.id_fatura) glosado_item ON true " +
                        "WHERE l.id_lote IN :ids " +
                        "GROUP BY l.id_lote, cv.nome"
        );
        query.setParameter("ids", ids);
        for (Object[] l : (List<Object[]>) query.getResultList()) {
            Enriquecimento e = new Enriquecimento();
            e.convenioNome = (String) l[1];
            e.quantidadeItens = ((Number) l[2]).intValue();
            e.valorPago = (BigDecimal) l[3];
            e.valorGlosado = (BigDecimal) l[4];
            resultado.put(((Number) l[0]).intValue(), e);
        }
        return resultado;
    }

    private static class Enriquecimento {
        String convenioNome;
        int quantidadeItens;
        BigDecimal valorPago = BigDecimal.ZERO;
        BigDecimal valorGlosado = BigDecimal.ZERO;
    }
}
