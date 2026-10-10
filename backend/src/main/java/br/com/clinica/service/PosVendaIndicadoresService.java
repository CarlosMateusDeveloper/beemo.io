package br.com.clinica.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/** Coorte pela data da falta; resultados são a situação atual dos mesmos casos. */
@Service
public class PosVendaIndicadoresService {
    private final JdbcTemplate db;
    private final AccessControlService access;
    @Value("${app.clinic.time-zone:America/Fortaleza}")
    private String clinicTimeZone = "America/Fortaleza";

    private static final String BASE = """
        WITH base AS (
          SELECT pv.*, ev.primeiro_contato, ev.tentativas,
            EXISTS(SELECT 1 FROM pos_venda_consulta v WHERE v.id_caso=pv.id
              AND v.id_consulta<>pv.id_consulta_origem) AS ja_reagendou
          FROM pos_venda_caso pv
          JOIN consulta c ON c.id_consulta=pv.id_consulta_origem
          JOIN agenda a ON a.id_agenda=c.id_agenda
          LEFT JOIN LATERAL (
            SELECT min(criado_em) AS primeiro_contato, count(*) AS tentativas
            FROM pos_venda_evento WHERE id_caso=pv.id AND tipo='contato'
          ) ev ON true
          WHERE pv.data_falta>=CAST(? AS date) AND pv.data_falta<CAST(? AS date)
            AND pv.status<>'registro_corrigido'
            AND (CAST(? AS INTEGER) IS NULL OR a.id_medico=?)
        )
        """;

    public PosVendaIndicadoresService(JdbcTemplate db, AccessControlService access) { this.db=db;this.access=access; }

    static void validarPeriodo(LocalDate inicio, LocalDate fim) {
        if (inicio==null || fim==null || fim.isBefore(inicio) || ChronoUnit.DAYS.between(inicio,fim)>365) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Escolha um período de até 366 dias, com início anterior ou igual ao fim");
        }
    }

    public Map<String,Object> calcular(LocalDate inicio, LocalDate fim) {
        LocalDate hoje=LocalDate.now(ZoneId.of(clinicTimeZone));
        LocalDate de=inicio==null ? hoje.withDayOfMonth(1) : inicio;
        LocalDate ate=fim==null ? hoje : fim;
        validarPeriodo(de,ate);
        var limite=ate.plusDays(1);
        Integer medico=access.clinicalDoctorId();
        var resumo=db.queryForMap(BASE+"""
            SELECT count(*) AS faltas,
              count(*) FILTER(WHERE primeiro_contato IS NOT NULL) AS contatados,
              count(*) FILTER(WHERE ja_reagendou) AS reagendados,
              count(*) FILTER(WHERE status='recuperado') AS recuperados,
              count(*) FILTER(WHERE status='reagendado') AS aguardando_comparecimento,
              count(*) FILTER(WHERE status='nova_falta') AS novas_faltas,
              count(*) FILTER(WHERE status='nao_deseja') AS nao_deseja,
              count(*) FILTER(WHERE status='nao_contatar') AS nao_contatar,
              count(*) FILTER(WHERE status IN ('pendente','em_contato','sem_resposta','nova_falta','reagendado')) AS abertos,
              coalesce(sum(tentativas),0) AS tentativas,
              round(100.0*count(*) FILTER(WHERE status='recuperado')/nullif(count(*),0),1) AS taxa_recuperacao,
              round(avg(greatest(0,extract(epoch FROM (primeiro_contato-(data_falta AT TIME ZONE ?)))/3600))
                FILTER(WHERE primeiro_contato IS NOT NULL),1) AS horas_primeiro_contato
            FROM base
            """,de,limite,medico,medico,clinicTimeZone);
        var motivos=db.queryForList(BASE+"""
            SELECT coalesce(nullif(trim(motivo_falta),''),'Não informado') AS motivo, count(*) AS quantidade
            FROM base GROUP BY 1 ORDER BY quantidade DESC,motivo LIMIT 10
            """,de,limite,medico,medico);
        var equipe=db.queryForList(BASE+"""
            SELECT b.id_responsavel,coalesce(u.nome,'Sem responsável') AS responsavel,
              count(*) AS casos,count(*) FILTER(WHERE b.primeiro_contato IS NOT NULL) AS contatados,
              count(*) FILTER(WHERE b.status='recuperado') AS recuperados,
              count(*) FILTER(WHERE b.status IN ('pendente','em_contato','sem_resposta','nova_falta','reagendado')) AS abertos
            FROM base b LEFT JOIN usuario u ON u.id=b.id_responsavel
            GROUP BY b.id_responsavel,u.nome ORDER BY casos DESC,responsavel
            """,de,limite,medico,medico);
        return Map.of("inicio",de,"fim",ate,"resumo",resumo,"motivos",motivos,"equipe",equipe);
    }
}
