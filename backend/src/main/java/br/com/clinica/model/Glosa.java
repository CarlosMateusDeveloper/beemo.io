package br.com.clinica.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

// idFatura/idConvenio/idUsuarioResponsavel ficam como Integer puro (não
// @ManyToOne) — mesmo padrão de Fatura/Exame: evita LAZY sem JOIN FETCH
// quebrando a serialização com open-in-view: false.
@Entity
@Table(name = "glosa")
@Getter
@Setter
@NoArgsConstructor
public class Glosa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_glosa")
    private Integer id;

    @NotNull
    @Column(name = "id_fatura", nullable = false)
    private Integer idFatura;

    @NotNull
    @Column(name = "id_convenio", nullable = false)
    private Integer idConvenio;

    @NotBlank
    @Column(nullable = false)
    private String motivo;

    @NotNull
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    @Column(name = "prazo_recurso")
    private LocalDate prazoRecurso;

    // Espelha o enum status_glosa (nova/em_analise/recurso_preparacao/
    // recurso_enviado/recuperada/recuperada_parcialmente/negada/confirmada).
    @Column(nullable = false)
    private String status = "nova";

    @Column(name = "id_usuario_responsavel")
    private Integer idUsuarioResponsavel;

    @Column(name = "criado_em")
    private OffsetDateTime criadoEm;

    @NotNull
    @Column(name = "data_glosa", nullable = false)
    private LocalDate dataGlosa;

    @Column(name = "valor_faturado", precision = 10, scale = 2)
    private BigDecimal valorFaturado;

    @Column(name = "codigo_motivo")
    private String codigoMotivo;

    // Espelha o enum? não — CHECK varchar (importada/manual), mesmo motivo
    // de Medico.status: evita mais um tipo nativo do Postgres pra um campo
    // de 2 valores.
    @Column(nullable = false)
    private String origem = "manual";

    // recorrivel / nao_recorrivel / necessita_analise — nulo até classificar.
    private String recorribilidade;

    // autorizacao / documentacao / codigo_procedimento / elegibilidade /
    // cobertura / cobranca / prazo / duplicidade / outros — nulo até classificar.
    @Column(name = "categoria_motivo")
    private String categoriaMotivo;
}
