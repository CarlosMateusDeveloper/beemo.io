package br.com.clinica.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

// idConsulta fica como Integer puro (não @ManyToOne) — mesmo padrão de
// Glosa/Fatura: evita LAZY sem JOIN FETCH quebrando a serialização com
// open-in-view: false. status_auditoria_atendimento já é enum nativo
// correto no banco (bloqueado/atencao/aprovado) — escrita sempre por SQL
// nativo com CAST em AuditoriaEngineService, nunca por repository.save().
@Entity
@Table(name = "auditoria_atendimento")
@Getter
@Setter
@NoArgsConstructor
public class AuditoriaAtendimento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_auditoria_atendimento")
    private Integer id;

    @NotNull
    @Column(name = "id_consulta", nullable = false, unique = true)
    private Integer idConsulta;

    // bloqueado / atencao / aprovado.
    @NotNull
    @Column(nullable = false)
    private String status;

    @NotNull
    @Column(name = "valor_em_risco", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorEmRisco = BigDecimal.ZERO;

    @Column(name = "avaliado_em")
    private OffsetDateTime avaliadoEm;
}
