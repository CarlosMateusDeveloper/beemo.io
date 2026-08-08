package br.com.clinica.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "comorbidade")
@Getter
@Setter
@NoArgsConstructor
public class Comorbidade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_comorbidade")
    private Integer id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_paciente", nullable = false)
    private Paciente paciente;

    @NotBlank
    @Size(max = 150)
    private String descricao;

    @Size(max = 10)
    @Column(name = "codigo_cid")
    private String codigoCid;

    @Column(name = "data_diagnostico")
    private LocalDate dataDiagnostico;

    private Boolean ativo = true;
}
