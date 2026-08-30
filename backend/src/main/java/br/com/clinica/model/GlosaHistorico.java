package br.com.clinica.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "glosa_historico")
@Getter
@Setter
@NoArgsConstructor
public class GlosaHistorico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_glosa_historico")
    private Integer id;

    @Column(name = "id_glosa", nullable = false)
    private Integer idGlosa;

    @NotBlank
    @Column(nullable = false)
    private String evento;

    @Column(name = "id_usuario")
    private Integer idUsuario;

    @Column(name = "criado_em")
    private OffsetDateTime criadoEm;
}
