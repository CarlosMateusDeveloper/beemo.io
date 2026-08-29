package br.com.clinica.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

// idConsulta/idConvenio ficam como Integer puro (não @ManyToOne) — mesmo
// padrão de Fatura/Pagamento: evita LAZY sem JOIN FETCH quebrando a
// serialização com open-in-view: false.
@Entity
@Table(name = "autorizacao_convenio")
@Getter
@Setter
@NoArgsConstructor
public class AutorizacaoConvenio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_autorizacao")
    private Integer id;

    @NotNull
    @Column(name = "id_consulta", nullable = false, unique = true)
    private Integer idConsulta;

    @NotNull
    @Column(name = "id_convenio", nullable = false)
    private Integer idConvenio;

    @Size(max = 50)
    @Column(name = "numero_guia")
    private String numeroGuia;

    // Espelha o enum status_autorizacao (pendente/autorizado/negado/expirado).
    @Column(nullable = false)
    private String status = "pendente";

    @Column(name = "solicitado_em")
    private OffsetDateTime solicitadoEm;

    @Column(name = "respondido_em")
    private OffsetDateTime respondidoEm;
}
