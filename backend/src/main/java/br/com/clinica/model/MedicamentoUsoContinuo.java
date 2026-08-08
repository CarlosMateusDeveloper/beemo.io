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
@Table(name = "medicamento_uso_continuo")
@Getter
@Setter
@NoArgsConstructor
public class MedicamentoUsoContinuo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_medicamento_uso_continuo")
    private Integer id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_paciente", nullable = false)
    private Paciente paciente;

    @NotBlank
    @Size(max = 150)
    private String medicamento;

    @Size(max = 50)
    private String dosagem;

    @Size(max = 100)
    private String posologia;

    @Column(name = "data_inicio")
    private LocalDate dataInicio;

    private Boolean ativo = true;
}
