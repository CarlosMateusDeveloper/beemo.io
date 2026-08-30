package br.com.clinica.service;

import br.com.clinica.dto.ConveniosKpisResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

// KPIs do cabeçalho de /convenios. Só "A receber" é real hoje — vem de
// `fatura` (consultas de paciente com convênio, ainda não pagas). Em
// risco/Glosado/Recuperado dependem de auditoria_atendimento/glosa, que
// existem no schema (Fase 14) mas não têm motor/tela nesta fase — ficam
// zero real, não inventado, mesmo princípio já usado em DashboardService.
@Service
public class ConveniosKpiService {

    private final EntityManager entityManager;

    public ConveniosKpiService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public ConveniosKpisResponse calcular(String periodo, Integer convenioId) {
        LocalDate inicio = resolverInicio(periodo);

        Query query = entityManager.createNativeQuery(
                "SELECT COALESCE(SUM(f.valor), 0), " +
                        "  COALESCE(AVG(EXTRACT(DAY FROM (now() - f.criado_em))), 0) " +
                        "FROM fatura f " +
                        "JOIN consulta c ON c.id_consulta = f.id_consulta " +
                        "JOIN paciente p ON p.id_paciente = c.id_paciente " +
                        "WHERE p.id_convenio IS NOT NULL " +
                        "  AND f.status IN ('pendente', 'atrasado') " +
                        "  AND f.criado_em >= :inicio " +
                        "  AND (CAST(:convenioId AS INTEGER) IS NULL OR p.id_convenio = :convenioId)"
        );
        query.setParameter("inicio", inicio.atStartOfDay());
        query.setParameter("convenioId", convenioId);
        Object[] linha = (Object[]) query.getSingleResult();
        BigDecimal aReceberValor = (BigDecimal) linha[0];
        int mediaDias = ((Number) linha[1]).intValue();

        // Lotes ainda não existe (aba Lotes é fase futura) — zero real.
        long lotes = 0;

        return new ConveniosKpisResponse(
                new ConveniosKpisResponse.AReceberDto(aReceberValor, lotes, mediaDias),
                new ConveniosKpisResponse.EmRiscoDto(BigDecimal.ZERO, 0),
                new ConveniosKpisResponse.GlosadoDto(BigDecimal.ZERO, 0),
                new ConveniosKpisResponse.RecuperadoDto(BigDecimal.ZERO, 0)
        );
    }

    private LocalDate resolverInicio(String periodo) {
        LocalDate hoje = LocalDate.now();
        return switch (periodo == null ? "" : periodo) {
            case "Hoje" -> hoje;
            case "7 dias", "Últimos 7 dias" -> hoje.minusDays(6);
            case "90 dias", "Últimos 90 dias" -> hoje.minusDays(89);
            default -> hoje.minusDays(29); // "Últimos 30 dias" — default da spec
        };
    }
}
