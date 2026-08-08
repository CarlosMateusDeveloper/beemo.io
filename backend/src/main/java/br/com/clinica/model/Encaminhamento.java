package br.com.clinica.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "encaminhamento")
@Getter
@Setter
@NoArgsConstructor
public class Encaminhamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_encaminhamento")
    private Integer id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_prontuario", nullable = false)
    private Prontuario prontuario;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_especialidade_destino", nullable = false)
    private Especialidade especialidadeDestino;

    @NotBlank
    @Column(columnDefinition = "TEXT")
    private String motivo;

    @NotNull
    @Enumerated(EnumType.STRING)
    private PrioridadeEncaminhamento prioridade = PrioridadeEncaminhamento.ROTINA;

    @Column(name = "emitido_em")
    private OffsetDateTime emitidoEm = OffsetDateTime.now();
}
