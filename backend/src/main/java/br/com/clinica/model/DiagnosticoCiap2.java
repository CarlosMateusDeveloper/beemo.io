package br.com.clinica.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "diagnostico_ciap2")
@Getter
@Setter
@NoArgsConstructor
public class DiagnosticoCiap2 {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_diagnostico_ciap2")
    private Integer id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_prontuario", nullable = false)
    private Prontuario prontuario;

    @NotBlank
    @Size(max = 10)
    @Column(name = "codigo_ciap2")
    private String codigoCiap2;

    @NotBlank
    @Size(max = 255)
    private String descricao;

    private Boolean principal = false;
}
