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
@Table(name = "documento_clinico")
@Getter
@Setter
@NoArgsConstructor
public class DocumentoClinico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_documento_clinico")
    private Integer id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_prontuario", nullable = false)
    private Prontuario prontuario;

    @NotNull
    @Enumerated(EnumType.STRING)
    private TipoDocumentoClinico tipo;

    @Column(name = "dias_afastamento")
    private Integer diasAfastamento;

    @Size(max = 10)
    @Column(name = "codigo_cid_relacionado")
    private String codigoCidRelacionado;

    @NotBlank
    @Column(columnDefinition = "TEXT")
    private String texto;

    @Column(name = "emitido_em")
    private OffsetDateTime emitidoEm = OffsetDateTime.now();
}
