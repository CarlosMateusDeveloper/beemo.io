package br.com.clinica.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "consulta")
@Getter
@Setter
@NoArgsConstructor
public class Consulta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_consulta")
    private Integer id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_paciente", nullable = false)
    private Paciente paciente;

    // agenda vive no servico Go (mesmo Postgres, tabela agenda); aqui fica so o FK.
    @NotNull
    @Column(name = "id_agenda", nullable = false, unique = true)
    private Integer idAgenda;

    // insertable/updatable = false: status_consulta é enum nativo do Postgres;
    // repository.save() bindaria o valor como varchar e quebraria ("operator
    // does not exist") sem stringtype=unspecified na URL JDBC. Escrita fica
    // em ConsultaEscritaService via SQL nativo com CAST; sem valor informado,
    // a coluna usa o DEFAULT do banco ('Agendada'). Convert (não @Enumerated)
    // por causa do espaço em "Em Espera"/"Em Atendimento" — ver StatusConsultaConverter.
    @Convert(converter = StatusConsultaConverter.class)
    @Column(name = "status_consulta", insertable = false, updatable = false)
    private StatusConsulta statusConsulta;
}
