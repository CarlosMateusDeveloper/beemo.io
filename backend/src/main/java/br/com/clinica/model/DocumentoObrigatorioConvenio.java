package br.com.clinica.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "documento_obrigatorio_convenio")
@Getter
@Setter
@NoArgsConstructor
public class DocumentoObrigatorioConvenio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_documento_obrigatorio")
    private Integer id;

    // Sem @NotNull: setado pelo service a partir do path (ver ConvenioPlano).
    // @JsonIgnore: convênio já é conhecido pela URL (ver ConvenioPlano).
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_convenio", nullable = false)
    private Convenio convenio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_convenio_procedimento")
    private ConvenioProcedimento procedimento;

    @NotBlank
    @Size(max = 150)
    @Column(name = "nome_documento")
    private String nomeDocumento;

    private Boolean obrigatorio = true;
}
