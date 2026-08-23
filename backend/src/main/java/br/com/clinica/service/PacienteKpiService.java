package br.com.clinica.service;

import br.com.clinica.dto.PacientesKpisResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

// Agrega os 4 KPIs da tela /pacientes (docs/specs/pacientes.md). Usa queries
// nativas pelas mesmas razões do DashboardService: os agregados cruzam
// paciente/consulta/agenda/mensagem/documento_anexo, que a JpaRepository
// simples de Paciente não expressa bem.
@Service
public class PacienteKpiService {

    private final EntityManager entityManager;

    public PacienteKpiService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public PacientesKpisResponse calcular() {
        LocalDate hoje = LocalDate.now();
        LocalDate inicioMes = hoje.withDayOfMonth(1);
        LocalDate fimMes = hoje.withDayOfMonth(hoje.lengthOfMonth());
        LocalDate inicioMesAnterior = inicioMes.minusMonths(1);
        LocalDate fimMesAnterior = inicioMes.minusDays(1);
        LocalDate umAnoAtras = hoje.minusMonths(12);
        LocalDate seisMesesAtras = hoje.minusMonths(6);

        int totalCadastros = contar("SELECT COUNT(*) FROM paciente");

        int baseAtiva = contar(
                "SELECT COUNT(DISTINCT c.id_paciente) FROM consulta c " +
                        "JOIN agenda a ON a.id_agenda = c.id_agenda " +
                        "WHERE a.data_slot BETWEEN :inicio AND :fim",
                q -> { q.setParameter("inicio", umAnoAtras); q.setParameter("fim", hoje); }
        );

        int novos = contarNovosNoPeriodo(inicioMes, fimMes);
        int novosMesAnterior = contarNovosNoPeriodo(inicioMesAnterior, fimMesAnterior);
        double novosDeltaPct = novosMesAnterior == 0 ? 0 : arredondar((novos - novosMesAnterior) * 100.0 / novosMesAnterior);

        int novosViaWhatsapp = contar(
                "SELECT COUNT(DISTINCT primeira.id_paciente) FROM (" +
                        "  SELECT c.id_paciente, MIN(a.data_slot) AS primeira_data " +
                        "  FROM consulta c JOIN agenda a ON a.id_agenda = c.id_agenda " +
                        "  GROUP BY c.id_paciente" +
                        ") primeira " +
                        "JOIN mensagem m ON m.id_paciente = primeira.id_paciente " +
                        "WHERE primeira.primeira_data BETWEEN :inicio AND :fim",
                q -> { q.setParameter("inicio", inicioMes); q.setParameter("fim", fimMes); }
        );
        double novosCanalWhatsappPct = novos == 0 ? 0 : arredondar(novosViaWhatsapp * 100.0 / novos);

        int risco = contar(
                "SELECT COUNT(*) FROM (" +
                        "  SELECT c.id_paciente, MAX(a.data_slot) AS ultima_data " +
                        "  FROM consulta c JOIN agenda a ON a.id_agenda = c.id_agenda " +
                        "  GROUP BY c.id_paciente" +
                        ") ultima " +
                        "WHERE ultima.ultima_data BETWEEN :inicio AND :fim",
                q -> { q.setParameter("inicio", umAnoAtras); q.setParameter("fim", seisMesesAtras); }
        );

        int incompletosSemDocumento = contar(
                "SELECT COUNT(*) FROM paciente p " +
                        "WHERE NOT EXISTS (SELECT 1 FROM documento_anexo d WHERE d.id_paciente = p.id_paciente)"
        );

        return new PacientesKpisResponse(
                totalCadastros, baseAtiva, novos, novosDeltaPct, novosCanalWhatsappPct,
                risco, incompletosSemDocumento, incompletosSemDocumento
        );
    }

    private int contarNovosNoPeriodo(LocalDate inicio, LocalDate fim) {
        return contar(
                "SELECT COUNT(*) FROM (" +
                        "  SELECT c.id_paciente, MIN(a.data_slot) AS primeira_data " +
                        "  FROM consulta c JOIN agenda a ON a.id_agenda = c.id_agenda " +
                        "  GROUP BY c.id_paciente" +
                        ") primeira " +
                        "WHERE primeira.primeira_data BETWEEN :inicio AND :fim",
                q -> { q.setParameter("inicio", inicio); q.setParameter("fim", fim); }
        );
    }

    private int contar(String sql) {
        return contar(sql, q -> { });
    }

    private int contar(String sql, java.util.function.Consumer<Query> parametros) {
        Query query = entityManager.createNativeQuery(sql);
        parametros.accept(query);
        return ((Number) query.getSingleResult()).intValue();
    }

    private double arredondar(double valor) {
        return Math.round(valor * 10.0) / 10.0;
    }
}
