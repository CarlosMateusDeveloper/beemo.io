package br.com.clinica.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "regra_auditoria")
@Getter
@Setter
@NoArgsConstructor
public class RegraAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_regra")
    private Integer id;

    // Sem @NotNull: setado pelo service a partir do path (ver ConvenioPlano).
    // @JsonIgnore: convênio já é conhecido pela URL (ver ConvenioPlano).
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_convenio", nullable = false)
    private Convenio convenio;

    // NULL = regra geral do convenio; preenchido = so vale pra esse procedimento.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_convenio_procedimento")
    private ConvenioProcedimento procedimento;

    @NotNull
    @Enumerated(EnumType.STRING)
    private TipoRegra tipo;

    @NotNull
    @Enumerated(EnumType.STRING)
    private Severidade severidade;

    @NotBlank
    @Size(max = 255)
    private String descricao;

    // Config especifica da regra (ex: {"quantidadeMaxima": 3}) — permite
    // novas variacoes sem alterar o schema. Motor que le isso ainda nao
    // existe (fase futura); aqui e so persistencia via CRUD. Default {} em
    // Java (não só no banco): Hibernate manda NULL explícito quando o campo
    // não vem no JSON, o que ignora o DEFAULT '{}' da coluna.
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> parametros = new HashMap<>();

    private Boolean ativo = true;

    public enum TipoRegra {
        autorizacao_obrigatoria, documento_obrigatorio, codigo_incompativel,
        procedimento_nao_coberto, paciente_inelegivel, quantidade_acima_permitido,
        prazo_faturamento_excedido, profissional_nao_habilitado, divergencia_atendimento_faturamento
    }

    public enum Severidade {
        critica, alta, media, baixa
    }
}
