package br.com.clinica.service;

import br.com.clinica.model.Fatura;
import br.com.clinica.model.Pagamento;
import br.com.clinica.repository.FaturaRepository;
import br.com.clinica.repository.PagamentoRepository;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

// Registra pagamento.metodo (enum metodo_pagamento) via SQL nativo com CAST
// explícito — mesma cautela de FaturaEscritaService/CaixaService. Depois de
// gravar, soma todos os pagamentos da fatura: se cobrir o valor, marca a
// fatura como "pago" (issue #14: "atualização de status da fatura...
// refletindo os pagamentos recebidos"). Não mexe em turno_caixa — isso é
// específico do fluxo de /caixa (CaixaService), este endpoint é de uso geral.
@Service
public class PagamentoEscritaService {

    private static final Set<String> METODOS_VALIDOS = Set.of(
            "pix", "cartao_credito", "cartao_debito", "boleto", "convenio", "dinheiro"
    );
    private static final Set<String> STATUS_FINAIS_NAO_SOBRESCREVER = Set.of("cancelado", "estornado");

    private final PagamentoRepository pagamentoRepository;
    private final FaturaRepository faturaRepository;
    private final EntityManager entityManager;

    public PagamentoEscritaService(
            PagamentoRepository pagamentoRepository, FaturaRepository faturaRepository, EntityManager entityManager
    ) {
        this.pagamentoRepository = pagamentoRepository;
        this.faturaRepository = faturaRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public Pagamento registrar(Integer idFatura, Pagamento dados) {
        Fatura fatura = faturaRepository.findById(idFatura)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fatura não encontrada"));
        if (dados.getMetodo() == null || !METODOS_VALIDOS.contains(dados.getMetodo())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "método de pagamento inválido: " + dados.getMetodo());
        }
        if (dados.getValorPago() == null || dados.getValorPago().signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "valorPago deve ser positivo");
        }

        BigDecimal desconto = dados.getDesconto() == null ? BigDecimal.ZERO : dados.getDesconto();

        Integer idPagamento = ((Number) entityManager.createNativeQuery(
                "INSERT INTO pagamento (id_fatura, metodo, valor_pago, referencia_externa, parcelas, desconto, motivo_desconto) " +
                        "VALUES (:idFatura, CAST(:metodo AS metodo_pagamento), :valorPago, :referencia, :parcelas, :desconto, :motivo) " +
                        "RETURNING id_pagamento"
        )
                .setParameter("idFatura", idFatura)
                .setParameter("metodo", dados.getMetodo())
                .setParameter("valorPago", dados.getValorPago())
                .setParameter("referencia", dados.getReferenciaExterna())
                .setParameter("parcelas", dados.getParcelas())
                .setParameter("desconto", desconto)
                .setParameter("motivo", dados.getMotivoDesconto())
                .getSingleResult()).intValue();

        atualizarStatusSeQuitada(fatura);

        entityManager.clear();
        return pagamentoRepository.findById(idPagamento).orElseThrow();
    }

    public List<Pagamento> listar(Integer idFatura) {
        if (!faturaRepository.existsById(idFatura)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Fatura não encontrada");
        }
        return pagamentoRepository.findByIdFaturaOrderByPagoEmDesc(idFatura);
    }

    private void atualizarStatusSeQuitada(Fatura fatura) {
        if (STATUS_FINAIS_NAO_SOBRESCREVER.contains(fatura.getStatus())) return;

        BigDecimal totalPago = pagamentoRepository.findByIdFaturaOrderByPagoEmDesc(fatura.getId()).stream()
                .map(Pagamento::getValorPago)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String novoStatus = totalPago.compareTo(fatura.getValor()) >= 0 ? "pago" : "pendente";
        entityManager.createNativeQuery(
                "UPDATE fatura SET status = CAST(:status AS status_pagamento) WHERE id_fatura = :id"
        ).setParameter("status", novoStatus).setParameter("id", fatura.getId()).executeUpdate();
    }
}
