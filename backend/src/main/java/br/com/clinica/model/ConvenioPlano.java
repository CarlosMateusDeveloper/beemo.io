package br.com.clinica.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "convenio_plano")
@Getter
@Setter
@NoArgsConstructor
public class ConvenioPlano {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_plano")
    private Integer id;

    // Sem @NotNull: setado pelo service a partir do path, não vem no corpo
    // da requisição — @Valid rejeitaria o POST antes disso acontecer.
    // nullable=false na coluna garante a integridade no banco de qualquer jeito.
    // @JsonIgnore: o convênio já é conhecido pela URL (/api/convenios/{id}/planos),
    // reincluí-lo no JSON só forçaria a inicializar o proxy LAZY fora da sessão.
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_convenio", nullable = false)
    private Convenio convenio;

    @NotBlank
    @Size(max = 100)
    private String nome;

    @Size(max = 50)
    private String codigo;

    private Boolean ativo = true;
}
