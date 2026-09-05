package br.com.clinica.service;

import br.com.clinica.dto.LoteDetalheDto;
import br.com.clinica.model.LoteFaturamento;
import br.com.clinica.repository.LoteFaturamentoRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

// Detalhe do lote — conciliação item a item (docs/specs/convenios.md, Aba 4:
// "comparação entre valor enviado e valor pago por lote, para pegar
// pagamento a menor que não veio acompanhado de glosa formal").
@Service
public class LoteDetalheService {

    private static final DateTimeFormatter DIA_MES_ANO = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final LoteFaturamentoRepository repository;
    private final EntityManager entityManager;

    public LoteDetalheService(LoteFaturamentoRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @SuppressWarnings("unchecked")
    public LoteDetalheDto detalhar(Integer id) {
        LoteFaturamento lote = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lote não encontrado"));

        Query query = entityManager.createNativeQuery(
                "SELECT f.id_fatura, p.nome, a.data_slot, c.tipo::text, f.valor, f.status, " +
                        "  COALESCE((SELECT SUM(pg.valor_pago) FROM pagamento pg WHERE pg.id_fatura = f.id_fatura), 0), " +
                        "  COALESCE((SELECT SUM(g.valor) FROM glosa g WHERE g.id_fatura = f.id_fatura), 0) " +
                        "FROM lote_item li " +
                        "JOIN fatura f ON f.id_fatura = li.id_fatura " +
                        "JOIN consulta c ON c.id_consulta = f.id_consulta " +
                        "JOIN agenda a ON a.id_agenda = c.id_agenda " +
                        "JOIN paciente p ON p.id_paciente = c.id_paciente " +
                        "WHERE li.id_lote = :idLote " +
                        "ORDER BY a.data_slot ASC"
        );
        query.setParameter("idLote", id);

        List<LoteDetalheDto.ItemDto> itens = new ArrayList<>();
        BigDecimal valorPagoTotal = BigDecimal.ZERO;
        BigDecimal valorGlosadoTotal = BigDecimal.ZERO;
        for (Object[] l : (List<Object[]>) query.getResultList()) {
            BigDecimal valorPago = (BigDecimal) l[6];
            BigDecimal valorGlosado = (BigDecimal) l[7];
            valorPagoTotal = valorPagoTotal.add(valorPago);
            valorGlosadoTotal = valorGlosadoTotal.add(valorGlosado);
            itens.add(new LoteDetalheDto.ItemDto(
                    ((Number) l[0]).intValue(), (String) l[1], paraLocalDate(l[2]).format(DIA_MES_ANO), (String) l[3],
                    (BigDecimal) l[4], valorPago, valorGlosado, (String) l[5]
            ));
        }

        Query convenioQuery = entityManager.createNativeQuery(
                "SELECT nome FROM convenio WHERE id_convenio = :id"
        );
        convenioQuery.setParameter("id", lote.getIdConvenio());
        String convenioNome = (String) convenioQuery.getSingleResult();

        BigDecimal divergente = lote.getValorTotal().subtract(valorPagoTotal).subtract(valorGlosadoTotal);

        return new LoteDetalheDto(
                lote.getId(), lote.getCodigo(), lote.getIdConvenio(), convenioNome, lote.getStatus(),
                lote.getDataEnvio() == null ? "—" : lote.getDataEnvio().format(DIA_MES_ANO),
                lote.getCriadoEm().toLocalDate().format(DIA_MES_ANO),
                lote.getValorTotal(), valorPagoTotal, valorGlosadoTotal, divergente,
                itens
        );
    }

    private LocalDate paraLocalDate(Object valor) {
        if (valor instanceof LocalDate localDate) return localDate;
        if (valor instanceof java.sql.Date sqlDate) return sqlDate.toLocalDate();
        if (valor instanceof java.sql.Timestamp timestamp) return timestamp.toLocalDateTime().toLocalDate();
        if (valor instanceof java.time.Instant instant) return instant.atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        throw new IllegalStateException("Tipo de data inesperado: " + valor.getClass());
    }
}
