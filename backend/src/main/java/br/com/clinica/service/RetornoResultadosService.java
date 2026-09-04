package br.com.clinica.service;

import br.com.clinica.dto.RetornoResultadosDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// Aba Resultados (docs/specs/retorno.md, Aba 3). "Conversão" = paciente que
// recebeu um envio_retorno e teve uma consulta Realizada depois disso —
// aproximação razoável de "voltou por causa da mensagem" sem precisar de
// rastreamento de clique/resposta (que dependeria do bot de agendamento,
// que também não está funcional).
@Service
public class RetornoResultadosService {

    // Reaproveitada nas 3 consultas: pra cada envio, existe consulta
    // Realizada depois dele? Se sim, qual a primeira (pra pegar o valor
    // faturado dela como "receita gerada").
    private static final String CTE_CONVERSAO =
            "WITH conversao AS (" +
                    "  SELECT er.id_envio, er.grupo, er.criado_em, u.nome AS usuario_nome, " +
                    "    EXISTS (SELECT 1 FROM consulta c JOIN agenda a ON a.id_agenda = c.id_agenda " +
                    "      WHERE c.id_paciente = er.id_paciente AND c.status_consulta = 'Realizada' " +
                    "      AND a.data_slot >= er.criado_em::date) AS converteu, " +
                    "    (SELECT f.valor FROM consulta c JOIN agenda a ON a.id_agenda = c.id_agenda " +
                    "      JOIN fatura f ON f.id_consulta = c.id_consulta " +
                    "      WHERE c.id_paciente = er.id_paciente AND c.status_consulta = 'Realizada' " +
                    "      AND a.data_slot >= er.criado_em::date ORDER BY a.data_slot ASC LIMIT 1) AS receita " +
                    "  FROM envio_retorno er " +
                    "  LEFT JOIN usuario u ON u.id = er.id_usuario_disparou " +
                    "  WHERE er.criado_em >= :inicio AND er.criado_em < :fim" +
                    ") ";

    private final EntityManager entityManager;

    public RetornoResultadosService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @SuppressWarnings("unchecked")
    public RetornoResultadosDto calcular(LocalDate inicio, LocalDate fim) {
        Query geral = entityManager.createNativeQuery(
                CTE_CONVERSAO + "SELECT COUNT(*), COUNT(*) FILTER (WHERE converteu), " +
                        "COALESCE(SUM(receita) FILTER (WHERE converteu), 0) FROM conversao"
        );
        geral.setParameter("inicio", inicio.atStartOfDay());
        geral.setParameter("fim", fim.plusDays(1).atStartOfDay());
        Object[] linhaGeral = (Object[]) geral.getSingleResult();
        int enviadas = ((Number) linhaGeral[0]).intValue();
        int converteram = ((Number) linhaGeral[1]).intValue();
        BigDecimal receita = (BigDecimal) linhaGeral[2];
        double conversaoPct = enviadas == 0 ? 0 : arredondar(converteram * 100.0 / enviadas);

        Query porGrupo = entityManager.createNativeQuery(
                CTE_CONVERSAO + "SELECT grupo, COUNT(*), COUNT(*) FILTER (WHERE converteu) " +
                        "FROM conversao GROUP BY grupo"
        );
        porGrupo.setParameter("inicio", inicio.atStartOfDay());
        porGrupo.setParameter("fim", fim.plusDays(1).atStartOfDay());
        List<RetornoResultadosDto.ConversaoPorGrupoDto> conversaoPorGrupo = new ArrayList<>();
        for (Object[] l : (List<Object[]>) porGrupo.getResultList()) {
            int env = ((Number) l[1]).intValue();
            int conv = ((Number) l[2]).intValue();
            conversaoPorGrupo.add(new RetornoResultadosDto.ConversaoPorGrupoDto(
                    (String) l[0], env, conv, env == 0 ? 0 : arredondar(conv * 100.0 / env)
            ));
        }

        Query mensal = entityManager.createNativeQuery(
                CTE_CONVERSAO + "SELECT to_char(criado_em, 'YYYY-MM'), COUNT(*), COUNT(*) FILTER (WHERE converteu), " +
                        "COALESCE(SUM(receita) FILTER (WHERE converteu), 0) " +
                        "FROM conversao GROUP BY 1 ORDER BY 1"
        );
        mensal.setParameter("inicio", inicio.atStartOfDay());
        mensal.setParameter("fim", fim.plusDays(1).atStartOfDay());
        List<RetornoResultadosDto.EvolucaoMensalDto> evolucaoMensal = new ArrayList<>();
        for (Object[] l : (List<Object[]>) mensal.getResultList()) {
            evolucaoMensal.add(new RetornoResultadosDto.EvolucaoMensalDto(
                    (String) l[0], ((Number) l[1]).intValue(), ((Number) l[2]).intValue(), (BigDecimal) l[3]
            ));
        }

        Query historico = entityManager.createNativeQuery(
                "SELECT to_char(date_trunc('day', er.criado_em), 'DD/MM/YYYY'), er.grupo, COUNT(*), u.nome, " +
                        "date_trunc('day', er.criado_em) AS dia " +
                        "FROM envio_retorno er LEFT JOIN usuario u ON u.id = er.id_usuario_disparou " +
                        "WHERE er.criado_em >= :inicio AND er.criado_em < :fim " +
                        "GROUP BY dia, er.grupo, u.nome ORDER BY dia DESC"
        );
        historico.setParameter("inicio", inicio.atStartOfDay());
        historico.setParameter("fim", fim.plusDays(1).atStartOfDay());
        List<RetornoResultadosDto.HistoricoEnvioDto> historicoLista = new ArrayList<>();
        for (Object[] l : (List<Object[]>) historico.getResultList()) {
            historicoLista.add(new RetornoResultadosDto.HistoricoEnvioDto(
                    (String) l[0], (String) l[1], ((Number) l[2]).intValue(),
                    l[3] != null ? (String) l[3] : "Automático"
            ));
        }

        return new RetornoResultadosDto(enviadas, converteram, conversaoPct, receita, conversaoPorGrupo, evolucaoMensal, historicoLista);
    }

    private double arredondar(double valor) {
        return Math.round(valor * 10.0) / 10.0;
    }
}
