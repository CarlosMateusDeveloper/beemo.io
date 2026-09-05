package br.com.clinica.service;

import br.com.clinica.dto.LoteElegivelItemDto;
import br.com.clinica.dto.LoteSugestaoDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

// Passos 1 e 2 do wizard de lotes (docs/specs/convenios.md, Aba 4: "o
// usuário não deve precisar selecionar atendimento por atendimento" — o
// ClinicOS sugere o agrupamento, a pessoa só revisa).
//
// "Atendimento aprovado" (fluxo da spec) dependeria da esteira de Auditoria,
// que ainda não roda (motor de regras existe na config do convênio, mas sem
// execução real — ver aba Auditoria). Sem isso, "elegível pra lote" aqui é:
// fatura pendente de uma consulta Realizada de paciente com convênio, que
// ainda não está em nenhum lote. Honesto com o que o sistema sustenta hoje,
// não bloqueia o fluxo esperando uma peça que ainda não existe.
@Service
public class LoteSugestaoService {

    private static final DateTimeFormatter DIA_MES_ANO = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final EntityManager entityManager;

    public LoteSugestaoService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    // Exclui atendimento bloqueado pela auditoria (docs/specs/convenios.md:
    // "Atendimentos aprovados → ClinicOS agrupa") — sem linha em
    // auditoria_atendimento (atendimento anterior ao motor existir) ou
    // status aprovado/atencao contam como elegíveis; "atencao" só vira um
    // aviso na revisão humana (ver LoteElegivelItemDto), não bloqueia.
    private static final String SEM_BLOQUEIO_AUDITORIA =
            "NOT EXISTS (SELECT 1 FROM auditoria_atendimento aa WHERE aa.id_consulta = c.id_consulta AND aa.status = 'bloqueado') ";

    @SuppressWarnings("unchecked")
    public List<LoteSugestaoDto> sugestoes() {
        Query query = entityManager.createNativeQuery(
                "SELECT p.id_convenio, cv.nome, COUNT(*), SUM(f.valor) " +
                        "FROM fatura f " +
                        "JOIN consulta c ON c.id_consulta = f.id_consulta " +
                        "JOIN paciente p ON p.id_paciente = c.id_paciente " +
                        "JOIN convenio cv ON cv.id_convenio = p.id_convenio " +
                        "WHERE f.status = 'pendente' AND c.status_consulta = 'Realizada' " +
                        "  AND NOT EXISTS (SELECT 1 FROM lote_item li WHERE li.id_fatura = f.id_fatura) " +
                        "  AND " + SEM_BLOQUEIO_AUDITORIA +
                        "GROUP BY p.id_convenio, cv.nome"
        );
        List<LoteSugestaoDto> resultado = new ArrayList<>();
        for (Object[] l : (List<Object[]>) query.getResultList()) {
            resultado.add(new LoteSugestaoDto(
                    ((Number) l[0]).intValue(), (String) l[1], ((Number) l[2]).intValue(), (BigDecimal) l[3]
            ));
        }
        resultado.sort((a, b) -> b.valorTotal().compareTo(a.valorTotal()));
        return resultado;
    }

    @SuppressWarnings("unchecked")
    public List<LoteElegivelItemDto> elegiveis(Integer idConvenio) {
        Query query = entityManager.createNativeQuery(
                "SELECT f.id_fatura, p.nome, a.data_slot, c.tipo::text, f.valor, " +
                        "  (SELECT aa.status FROM auditoria_atendimento aa WHERE aa.id_consulta = c.id_consulta) " +
                        "FROM fatura f " +
                        "JOIN consulta c ON c.id_consulta = f.id_consulta " +
                        "JOIN agenda a ON a.id_agenda = c.id_agenda " +
                        "JOIN paciente p ON p.id_paciente = c.id_paciente " +
                        "WHERE f.status = 'pendente' AND c.status_consulta = 'Realizada' " +
                        "  AND p.id_convenio = :idConvenio " +
                        "  AND NOT EXISTS (SELECT 1 FROM lote_item li WHERE li.id_fatura = f.id_fatura) " +
                        "  AND " + SEM_BLOQUEIO_AUDITORIA +
                        "ORDER BY a.data_slot ASC"
        );
        query.setParameter("idConvenio", idConvenio);
        List<LoteElegivelItemDto> resultado = new ArrayList<>();
        for (Object[] l : (List<Object[]>) query.getResultList()) {
            LocalDate data = paraLocalDate(l[2]);
            resultado.add(new LoteElegivelItemDto(
                    ((Number) l[0]).intValue(), (String) l[1], data.format(DIA_MES_ANO), (String) l[3], (BigDecimal) l[4],
                    (String) l[5]
            ));
        }
        return resultado;
    }

    private LocalDate paraLocalDate(Object valor) {
        if (valor instanceof LocalDate localDate) return localDate;
        if (valor instanceof java.sql.Date sqlDate) return sqlDate.toLocalDate();
        if (valor instanceof java.sql.Timestamp timestamp) return timestamp.toLocalDateTime().toLocalDate();
        if (valor instanceof java.time.Instant instant) return instant.atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        throw new IllegalStateException("Tipo de data inesperado: " + valor.getClass());
    }
}
