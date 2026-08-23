package br.com.clinica.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "fatura")
@Getter
@Setter
@NoArgsConstructor
public class Fatura {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_fatura")
    private Integer id;

    @NotNull
    @Column(name = "id_consulta", nullable = false, unique = true)
    private Integer idConsulta;

    @NotNull
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    // Espelha o enum status_pagamento do schema (pendente/pago/atrasado/cancelado/estornado).
    @NotNull
    @Column(nullable = false)
    private String status = "pendente";

    @NotNull
    @Column(nullable = false)
    private LocalDate vencimento;

    @Column(name = "criado_em")
    private OffsetDateTime criadoEm;
}
