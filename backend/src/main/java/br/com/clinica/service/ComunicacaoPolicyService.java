package br.com.clinica.service;

import br.com.clinica.dto.ComunicacaoDtos;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ComunicacaoPolicyService {
    private static final Map<String,Set<String>> TRANSICOES = Map.of(
            "rascunho", Set.of("submetido", "inativo"),
            "submetido", Set.of("aprovado", "rejeitado", "inativo"),
            "aprovado", Set.of("inativo"),
            "rejeitado", Set.of("inativo"),
            "inativo", Set.of());

    private final JdbcTemplate db;
    public ComunicacaoPolicyService(JdbcTemplate db) { this.db = db; }

    @Transactional
    public Map<String,Object> registrar(ComunicacaoDtos.Consentimento input, int actor) {
        if (!Boolean.TRUE.equals(db.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM paciente WHERE id_paciente=?)", Boolean.class, input.pacienteId()))) {
            throw notFound("Paciente não encontrado");
        }
        if (input.ocorridoEm().isAfter(OffsetDateTime.now().plusMinutes(5))) {
            throw bad("A data do consentimento não pode estar no futuro");
        }
        if (input.decisao().equals("concedido") && blank(input.textoAceito())) {
            throw bad("Registre o texto aceito pelo paciente");
        }
        long id = db.queryForObject("""
                INSERT INTO comunicacao_consentimento_evento(
                    id_paciente,canal,finalidade,decisao,origem,texto_aceito,
                    evidencia_referencia,ocorrido_em,id_usuario
                ) VALUES (?,?,?,?,?,?,?,?,?) RETURNING id_consentimento
                """, Long.class, input.pacienteId(), input.canal(), input.finalidade(), input.decisao(),
                input.origem(), clean(input.textoAceito()), clean(input.evidenciaReferencia()),
                Timestamp.from(input.ocorridoEm().toInstant()), actor);
        if (input.canal().equals("todos") && input.finalidade().equals("todos")) {
            if (input.decisao().equals("revogado")) {
                db.update("""
                        INSERT INTO paciente_retorno_status(id_paciente,status,motivo_nao_contatar,atualizado_em)
                        VALUES (?,'nao_contatar','Descadastro registrado na política de comunicação',now())
                        ON CONFLICT(id_clinica,id_paciente) DO UPDATE SET status='nao_contatar',
                            motivo_nao_contatar=excluded.motivo_nao_contatar,atualizado_em=now()
                        """, input.pacienteId());
            } else {
                db.update("""
                        UPDATE paciente_retorno_status SET status='pendente',motivo_nao_contatar=NULL,atualizado_em=now()
                        WHERE id_paciente=? AND status='nao_contatar'
                        """, input.pacienteId());
            }
        }
        return consentimento(id);
    }

    public Map<String,Object> paciente(int patient) {
        if (!Boolean.TRUE.equals(db.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM paciente WHERE id_paciente=?)", Boolean.class, patient))) {
            throw notFound("Paciente não encontrado");
        }
        List<Map<String,Object>> history = db.queryForList("""
                SELECT c.id_consentimento AS id,c.canal,c.finalidade,c.decisao,c.origem,c.texto_aceito AS "textoAceito",
                    c.evidencia_referencia AS "evidenciaReferencia",c.ocorrido_em AS "ocorridoEm",
                    c.criado_em AS "registradoEm",u.nome AS responsavel
                FROM comunicacao_consentimento_evento c LEFT JOIN usuario u ON u.id=c.id_usuario
                WHERE c.id_paciente=? ORDER BY c.ocorrido_em DESC,c.id_consentimento DESC
                """, patient);
        List<Map<String,Object>> current = db.queryForList("""
                SELECT DISTINCT ON (canal,finalidade) id_consentimento AS id,canal,finalidade,decisao,
                    origem,ocorrido_em AS "ocorridoEm"
                FROM comunicacao_consentimento_evento WHERE id_paciente=?
                ORDER BY canal,finalidade,ocorrido_em DESC,id_consentimento DESC
                """, patient);
        List<Map<String,Object>> attempts = db.queryForList("""
                SELECT id_tentativa AS id,canal,finalidade,essencial,resultado,motivo,origem,
                    id_consentimento AS "consentimentoId",id_modelo AS "modeloId",
                    modelo_versao AS "modeloVersao",criado_em AS "criadoEm"
                FROM comunicacao_tentativa WHERE id_paciente=? ORDER BY id_tentativa DESC LIMIT 100
                """, patient);
        return Map.of("estado", current, "historico", history, "tentativas", attempts);
    }

    public Map<String,Object> revogarTodos(int patient, int actor, String evidence) {
        return registrar(new ComunicacaoDtos.Consentimento(patient,"todos","todos","revogado","usuario",
                null,evidence,OffsetDateTime.now()),actor);
    }

    @Transactional
    public Map<String,Object> criarModelo(ComunicacaoDtos.Modelo input, int actor) {
        Integer version = db.queryForObject("""
                SELECT coalesce(max(versao),0)+1 FROM comunicacao_modelo WHERE codigo=?
                """, Integer.class, input.codigo());
        long id = db.queryForObject("""
                INSERT INTO comunicacao_modelo(
                    codigo,versao,nome,canal,finalidade,categoria,idioma,conteudo,id_usuario
                ) VALUES (?,?,?,?,?,?,?,?,?) RETURNING id_modelo
                """, Long.class, input.codigo(), version, input.nome().strip(), input.canal(), input.finalidade(),
                input.categoria().strip(), input.idioma(), input.conteudo().strip(), actor);
        db.update("INSERT INTO comunicacao_modelo_status_evento(id_modelo,status,id_usuario) VALUES (?,'rascunho',?)",
                id, actor);
        return modelo(id);
    }

    @Transactional
    public Map<String,Object> transicionarModelo(long id, ComunicacaoDtos.ModeloStatus input, int actor) {
        var current = db.queryForList("""
                SELECT m.*,s.status FROM comunicacao_modelo m
                JOIN LATERAL (SELECT status FROM comunicacao_modelo_status_evento
                    WHERE id_modelo=m.id_modelo ORDER BY id_status DESC LIMIT 1) s ON true
                WHERE m.id_modelo=?
                """, id).stream()
                .findFirst().orElseThrow(() -> notFound("Modelo não encontrado"));
        String currentStatus = Objects.toString(current.get("status"));
        if (!TRANSICOES.getOrDefault(currentStatus, Set.of()).contains(input.status())) {
            throw bad("Transição de estado do modelo inválida");
        }
        if (input.status().equals("aprovado") && (blank(input.provedor()) || blank(input.provedorModeloId()))) {
            throw bad("Informe o provedor e o identificador aprovado do modelo");
        }
        if (input.status().equals("rejeitado") && blank(input.motivoRejeicao())) {
            throw bad("Informe o motivo da rejeição");
        }
        if (input.status().equals("aprovado")) {
            db.update("""
                    INSERT INTO comunicacao_modelo_status_evento(id_modelo,status,id_usuario)
                    SELECT active.id_modelo,'inativo',? FROM (
                        SELECT other.id_modelo,latest.status FROM comunicacao_modelo other
                        JOIN LATERAL (SELECT status FROM comunicacao_modelo_status_evento
                            WHERE id_modelo=other.id_modelo ORDER BY id_status DESC LIMIT 1) latest ON true
                        WHERE other.codigo=? AND other.id_modelo<>?
                    ) active WHERE active.status='aprovado'
                    """, actor, current.get("codigo"), id);
        }
        db.update("""
                INSERT INTO comunicacao_modelo_status_evento(
                    id_modelo,status,provedor,provedor_modelo_id,motivo_rejeicao,id_usuario
                ) VALUES (?,?,?,?,?,?)
                """, id, input.status(), clean(input.provedor()), clean(input.provedorModeloId()),
                clean(input.motivoRejeicao()), actor);
        return modelo(id);
    }

    public List<Map<String,Object>> modelos(boolean somenteAprovados) {
        return db.queryForList("""
                SELECT DISTINCT ON (m.codigo) m.id_modelo AS id,m.codigo,m.versao,m.nome,m.canal,m.finalidade,m.categoria,
                    m.idioma,m.conteudo,s.status,s.provedor,s.provedor_modelo_id AS "provedorModeloId",
                    s.motivo_rejeicao AS "motivoRejeicao",m.criado_em AS "criadoEm"
                FROM comunicacao_modelo m JOIN LATERAL (
                    SELECT status,provedor,provedor_modelo_id,motivo_rejeicao
                    FROM comunicacao_modelo_status_evento WHERE id_modelo=m.id_modelo
                    ORDER BY id_status DESC LIMIT 1
                ) s ON true WHERE (NOT ? OR s.status='aprovado')
                ORDER BY codigo,versao DESC
                """, somenteAprovados);
    }

    public List<Map<String,Object>> versoes(String code) {
        return db.queryForList("""
                SELECT m.id_modelo AS id,m.codigo,m.versao,m.nome,m.canal,m.finalidade,m.categoria,m.idioma,m.conteudo,
                    s.status,s.provedor,s.provedor_modelo_id AS "provedorModeloId",
                    s.motivo_rejeicao AS "motivoRejeicao",m.criado_em AS "criadoEm"
                FROM comunicacao_modelo m JOIN LATERAL (
                    SELECT status,provedor,provedor_modelo_id,motivo_rejeicao
                    FROM comunicacao_modelo_status_evento WHERE id_modelo=m.id_modelo
                    ORDER BY id_status DESC LIMIT 1
                ) s ON true WHERE m.codigo=? ORDER BY m.versao DESC
                """, code);
    }

    public List<Map<String,Object>> historicoModelo(long id) {
        if (!Boolean.TRUE.equals(db.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM comunicacao_modelo WHERE id_modelo=?)", Boolean.class, id))) {
            throw notFound("Modelo não encontrado");
        }
        return db.queryForList("""
                SELECT s.id_status AS id,s.status,s.provedor,s.provedor_modelo_id AS "provedorModeloId",
                    s.motivo_rejeicao AS "motivoRejeicao",s.criado_em AS "criadoEm",u.nome AS responsavel
                FROM comunicacao_modelo_status_evento s JOIN usuario u ON u.id=s.id_usuario
                WHERE s.id_modelo=? ORDER BY s.id_status
                """, id);
    }

    public Decision avaliar(int patient, String phone, String channel, String purpose, boolean essential,
                             Long model, String origin, Integer actor, boolean record) {
        String sql = record
                ? "SELECT * FROM comunicacao_registrar_tentativa(?,?,?,?,?,?,?,?)"
                : "SELECT NULL::bigint AS id_tentativa,* FROM comunicacao_avaliar(?,?,?,?,?,?)";
        Object[] params = record
                ? new Object[]{patient,phone,channel,purpose,essential,model,origin,actor}
                : new Object[]{patient,phone,channel,purpose,essential,model};
        var row = db.queryForMap(sql, params);
        return new Decision(number(row.get("id_tentativa")), Boolean.TRUE.equals(row.get("permitido")),
                Objects.toString(row.get("motivo")), number(row.get("id_consentimento")),
                number(row.get("id_modelo")), integer(row.get("modelo_versao")));
    }

    public Long modeloAprovado(String code, String content) {
        return db.queryForList("""
                SELECT m.id_modelo FROM comunicacao_modelo m JOIN LATERAL (
                    SELECT status FROM comunicacao_modelo_status_evento WHERE id_modelo=m.id_modelo
                    ORDER BY id_status DESC LIMIT 1
                ) s ON true WHERE m.codigo=? AND s.status='aprovado' AND m.conteudo=?
                ORDER BY m.versao DESC LIMIT 1
                """, code, content).stream().map(row -> ((Number) row.get("id_modelo")).longValue())
                .findFirst().orElse(null);
    }

    private Map<String,Object> consentimento(long id) {
        return db.queryForMap("""
                SELECT id_consentimento AS id,id_paciente AS "pacienteId",canal,finalidade,decisao,origem,
                    texto_aceito AS "textoAceito",evidencia_referencia AS "evidenciaReferencia",
                    ocorrido_em AS "ocorridoEm",criado_em AS "registradoEm" FROM comunicacao_consentimento_evento
                WHERE id_consentimento=?
                """, id);
    }
    private Map<String,Object> modelo(long id) {
        return db.queryForMap("""
                SELECT m.id_modelo AS id,m.codigo,m.versao,m.nome,m.canal,m.finalidade,m.categoria,m.idioma,m.conteudo,
                    s.status,s.provedor,s.provedor_modelo_id AS "provedorModeloId",
                    s.motivo_rejeicao AS "motivoRejeicao",m.criado_em AS "criadoEm"
                FROM comunicacao_modelo m JOIN LATERAL (
                    SELECT status,provedor,provedor_modelo_id,motivo_rejeicao
                    FROM comunicacao_modelo_status_evento WHERE id_modelo=m.id_modelo
                    ORDER BY id_status DESC LIMIT 1
                ) s ON true WHERE m.id_modelo=?
                """, id);
    }
    private static Long number(Object value) { return value == null ? null : ((Number) value).longValue(); }
    private static Integer integer(Object value) { return value == null ? null : ((Number) value).intValue(); }
    private static String clean(String value) { return blank(value) ? null : value.strip(); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT,message); }
    private static ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND,message); }

    public record Decision(Long tentativaId, boolean permitido, String motivo, Long consentimentoId,
                           Long modeloId, Integer modeloVersao) {}
}
