package br.com.clinica.service;

import br.com.clinica.dto.GlosaIndicadoresDto;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

// Spec seção 14. Totais globais (sem filtro de período nesta primeira
// versão — o motor de análise por período/convênio fica pra quando a área
// de Glosas ganhar filtros próprios, igual /convenios já tem pras outras abas).
@Service
public class GlosaIndicadoresService {

    private final EntityManager entityManager;

    public GlosaIndicadoresService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public GlosaIndicadoresDto calcular() {
        Object[] linhaGlosa = (Object[]) entityManager.createNativeQuery(
                "SELECT " +
                        "  COALESCE(SUM(CASE WHEN recorribilidade = 'recorrivel' " +
                        "    AND status NOT IN ('confirmada', 'recuperada', 'recuperada_parcialmente', 'negada') " +
                        "    THEN valor ELSE 0 END), 0), " +
                        "  COALESCE(SUM(CASE WHEN status = 'confirmada' THEN valor ELSE 0 END), 0) " +
                        "FROM glosa"
        ).getSingleResult();

        // Só a tentativa de recurso mais recente por glosa entra na conta —
        // uma glosa com dois recursos (negado, recorrido de novo) não deve
        // contar o valor duas vezes.
        Object[] linhaRecurso = (Object[]) entityManager.createNativeQuery(
                "SELECT " +
                        "  COALESCE(SUM(CASE WHEN status IN ('recuperado', 'recuperado_parcialmente') THEN valor_recuperado ELSE 0 END), 0), " +
                        "  COALESCE(SUM(CASE WHEN status IN ('recuperado_parcialmente', 'negado') THEN valor_nao_recuperado ELSE 0 END), 0), " +
                        "  COUNT(*) FILTER (WHERE status IN ('rascunho', 'em_preparacao', 'enviado', 'aguardando_retorno', 'em_analise_convenio')) " +
                        "FROM recurso_glosa " +
                        "WHERE id_recurso IN (SELECT MAX(id_recurso) FROM recurso_glosa GROUP BY id_glosa)"
        ).getSingleResult();

        BigDecimal valorRecuperavel = (BigDecimal) linhaGlosa[0];
        BigDecimal valorPerdidoConfirmada = (BigDecimal) linhaGlosa[1];
        BigDecimal valorRecuperado = (BigDecimal) linhaRecurso[0];
        BigDecimal valorPerdidoRecurso = (BigDecimal) linhaRecurso[1];
        int recursosPendentes = ((Number) linhaRecurso[2]).intValue();

        BigDecimal valorPerdido = valorPerdidoConfirmada.add(valorPerdidoRecurso);
        BigDecimal baseConcluida = valorRecuperado.add(valorPerdidoRecurso);
        double taxaRecuperacaoPct = baseConcluida.signum() == 0
                ? 0
                : valorRecuperado.doubleValue() * 100.0 / baseConcluida.doubleValue();

        return new GlosaIndicadoresDto(
                Math.round(taxaRecuperacaoPct * 10.0) / 10.0,
                valorRecuperavel, valorRecuperado, valorPerdido, recursosPendentes
        );
    }
}
