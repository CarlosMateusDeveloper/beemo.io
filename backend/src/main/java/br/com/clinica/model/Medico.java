package br.com.clinica.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "medico")
@Getter
@Setter
@NoArgsConstructor
public class Medico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_medico")
    private Integer id;

    @NotBlank
    @Size(max = 100)
    private String nome;

    @NotBlank
    @Size(max = 20)
    @Column(unique = true)
    private String crm;

    @NotBlank
    @Pattern(regexp = "ativo|ferias|afastado|desligado")
    @Column(length = 10)
    private String status = "ativo";

    @DecimalMin("0")
    @DecimalMax("100")
    @Column(name = "repasse_percentual", precision = 5, scale = 2)
    private BigDecimal repassePercentual;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_especialidade", nullable = false)
    private Especialidade especialidade;
}
