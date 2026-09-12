package br.com.clinica.dto;

import java.math.BigDecimal;
import java.util.List;

// DRE por regime de competência (data do atendimento). Cobre receita,
// repasse médico e despesas lançadas em /caixa/despesas (ver avisoDespesas
// e CaixaDreService — o resultado só é tão completo quanto o que foi
// lançado manualmente).
public record CaixaDreResponse(
        BigDecimal receitaBrutaConvenio, BigDecimal receitaBrutaParticular, BigDecimal receitaBruta,
        BigDecimal deducoes, BigDecimal receitaLiquida,
        List<RepasseMedicoDto> repasses, BigDecimal totalRepasses, List<String> medicosSemRepasseConfigurado,
        List<DespesaCategoriaDto> despesasPorCategoria, BigDecimal totalDespesas,
        BigDecimal resultado, String avisoDespesas
) {

    public record RepasseMedicoDto(String medicoNome, BigDecimal receitaBruta, BigDecimal percentual, BigDecimal valorRepasse) {
    }

    public record DespesaCategoriaDto(String categoria, BigDecimal valor) {
    }
}
