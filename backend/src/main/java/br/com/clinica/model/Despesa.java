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

// status/categoria ficam String puro (VARCHAR + CHECK no banco, não enum
// nativo do Postgres) — mesmo padrão de Glosa.status/Medico.status, evita
// de propósito o bug de ddl-auto do Hibernate que já pegou recurso_glosa e
// regra_auditoria nesta mesma leva. Sem @ManyToOne pra clinica (id_clinica
// fica placeholder fixo em DespesaService, sem sessão/multi-tenant real
// ainda) — mesmo motivo de Fatura/Glosa: evita LAZY sem JOIN FETCH.
@Entity
@Table(name = "despesa")
@Getter
@Setter
@NoArgsConstructor
public class Despesa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_despesa")
    private Integer id;

    @Column(name = "id_clinica", nullable = false)
    private Integer idClinica;

    @NotBlank
    @Column(nullable = false)
    private String descricao;

    // aluguel / folha / fornecedores / insumos / impostos / marketing / manutencao / servicos / outros.
    @NotBlank
    @Column(nullable = false)
    private String categoria;

    @NotNull
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    @NotNull
    @Column(nullable = false)
    private LocalDate vencimento;

    @Column(name = "pago_em")
    private LocalDate pagoEm;

    // pendente / pago / cancelado — "atrasado" é calculado na leitura, não armazenado.
    @Column(nullable = false)
    private String status = "pendente";

    private String fornecedor;

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    @Column(name = "criado_em")
    private OffsetDateTime criadoEm;
}
