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
@Table(name = "paciente")
@Getter
@Setter
@NoArgsConstructor
public class Paciente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_paciente")
    private Integer id;

    @NotBlank
    @Size(max = 100)
    private String nome;

    @NotBlank
    @Size(min = 11, max = 11)
    @Column(length = 11, unique = true)
    private String cpf;

    @NotNull
    @Column(name = "data_nascimento")
    private LocalDate dataNascimento;

    @NotBlank
    @Size(min = 2, max = 2)
    @Column(length = 2)
    private String ddd;

    @NotBlank
    @Size(max = 10)
    private String numero;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_convenio")
    private Convenio convenio;

    @Column(name = "historia_familiar", columnDefinition = "TEXT")
    private String historiaFamiliar;

    @Column(name = "historia_social", columnDefinition = "TEXT")
    private String historiaSocial;
}
