package br.com.clinica.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "agenda", uniqueConstraints = @UniqueConstraint(columnNames = {"id_medico", "data_slot", "hora_slot"}))
@Getter
@Setter
@NoArgsConstructor
public class Agenda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_agenda")
    private Integer id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_medico", nullable = false)
    private Medico medico;

    @NotNull
    @Enumerated(EnumType.STRING)
    private SituacaoAgenda situacao = SituacaoAgenda.Livre;

    @NotNull
    @Column(name = "data_slot")
    private LocalDate dataSlot;

    @NotNull
    @Column(name = "hora_slot")
    private LocalTime horaSlot;
}
