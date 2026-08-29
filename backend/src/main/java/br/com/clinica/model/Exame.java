package br.com.clinica.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

// idPaciente/idConsulta ficam como Integer puro (não @ManyToOne) — mesmo
// padrão de Fatura/Pagamento: evita LAZY sem JOIN FETCH quebrando a
// serialização com open-in-view: false.
@Entity
@Table(name = "exame")
@Getter
@Setter
@NoArgsConstructor
public class Exame {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_exame")
    private Integer id;

    @NotNull
    @Column(name = "id_paciente", nullable = false)
    private Integer idPaciente;

    // Exame também pode ser solicitado fora de uma consulta (schema: NULL permitido).
    @Column(name = "id_consulta")
    private Integer idConsulta;

    @NotBlank
    @Size(max = 150)
    @Column(name = "nome_exame")
    private String nomeExame;

    @Size(max = 150)
    private String laboratorio;

    // Espelha o enum status_exame (solicitado/agendado/realizado/resultado_disponivel/cancelado).
    // Transições validadas em ExameEscritaService, não aqui.
    @Column(nullable = false)
    private String status = "solicitado";

    @Column(name = "solicitado_em")
    private OffsetDateTime solicitadoEm;
}
