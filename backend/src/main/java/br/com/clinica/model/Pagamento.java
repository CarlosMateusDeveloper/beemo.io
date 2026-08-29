package br.com.clinica.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

// idFatura fica como Integer puro (não @ManyToOne) de propósito — mesmo
// padrão de Fatura.idConsulta: evita LAZY sem JOIN FETCH quebrando a
// serialização com open-in-view: false (ver ProntuarioDetalheService).
// Só leitura passa por esta entidade; o INSERT em PagamentoEscritaService
// usa SQL nativo com CAST pro enum metodo_pagamento — repository.save()
// bindaria "metodo" como varchar e o Postgres rejeitaria (sem
// stringtype=unspecified na URL JDBC), igual ao motivo de Medico.status
// ter virado VARCHAR+CHECK em vez de enum nativo.
@Entity
@Table(name = "pagamento")
@Getter
@Setter
@NoArgsConstructor
public class Pagamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_pagamento")
    private Integer id;

    // Sem @NotNull de propósito: vem do path (/api/faturas/{idFatura}/pagamentos),
    // não do corpo do POST — PagamentoEscritaService.registrar() é quem define
    // esse valor, então @Valid não deve exigi-lo no JSON de entrada.
    @Column(name = "id_fatura", nullable = false)
    private Integer idFatura;

    // Espelha o enum metodo_pagamento (pix/cartao_credito/cartao_debito/boleto/convenio/dinheiro).
    @NotBlank
    @Column(nullable = false)
    private String metodo;

    @NotNull
    @Column(name = "valor_pago", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorPago;

    @Column(name = "pago_em")
    private OffsetDateTime pagoEm;

    @Column(name = "referencia_externa")
    private String referenciaExterna;

    private Integer parcelas;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal desconto = BigDecimal.ZERO;

    @Column(name = "motivo_desconto")
    private String motivoDesconto;

    @Column(name = "id_turno_caixa")
    private Integer idTurnoCaixa;
}
