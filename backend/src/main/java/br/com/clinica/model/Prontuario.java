package br.com.clinica.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "prontuario")
@Getter
@Setter
@NoArgsConstructor
public class Prontuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_prontuario")
    private Integer id;

    @NotNull
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_consulta", nullable = false, unique = true)
    private Consulta consulta;

    // Subjetivo
    @Column(name = "queixa_principal", columnDefinition = "TEXT")
    private String queixaPrincipal;

    @Column(name = "historia_doenca_atual", columnDefinition = "TEXT")
    private String historiaDoencaAtual;

    @NotBlank
    @Column(columnDefinition = "TEXT")
    private String descricao;

    // Objetivo
    @Column(name = "exame_fisico", columnDefinition = "TEXT")
    private String exameFisico;

    // Avaliacao
    @Column(name = "hipotese_diagnostica", columnDefinition = "TEXT")
    private String hipoteseDiagnostica;

    @NotBlank
    @Column(columnDefinition = "TEXT")
    private String diagnostico;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_diagnostico")
    private TipoDiagnostico tipoDiagnostico = TipoDiagnostico.DEFINITIVO;

    // Plano
    @NotBlank
    @Column(columnDefinition = "TEXT")
    private String prescricao;

    @Column(name = "plano_terapeutico", columnDefinition = "TEXT")
    private String planoTerapeutico;

    @Column(columnDefinition = "TEXT")
    private String conduta;

    // Assinatura e auditoria
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_medico_responsavel")
    private Medico medicoResponsavel;

    @Column(name = "assinado_em")
    private OffsetDateTime assinadoEm;

    @Column(name = "criado_em")
    private OffsetDateTime criadoEm = OffsetDateTime.now();
}
