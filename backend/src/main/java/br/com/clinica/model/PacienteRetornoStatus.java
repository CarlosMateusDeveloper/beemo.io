package br.com.clinica.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

// Estado de contato por paciente pra /retorno (Fase 16) — aplicado igual nos
// 4 grupos. PK = id_paciente (1:1 com paciente, não um id próprio) porque
// cada paciente tem no máximo um estado de retorno ativo por vez.
@Entity
@Table(name = "paciente_retorno_status")
@Getter
@Setter
@NoArgsConstructor
public class PacienteRetornoStatus {

    @Id
    @Column(name = "id_paciente")
    private Integer idPaciente;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "id_paciente")
    private Paciente paciente;

    private String status = "pendente";

    @Column(name = "adiado_ate")
    private LocalDate adiadoAte;

    @Column(name = "motivo_nao_contatar", columnDefinition = "TEXT")
    private String motivoNaoContatar;

    @Column(name = "atualizado_em")
    private OffsetDateTime atualizadoEm = OffsetDateTime.now();
}
