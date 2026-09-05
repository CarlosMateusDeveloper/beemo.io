package br.com.clinica.service;

import br.com.clinica.dto.LoteCriarRequest;
import br.com.clinica.model.LoteFaturamento;
import br.com.clinica.model.LoteItem;
import br.com.clinica.repository.LoteFaturamentoRepository;
import br.com.clinica.repository.LoteItemRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Passo 3 do wizard (criar) + progressão de status do lote depois de criado
// (docs/specs/convenios.md, Aba 4: "Rascunho / Pronto para envio / Enviado /
// Processando / Pago parcialmente / Pago / Com glosas").
@Service
public class LoteEscritaService {

    // Transições válidas — evita, por exemplo, voltar um lote "Pago" pra
    // "Rascunho" sem querer. Terminal (pago/com_glosas) não avança mais por
    // aqui; reabrir um lote fechado não é um caso previsto na spec.
    private static final Map<String, Set<String>> TRANSICOES = Map.of(
            "rascunho", Set.of("pronto_envio"),
            "pronto_envio", Set.of("rascunho", "enviado"),
            "enviado", Set.of("processando"),
            "processando", Set.of("pago", "pago_parcial", "com_glosas")
    );

    private final LoteFaturamentoRepository loteRepository;
    private final LoteItemRepository itemRepository;
    private final EntityManager entityManager;

    public LoteEscritaService(LoteFaturamentoRepository loteRepository, LoteItemRepository itemRepository, EntityManager entityManager) {
        this.loteRepository = loteRepository;
        this.itemRepository = itemRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public LoteFaturamento criar(LoteCriarRequest request) {
        if (request.idsFatura() == null || request.idsFatura().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione ao menos um atendimento para o lote");
        }

        BigDecimal valorTotal = somarValorFaturas(request.idsFatura());

        LoteFaturamento lote = new LoteFaturamento();
        lote.setIdConvenio(request.idConvenio());
        lote.setValorTotal(valorTotal);
        lote.setCriadoEm(OffsetDateTime.now());
        // Código provisório só pra satisfazer a UNIQUE NOT NULL até ter o id
        // gerado — vira "LOTE-00042" logo em seguida.
        lote.setCodigo("LOTE-TMP-" + System.nanoTime());
        lote = loteRepository.save(lote);

        lote.setCodigo(String.format("LOTE-%05d", lote.getId()));
        lote = loteRepository.save(lote);

        for (Integer idFatura : request.idsFatura()) {
            LoteItem item = new LoteItem();
            item.setIdLote(lote.getId());
            item.setIdFatura(idFatura);
            itemRepository.save(item);
        }

        return lote;
    }

    @Transactional
    public LoteFaturamento atualizarStatus(Integer id, String novoStatus) {
        LoteFaturamento lote = loteRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lote não encontrado"));

        Set<String> permitidos = TRANSICOES.getOrDefault(lote.getStatus(), Set.of());
        if (!permitidos.contains(novoStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Não é possível mudar de \"" + lote.getStatus() + "\" para \"" + novoStatus + "\"");
        }

        lote.setStatus(novoStatus);
        if ("enviado".equals(novoStatus)) {
            lote.setDataEnvio(LocalDate.now());
        }
        return loteRepository.save(lote);
    }

    @SuppressWarnings("unchecked")
    private BigDecimal somarValorFaturas(List<Integer> idsFatura) {
        Query query = entityManager.createNativeQuery(
                "SELECT f.id_fatura, f.valor FROM fatura f WHERE f.id_fatura IN :ids " +
                        "  AND f.status = 'pendente' " +
                        "  AND NOT EXISTS (SELECT 1 FROM lote_item li WHERE li.id_fatura = f.id_fatura)"
        );
        query.setParameter("ids", idsFatura);
        List<Object[]> linhas = query.getResultList();
        if (linhas.size() != idsFatura.size()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Um ou mais atendimentos selecionados já não estão mais elegíveis (já faturados, cancelados ou já em outro lote)");
        }
        return linhas.stream().map(l -> (BigDecimal) l[1]).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
