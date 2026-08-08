package br.com.clinica.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "diagnostico_cid")
@Getter
@Setter
@NoArgsConstructor
public class DiagnosticoCid {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_diagnostico_cid")
    private Integer id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_prontuario", nullable = false)
    private Prontuario prontuario;

    @NotBlank
    @Size(max = 10)
    @Column(name = "codigo_cid")
    private String codigoCid;

    @NotBlank
    @Size(max = 255)
    private String descricao;

    private Boolean principal = false;
}
