package br.com.clinica.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "prontuario")
@Getter
@Setter
@NoArgsConstructor
public class Prontuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_prontuario")
    private Integer id;

    @NotNull
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_consulta", nullable = false, unique = true)
    private Consulta consulta;

    @NotBlank
    @Column(columnDefinition = "TEXT")
    private String descricao;

    @NotBlank
    @Column(columnDefinition = "TEXT")
    private String prescricao;

    @NotBlank
    @Column(columnDefinition = "TEXT")
    private String diagnostico;
}
