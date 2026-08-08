package br.com.clinica.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "item_prescricao")
@Getter
@Setter
@NoArgsConstructor
public class ItemPrescricao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_item_prescricao")
    private Integer id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_prontuario", nullable = false)
    private Prontuario prontuario;

    @NotBlank
    @Size(max = 150)
    private String medicamento;

    @Size(max = 150)
    @Column(name = "principio_ativo")
    private String principioAtivo;

    @Size(max = 50)
    private String dosagem;

    @Size(max = 50)
    @Column(name = "via_administracao")
    private String viaAdministracao;

    @Size(max = 100)
    private String posologia;

    @Size(max = 50)
    @Column(name = "duracao_tratamento")
    private String duracaoTratamento;

    @Size(max = 50)
    private String quantidade;
}
