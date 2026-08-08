package br.com.clinica.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "alergia")
@Getter
@Setter
@NoArgsConstructor
public class Alergia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_alergia")
    private Integer id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_paciente", nullable = false)
    private Paciente paciente;

    @NotNull
    @Enumerated(EnumType.STRING)
    private TipoAlergia tipo;

    @NotBlank
    @Size(max = 150)
    private String substancia;

    @NotNull
    @Enumerated(EnumType.STRING)
    private GravidadeAlergia gravidade;

    @Column(columnDefinition = "TEXT")
    private String observacao;

    @Column(name = "registrado_em")
    private OffsetDateTime registradoEm = OffsetDateTime.now();
}
