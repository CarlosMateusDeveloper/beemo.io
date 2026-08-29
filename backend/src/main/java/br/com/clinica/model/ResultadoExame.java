package br.com.clinica.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

// idExame fica como Integer puro (não @ManyToOne) — mesmo padrão de Exame.
// Sem coluna enum aqui, então repository.save() funciona normalmente (ao
// contrário de Exame.status).
@Entity
@Table(name = "resultado_exame")
@Getter
@Setter
@NoArgsConstructor
public class ResultadoExame {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_resultado")
    private Integer id;

    // Sem @NotNull de propósito: vem do path (/api/exames/{idExame}/resultado),
    // não do corpo do POST — ResultadoExameEscritaService é quem define esse
    // valor, então @Valid não deve exigi-lo no JSON de entrada.
    @Column(name = "id_exame", nullable = false, unique = true)
    private Integer idExame;

    @NotBlank
    @Column(name = "url_arquivo", columnDefinition = "TEXT")
    private String urlArquivo;

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    @Column(name = "recebido_em")
    private OffsetDateTime recebidoEm;
}
