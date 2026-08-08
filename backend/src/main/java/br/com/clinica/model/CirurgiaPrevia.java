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
@Table(name = "cirurgia_previa")
@Getter
@Setter
@NoArgsConstructor
public class CirurgiaPrevia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cirurgia_previa")
    private Integer id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_paciente", nullable = false)
    private Paciente paciente;

    @NotBlank
    @Size(max = 150)
    private String descricao;

    @Column(name = "data_cirurgia")
    private LocalDate dataCirurgia;

    @Column(columnDefinition = "TEXT")
    private String observacao;
}
