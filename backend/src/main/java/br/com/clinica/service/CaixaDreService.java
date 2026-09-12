package br.com.clinica.service;

import br.com.clinica.dto.CaixaDreResponse;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// DRE por regime de competência — data do atendimento (agenda.data_slot),
// mesma âncora que MedicoPainelService já usa pra "receita bruta" por
// médico, pra manter os números coerentes entre /medicos e /caixa/dre.
// Despesas entram por vencimento (mesmo regime de competência), lançadas
// em /caixa/despesas (DespesaService).
//
// Cobre receita, repasse médico (medico.repasse_percentual) e despesa —
// mas o resultado só é tão completo quanto o que foi lançado manualmente
// em despesas; não há centro de custo/rateio automático. Ver avisoDespesas,
// mostrado em destaque no frontend, não em rodapé. Complementa
// CaixaFluxoConsolidadoService, que é por regime de caixa.
@Service
public class CaixaDreService {

    private static final int ID_CLINICA_ATUAL = 1; // mesmo placeholder de CaixaService/DespesaService.

    private static final String AVISO_DESPESAS =
            "O resultado reflete as despesas lançadas em Caixa → Despesas. Categorias " +
                    "de custo que não foram lançadas manualmente não entram nesta conta.";

    private final EntityManager entityManager;

    public CaixaDreService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @SuppressWarnings("unchecked")
    public CaixaDreResponse calcular(String periodo) {
        LocalDate inicio = resolverInicio(periodo);
        LocalDate fim = LocalDate.now();

        Object[] receitaLinha = (Object[]) entityManager.createNativeQuery(
                "SELECT " +
                        "  COALESCE(SUM(f.valor) FILTER (WHERE p.id_convenio IS NOT NULL), 0), " +
                        "  COALESCE(SUM(f.valor) FILTER (WHERE p.id_convenio IS NULL), 0), " +
                        "  COALESCE(SUM(f.valor) FILTER (WHERE f.status IN ('cancelado', 'estornado')), 0) " +
                        "FROM fatura f " +
                        "JOIN consulta c ON c.id_consulta = f.id_consulta " +
                        "JOIN agenda a ON a.id_agenda = c.id_agenda " +
                        "JOIN paciente p ON p.id_paciente = c.id_paciente " +
                        "WHERE a.data_slot BETWEEN :inicio AND :fim"
        ).setParameter("inicio", inicio).setParameter("fim", fim).getSingleResult();

        BigDecimal receitaBrutaConvenio = (BigDecimal) receitaLinha[0];
        BigDecimal receitaBrutaParticular = (BigDecimal) receitaLinha[1];
        BigDecimal deducoes = (BigDecimal) receitaLinha[2];
        BigDecimal receitaBruta = receitaBrutaConvenio.add(receitaBrutaParticular);
        BigDecimal receitaLiquida = receitaBruta.subtract(deducoes);

        List<Object[]> porMedico = entityManager.createNativeQuery(
                "SELECT m.nome, m.repasse_percentual, COALESCE(SUM(f.valor), 0) " +
                        "FROM medico m " +
                        "JOIN agenda a ON a.id_medico = m.id_medico AND a.data_slot BETWEEN :inicio AND :fim " +
                        "JOIN consulta c ON c.id_agenda = a.id_agenda " +
                        "JOIN fatura f ON f.id_consulta = c.id_consulta " +
                        "WHERE f.status NOT IN ('cancelado', 'estornado') " +
                        "GROUP BY m.id_medico, m.nome, m.repasse_percentual " +
                        "HAVING COALESCE(SUM(f.valor), 0) > 0 " +
                        "ORDER BY m.nome"
        ).setParameter("inicio", inicio).setParameter("fim", fim).getResultList();

        List<CaixaDreResponse.RepasseMedicoDto> repasses = new ArrayList<>();
        List<String> semRepasseConfigurado = new ArrayList<>();
        BigDecimal totalRepasses = BigDecimal.ZERO;
        for (Object[] linha : porMedico) {
            String nome = (String) linha[0];
            BigDecimal percentual = (BigDecimal) linha[1];
            BigDecimal receitaMedico = (BigDecimal) linha[2];
            if (percentual == null) {
                semRepasseConfigurado.add(nome);
                continue;
            }
            BigDecimal valorRepasse = receitaMedico.multiply(percentual)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            totalRepasses = totalRepasses.add(valorRepasse);
            repasses.add(new CaixaDreResponse.RepasseMedicoDto(nome, receitaMedico, percentual, valorRepasse));
        }

        List<Object[]> porCategoria = entityManager.createNativeQuery(
                "SELECT categoria, SUM(valor) FROM despesa " +
                        "WHERE id_clinica = :idClinica AND status != 'cancelado' AND vencimento BETWEEN :inicio AND :fim " +
                        "GROUP BY categoria ORDER BY SUM(valor) DESC"
        ).setParameter("idClinica", ID_CLINICA_ATUAL).setParameter("inicio", inicio).setParameter("fim", fim).getResultList();

        List<CaixaDreResponse.DespesaCategoriaDto> despesasPorCategoria = new ArrayList<>();
        BigDecimal totalDespesas = BigDecimal.ZERO;
        for (Object[] linha : porCategoria) {
            BigDecimal valor = (BigDecimal) linha[1];
            totalDespesas = totalDespesas.add(valor);
            despesasPorCategoria.add(new CaixaDreResponse.DespesaCategoriaDto((String) linha[0], valor));
        }

        BigDecimal resultado = receitaLiquida.subtract(totalRepasses).subtract(totalDespesas);

        return new CaixaDreResponse(
                receitaBrutaConvenio, receitaBrutaParticular, receitaBruta, deducoes, receitaLiquida,
                repasses, totalRepasses, semRepasseConfigurado, despesasPorCategoria, totalDespesas,
                resultado, AVISO_DESPESAS
        );
    }

    // Mesmas strings de período de ConveniosKpiService — consistência entre módulos.
    private LocalDate resolverInicio(String periodo) {
        LocalDate hoje = LocalDate.now();
        return switch (periodo == null ? "" : periodo) {
            case "Hoje" -> hoje;
            case "7 dias", "Últimos 7 dias" -> hoje.minusDays(6);
            case "90 dias", "Últimos 90 dias" -> hoje.minusDays(89);
            default -> hoje.minusDays(29); // "Últimos 30 dias"
        };
    }
}
