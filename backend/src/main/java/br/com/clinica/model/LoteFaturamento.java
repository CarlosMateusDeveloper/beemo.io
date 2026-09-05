package br.com.clinica.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

// idConvenio como Integer puro (não @ManyToOne) — mesmo padrão de Glosa/Fatura:
// evita LAZY sem JOIN FETCH quebrando a serialização (open-in-view: false).
@Entity
@Table(name = "lote_faturamento")
@Getter
@Setter
@NoArgsConstructor
public class LoteFaturamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_lote")
    private Integer id;

    @NotNull
    @Column(name = "id_convenio", nullable = false)
    private Integer idConvenio;

    @NotNull
    @Column(nullable = false, unique = true)
    private String codigo;

    // Espelha o enum status_lote_faturamento (rascunho/pronto_envio/enviado/
    // processando/pago_parcial/pago/com_glosas) — plain String, mesmo motivo
    // de Glosa.status.
    @Column(nullable = false)
    private String status = "rascunho";

    @Column(name = "data_envio")
    private LocalDate dataEnvio;

    @NotNull
    @Column(name = "valor_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorTotal = BigDecimal.ZERO;

    @Column(name = "criado_em")
    private OffsetDateTime criadoEm;
}
