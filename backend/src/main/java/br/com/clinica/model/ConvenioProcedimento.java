package br.com.clinica.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "convenio_procedimento")
@Getter
@Setter
@NoArgsConstructor
public class ConvenioProcedimento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_convenio_procedimento")
    private Integer id;

    // Sem @NotNull: setado pelo service a partir do path (ver ConvenioPlano).
    // @JsonIgnore: convênio já é conhecido pela URL (ver ConvenioPlano).
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_convenio", nullable = false)
    private Convenio convenio;

    // NULL = vale pra todos os planos do convenio.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_plano")
    private ConvenioPlano plano;

    @NotBlank
    @Size(max = 30)
    private String codigo;

    @NotBlank
    @Size(max = 200)
    private String descricao;

    @Column(name = "valor_negociado", precision = 10, scale = 2)
    private BigDecimal valorNegociado;

    private Boolean cobertura = true;

    @Column(name = "exige_autorizacao")
    private Boolean exigeAutorizacao = false;
}
