package br.com.clinica.service;

import br.com.clinica.dto.CaixaFluxoConsolidadoResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// Fluxo de caixa consolidado — regime de caixa: entradas via pagamento.pago_em
// (dinheiro que efetivamente entrou) e saídas via despesa.pago_em (despesas
// já pagas, lançadas em /caixa/despesas — DespesaService). Complementa
// CaixaDreService, que é por regime de competência.
@Service
public class CaixaFluxoConsolidadoService {

    private static final int ID_CLINICA_ATUAL = 1; // mesmo placeholder de CaixaService/DespesaService.
    private static final DateTimeFormatter DIA_MES_ANO = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final EntityManager entityManager;

    public CaixaFluxoConsolidadoService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @SuppressWarnings("unchecked")
    public CaixaFluxoConsolidadoResponse calcular(String periodo) {
        LocalDate inicio = resolverInicio(periodo);

        Map<LocalDate, BigDecimal> entradasPorDia = new TreeMap<>();
        Query entradasQuery = entityManager.createNativeQuery(
                "SELECT pago_em::date, SUM(valor_pago) FROM pagamento " +
                        "WHERE pago_em::date >= :inicio GROUP BY pago_em::date"
        );
        entradasQuery.setParameter("inicio", inicio);
        for (Object[] linha : (List<Object[]>) entradasQuery.getResultList()) {
            entradasPorDia.put(paraLocalDate(linha[0]), (BigDecimal) linha[1]);
        }

        Map<LocalDate, BigDecimal> saidasPorDia = new TreeMap<>();
        Query saidasQuery = entityManager.createNativeQuery(
                "SELECT pago_em, SUM(valor) FROM despesa " +
                        "WHERE id_clinica = :idClinica AND status = 'pago' AND pago_em >= :inicio GROUP BY pago_em"
        );
        saidasQuery.setParameter("idClinica", ID_CLINICA_ATUAL).setParameter("inicio", inicio);
        for (Object[] linha : (List<Object[]>) saidasQuery.getResultList()) {
            saidasPorDia.put(paraLocalDate(linha[0]), (BigDecimal) linha[1]);
        }

        Map<LocalDate, BigDecimal> todosDias = new TreeMap<>(entradasPorDia);
        todosDias.putAll(saidasPorDia);

        List<CaixaFluxoConsolidadoResponse.PontoDto> serie = new ArrayList<>();
        BigDecimal saldoAcumulado = BigDecimal.ZERO;
        BigDecimal totalEntradas = BigDecimal.ZERO;
        BigDecimal totalSaidas = BigDecimal.ZERO;
        for (LocalDate dia : todosDias.keySet()) {
            BigDecimal entradas = entradasPorDia.getOrDefault(dia, BigDecimal.ZERO);
            BigDecimal saidas = saidasPorDia.getOrDefault(dia, BigDecimal.ZERO);
            saldoAcumulado = saldoAcumulado.add(entradas).subtract(saidas);
            totalEntradas = totalEntradas.add(entradas);
            totalSaidas = totalSaidas.add(saidas);
            serie.add(new CaixaFluxoConsolidadoResponse.PontoDto(dia.format(DIA_MES_ANO), entradas, saidas, saldoAcumulado));
        }

        int diasComMovimento = serie.size();
        BigDecimal saldoPeriodo = totalEntradas.subtract(totalSaidas);
        BigDecimal ticketMedioDiario = diasComMovimento == 0 ? BigDecimal.ZERO
                : totalEntradas.divide(BigDecimal.valueOf(diasComMovimento), 2, RoundingMode.HALF_UP);

        return new CaixaFluxoConsolidadoResponse(serie, totalEntradas, totalSaidas, saldoPeriodo, diasComMovimento, ticketMedioDiario);
    }

    private LocalDate paraLocalDate(Object valor) {
        if (valor instanceof LocalDate ld) return ld;
        if (valor instanceof java.sql.Date sqlDate) return sqlDate.toLocalDate();
        throw new IllegalStateException("Tipo de data inesperado: " + valor.getClass());
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
