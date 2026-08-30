package br.com.clinica.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

// idGlosa/idUsuarioResponsavel/idUsuarioEnvio ficam como Integer puro (não
// @ManyToOne) — mesmo motivo de Glosa.
@Entity
@Table(name = "recurso_glosa")
@Getter
@Setter
@NoArgsConstructor
public class RecursoGlosa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_recurso")
    private Integer id;

    @Column(name = "id_glosa", nullable = false)
    private Integer idGlosa;

    // Espelha o enum status_recurso_glosa — transições validadas em
    // RecursoGlosaEscritaService, não aqui.
    @Column(nullable = false)
    private String status = "rascunho";

    @Column(columnDefinition = "TEXT")
    private String justificativa;

    @Column(name = "prazo_limite")
    private LocalDate prazoLimite;

    @Column(name = "id_usuario_responsavel")
    private Integer idUsuarioResponsavel;

    // manual / portal_convenio / email — nulo até enviar.
    @Column(name = "canal_envio")
    private String canalEnvio;

    private String protocolo;

    @Column(name = "enviado_em")
    private OffsetDateTime enviadoEm;

    @Column(name = "id_usuario_envio")
    private Integer idUsuarioEnvio;

    @Column(name = "respondido_em")
    private OffsetDateTime respondidoEm;

    @Column(name = "valor_recuperado", precision = 10, scale = 2)
    private BigDecimal valorRecuperado;

    @Column(name = "valor_nao_recuperado", precision = 10, scale = 2)
    private BigDecimal valorNaoRecuperado;

    @Column(name = "motivo_negativa", columnDefinition = "TEXT")
    private String motivoNegativa;

    @Column(name = "documento_resposta_url", columnDefinition = "TEXT")
    private String documentoRespostaUrl;

    @Column(name = "evidencias_conferidas", nullable = false)
    private boolean evidenciasConferidas = false;

    @Column(name = "criado_em")
    private OffsetDateTime criadoEm;
}
