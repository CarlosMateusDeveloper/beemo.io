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
@Table(name = "solicitacao_exame")
@Getter
@Setter
@NoArgsConstructor
public class SolicitacaoExame {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_solicitacao_exame")
    private Integer id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_prontuario", nullable = false)
    private Prontuario prontuario;

    @NotBlank
    @Size(max = 150)
    private String exame;

    private Boolean urgente = false;

    @Column(columnDefinition = "TEXT")
    private String justificativa;

    @NotNull
    @Enumerated(EnumType.STRING)
    private StatusSolicitacaoExame status = StatusSolicitacaoExame.SOLICITADO;

    @Column(name = "solicitado_em")
    private OffsetDateTime solicitadoEm = OffsetDateTime.now();
}
