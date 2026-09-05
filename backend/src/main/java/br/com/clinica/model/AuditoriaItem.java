package br.com.clinica.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// idAuditoriaAtendimento/idRegra ficam como Integer puro, mesmo motivo de
// AuditoriaAtendimento. status_auditoria_item (ok/falha) e severidade_regra
// já são enums nativos corretos no banco — escrita por SQL nativo com CAST
// em AuditoriaEngineService.
@Entity
@Table(name = "auditoria_item")
@Getter
@Setter
@NoArgsConstructor
public class AuditoriaItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_auditoria_item")
    private Integer id;

    @NotNull
    @Column(name = "id_auditoria_atendimento", nullable = false)
    private Integer idAuditoriaAtendimento;

    // Regra que gerou o item — nulo quando o item não vem de uma regra
    // cadastrada (não há caso assim hoje, mas a coluna permite).
    @Column(name = "id_regra")
    private Integer idRegra;

    // ok / falha.
    @NotNull
    @Column(nullable = false)
    private String status;

    @NotBlank
    @Column(nullable = false)
    private String descricao;

    // critica / alta / media / baixa — nulo quando status = ok.
    private String severidade;

    @Column(name = "acao_recomendada")
    private String acaoRecomendada;
}
