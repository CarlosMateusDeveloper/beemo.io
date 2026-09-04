package br.com.clinica.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "envio_retorno")
@Getter
@Setter
@NoArgsConstructor
public class EnvioRetorno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_envio")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_paciente", nullable = false)
    private Paciente paciente;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GrupoRetorno grupo;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String texto;

    // Liga no registro real em `mensagem` (Fase 10, tabela compartilhada com
    // /whatsapp) — sem entidade JPA própria pra `mensagem` porque só esse
    // módulo grava nela pelo lado Java; a leitura/estado da conversa
    // continua toda no chatbot (Python).
    @Column(name = "id_mensagem")
    private Long idMensagem;

    // NULL = disparo automático (régua); preenchido = alguém clicou "enviar".
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario_disparou")
    private Usuario usuarioDisparou;

    @Column(name = "criado_em")
    private OffsetDateTime criadoEm = OffsetDateTime.now();
}
