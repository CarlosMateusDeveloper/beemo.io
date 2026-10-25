package br.com.clinica.service;

import br.com.clinica.dto.PosVendaCsvConfigRequest;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.text.Normalizer;
import java.util.*;

@Service
public class PosVendaCsvImportService {
    private static final ObjectMapper JSON = new ObjectMapper();
    static final long MAX_BYTES = 5L * 1024 * 1024;
    static final int MAX_ROWS = 20_000;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            MediaType.TEXT_PLAIN_VALUE, "text/csv", "application/csv", "application/vnd.ms-excel");
    static final List<String> CAMPOS = List.of("pacienteId", "pacienteNome", "telefone", "atendimentoId",
            "dataHora", "profissional", "especialidade", "tipo", "situacao", "atualizadoEm",
            "consentimentoDecisao", "consentimentoCanal", "consentimentoFinalidade",
            "consentimentoTexto", "consentimentoData", "consentimentoEvidencia");
    private static final Set<String> OBRIGATORIOS = Set.of("pacienteId", "pacienteNome", "telefone",
            "atendimentoId", "dataHora", "profissional", "especialidade", "tipo", "situacao");
    private static final Map<String, Integer> LIMITES = Map.of(
            "pacienteId", 160,
            "pacienteNome", 200,
            "atendimentoId", 160,
            "profissional", 200,
            "especialidade", 160,
            "tipo", 160,
            "situacao", 120
    );
    private static final Set<String> SITUACOES = Set.of("AGENDADO", "CONFIRMADO", "FALTOU", "REALIZADO",
            "CANCELADO", "DESCONHECIDO");
    private static final Map<String, List<String>> SINONIMOS = Map.ofEntries(
            Map.entry("pacienteId", List.of("paciente_id", "id_paciente", "codigo_paciente", "cod_paciente", "patient_id")),
            Map.entry("pacienteNome", List.of("paciente", "nome_paciente", "paciente_nome", "nome", "patient_name")),
            Map.entry("telefone", List.of("telefone", "celular", "whatsapp", "fone", "phone", "mobile")),
            Map.entry("atendimentoId", List.of("atendimento_id", "id_atendimento", "consulta_id", "agendamento_id", "codigo_atendimento", "codigo_agendamento", "appointment_id")),
            Map.entry("dataHora", List.of("data_hora", "datahora", "inicio", "data_atendimento", "data_consulta", "scheduled_at", "appointment_date")),
            Map.entry("profissional", List.of("profissional", "medico", "dentista", "responsavel_clinico", "doctor", "provider")),
            Map.entry("especialidade", List.of("especialidade", "area", "servico", "specialty")),
            Map.entry("tipo", List.of("tipo", "procedimento", "servico", "tipo_atendimento", "consulta_tipo", "procedure", "appointment_type")),
            Map.entry("situacao", List.of("situacao", "status", "status_atendimento", "situacao_atendimento")),
            Map.entry("atualizadoEm", List.of("atualizado_em", "data_atualizacao", "ultima_alteracao")),
            Map.entry("consentimentoDecisao", List.of("consentimento", "consentimento_decisao", "opt_in", "aceite_comunicacao")),
            Map.entry("consentimentoCanal", List.of("consentimento_canal", "canal_consentimento")),
            Map.entry("consentimentoFinalidade", List.of("consentimento_finalidade", "finalidade_consentimento")),
            Map.entry("consentimentoTexto", List.of("consentimento_texto", "texto_aceite", "termo_aceito")),
            Map.entry("consentimentoData", List.of("consentimento_data", "data_consentimento", "aceite_em")),
            Map.entry("consentimentoEvidencia", List.of("consentimento_evidencia", "evidencia_consentimento", "aceite_id"))
    );
    private static final List<DateTimeFormatter> LOCAL_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm[:ss]"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm[:ss]")
    );

    private final JdbcTemplate db;
    private final Clock clock;
    private final ImportFileProtectionService fileProtection;

    @Autowired
    public PosVendaCsvImportService(JdbcTemplate db, Clock clock, ImportFileProtectionService fileProtection) {
        this.db = db;
        this.clock = clock;
        this.fileProtection = fileProtection;
    }

    PosVendaCsvImportService(JdbcTemplate db, Clock clock) {
        this(db, clock, new ImportFileProtectionService("", new java.security.SecureRandom()));
    }

    public Map<String, Object> preview(MultipartFile file, String fuso) {
        ZoneId zone = zone(fuso);
        CsvDocument csv = read(file);
        Map<String, String> mapping = detectarMapeamento(csv.headers());
        Map<String, String> statusMapping = new LinkedHashMap<>();
        Map<String, Integer> statusCount = new LinkedHashMap<>();
        Integer statusIndex = index(csv.headers(), mapping.get("situacao"));
        if (statusIndex != null) {
            for (CsvRow row : csv.rows()) {
                String original = cell(row, statusIndex).trim();
                if (!original.isEmpty()) statusCount.merge(original, 1, Integer::sum);
            }
            statusCount.keySet().forEach(value -> statusMapping.put(value, sugerirSituacao(value)));
        }
        PosVendaCsvConfigRequest config = new PosVendaCsvConfigRequest("preview", "Prévia", zone.getId(), mapping, statusMapping);
        return previewResult(file, csv, config, statusCount);
    }

    public Map<String, Object> preview(MultipartFile file, PosVendaCsvConfigRequest config) {
        zone(config.fusoHorario());
        validateConfig(config);
        CsvDocument csv = read(file);
        validateHeaders(csv.headers(), config.colunas());
        Map<String, Integer> statusCount = new LinkedHashMap<>();
        Integer statusIndex = index(csv.headers(), config.colunas().get("situacao"));
        if (statusIndex != null) {
            for (CsvRow row : csv.rows()) {
                String original = cell(row, statusIndex).trim();
                if (!original.isEmpty()) statusCount.merge(original, 1, Integer::sum);
            }
        }
        return previewResult(file, csv, config, statusCount);
    }

    private Map<String, Object> previewResult(MultipartFile file, CsvDocument csv,
            PosVendaCsvConfigRequest config, Map<String, Integer> statusCount) {
        List<Map<String, Object>> rows = new ArrayList<>();
        int valid = 0;
        int errors = 0;
        for (CsvRow row : csv.rows()) {
            Parsed parsed = parse(row, csv.headers(), config);
            if (parsed.problem() == null) valid++; else errors++;
            if (rows.size() < 20) rows.add(previewRow(parsed));
        }
        List<Map<String, Object>> detected = statusCount.entrySet().stream().map(entry -> Map.<String, Object>of(
                "origem", entry.getKey(), "quantidade", entry.getValue(), "sugestao",
                Objects.toString(config.situacoes().get(entry.getKey()), sugerirSituacao(entry.getKey()))
        )).toList();
        var result = new LinkedHashMap<String, Object>();
        result.put("arquivo", Objects.toString(file.getOriginalFilename(), "arquivo.csv"));
        result.put("separador", Character.toString(csv.delimiter()));
        result.put("colunas", csv.headers());
        result.put("mapeamento", config.colunas());
        result.put("camposObrigatorios", OBRIGATORIOS);
        result.put("situacoesDetectadas", detected);
        result.put("totalLinhas", csv.rows().size());
        result.put("linhasValidas", valid);
        result.put("linhasComErro", errors);
        result.put("amostra", rows);
        return result;
    }

    @Transactional
    public Map<String, Object> importar(MultipartFile file, PosVendaCsvConfigRequest config, int author) {
        zone(config.fusoHorario());
        validateConfig(config);
        CsvDocument csv = read(file);
        validateHeaders(csv.headers(), config.colunas());
        int tenant = TenantContext.id();
        long source = source(config);
        String hash = sha256(file);
        ImportFileProtectionService.ProtectedFile protectedFile = fileProtection.protect(fileBytes(file), tenant, hash);
        long importId = db.queryForObject("""
                INSERT INTO integracao_importacao(
                    id_clinica,id_fonte,arquivo_nome,arquivo_sha256,arquivo_conteudo_criptografado,
                    arquivo_nonce,arquivo_tamanho,arquivo_chave_versao,fuso_horario,status,criado_por
                ) VALUES (?,?,?,?,?,?,?,?,?,'PROCESSANDO',?) RETURNING id_importacao
                """, Long.class, tenant, source, safeName(file.getOriginalFilename()), hash,
                protectedFile.ciphertext(), protectedFile.nonce(), protectedFile.originalSize(),
                protectedFile.keyVersion(), config.fusoHorario(), author);

        Counters counters = new Counters();
        for (CsvRow row : csv.rows()) {
            Parsed parsed = parse(row, csv.headers(), config);
            if (parsed.problem() != null) {
                saveLine(importId, row.number(), parsed, parsed.problem().review() ? "REVISAO" : "ERRO");
                if (parsed.problem().review()) counters.review++; else counters.errors++;
                continue;
            }
            Outcome outcome = importRow(source, importId, parsed);
            saveLine(importId, row.number(), parsed, outcome);
            switch (outcome.result()) {
                case "IMPORTADA" -> counters.imported++;
                case "ATUALIZADA" -> counters.updated++;
                case "SEM_ALTERACAO" -> counters.unchanged++;
                case "REVISAO" -> counters.review++;
                default -> counters.errors++;
            }
        }
        String status = counters.errors > 0 || counters.review > 0 ? "CONCLUIDA_COM_ERROS" : "CONCLUIDA";
        db.update("""
                UPDATE integracao_importacao SET status=?,total_linhas=?,importadas=?,atualizadas=?,sem_alteracao=?,
                    revisao=?,erros=?,concluido_em=now() WHERE id_importacao=?
                """, status, csv.rows().size(), counters.imported, counters.updated, counters.unchanged,
                counters.review, counters.errors, importId);
        db.update("UPDATE integracao_fonte_dados SET ultima_sincronizacao=now(),atualizado_em=now() WHERE id_fonte=?", source);
        return detail(importId);
    }

    public List<Map<String, Object>> list() {
        return db.queryForList("""
                SELECT i.id_importacao AS id,i.arquivo_nome AS arquivo,i.status,i.total_linhas AS total,
                    i.importadas,i.atualizadas,i.sem_alteracao AS "semAlteracao",i.revisao,i.erros,
                    i.criado_em::text AS "iniciadaEm",i.concluido_em::text AS "concluidaEm",
                    i.rollback_status AS "rollbackStatus",i.desfeita_em::text AS "desfeitaEm",
                    (i.arquivo_conteudo_criptografado IS NOT NULL) AS "arquivoProtegido",
                    f.codigo AS "fonteCodigo",f.nome AS fonte,f.ultima_sincronizacao::text AS "ultimaSincronizacao",
                    u.nome AS responsavel
                FROM integracao_importacao i JOIN integracao_fonte_dados f ON f.id_fonte=i.id_fonte
                JOIN usuario u ON u.id=i.criado_por ORDER BY i.id_importacao DESC LIMIT 50
                """);
    }

    public Map<String, Object> detail(long id) {
        Map<String, Object> batch = db.queryForList("""
                SELECT i.id_importacao AS id,i.arquivo_nome AS arquivo,i.arquivo_sha256 AS sha256,i.status,
                    i.total_linhas AS total,i.importadas,i.atualizadas,i.sem_alteracao AS "semAlteracao",
                    i.revisao,i.erros,i.fuso_horario AS fuso,i.criado_em::text AS "iniciadaEm",
                    i.concluido_em::text AS "concluidaEm",i.rollback_status AS "rollbackStatus",
                    i.rollback_relatorio::text AS "rollbackRelatorio",i.desfeita_em::text AS "desfeitaEm",
                    (i.arquivo_conteudo_criptografado IS NOT NULL) AS "arquivoProtegido",
                    i.arquivo_tamanho AS "arquivoTamanho",
                    f.codigo AS "fonteCodigo",f.nome AS fonte,u.nome AS responsavel
                FROM integracao_importacao i JOIN integracao_fonte_dados f ON f.id_fonte=i.id_fonte
                JOIN usuario u ON u.id=i.criado_por WHERE i.id_importacao=?
                """, id).stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Importação não encontrada"));
        List<Map<String, Object>> issues = db.queryForList("""
                SELECT numero_linha AS linha,paciente_externo AS "pacienteId",
                    atendimento_externo AS "atendimentoId",resultado,codigo,campo,mensagem
                FROM integracao_importacao_linha WHERE id_importacao=? AND resultado IN ('REVISAO','ERRO')
                ORDER BY numero_linha LIMIT 500
                """, id);
        return Map.of("importacao", batch, "pendencias", issues);
    }

    @Transactional
    public Map<String,Object> rollback(long id, int author) {
        Map<String,Object> batch = db.queryForList("""
                SELECT id_importacao,id_fonte,rollback_status FROM integracao_importacao
                WHERE id_importacao=? FOR UPDATE
                """, id).stream().findFirst().orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Importação não encontrada"));
        if (batch.get("rollback_status") != null) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Esta importação já possui um rollback registrado");

        long source = ((Number) batch.get("id_fonte")).longValue();
        List<Map<String,Object>> created = db.queryForList("""
                SELECT a.id_atendimento_externo AS id,a.identificador_externo AS identificador,
                    c.id AS caso,c.status AS caso_status,
                    (SELECT count(*) FROM pos_venda_evento e WHERE e.id_caso=c.id AND e.tipo<>'falta') AS alteracoes
                FROM integracao_importacao_linha l
                JOIN integracao_atendimento_externo a ON a.id_fonte=?
                    AND a.identificador_externo=l.atendimento_externo
                LEFT JOIN pos_venda_caso c ON c.id_atendimento_externo_origem=a.id_atendimento_externo
                WHERE l.id_importacao=? AND l.resultado='IMPORTADA'
                ORDER BY l.numero_linha
                """, source, id);
        int removed = 0;
        List<String> assisted = new ArrayList<>();
        for (Map<String,Object> row : created) {
            Number caseId = (Number) row.get("caso");
            boolean untouched = caseId == null || (((Number) row.get("alteracoes")).intValue() == 0
                    && Set.of("pendente", "nao_contatar", "registro_corrigido")
                    .contains(Objects.toString(row.get("caso_status"), "pendente")));
            if (!untouched) {
                assisted.add(Objects.toString(row.get("identificador")));
                continue;
            }
            if (caseId != null) {
                db.update("DELETE FROM pos_venda_evento WHERE id_caso=?", caseId.longValue());
                db.update("DELETE FROM pos_venda_caso WHERE id=?", caseId.longValue());
            }
            removed += db.update("DELETE FROM integracao_atendimento_externo WHERE id_atendimento_externo=? AND id_importacao=?",
                    ((Number) row.get("id")).longValue(), id);
        }
        Integer updated = db.queryForObject("""
                SELECT count(*) FROM integracao_importacao_linha
                WHERE id_importacao=? AND resultado IN ('ATUALIZADA','REVISAO')
                """, Integer.class, id);
        if (updated != null && updated > 0) assisted.add(updated + " registro(s) preexistente(s) atualizado(s)");
        db.update("""
                DELETE FROM comunicacao_consentimento_evento
                WHERE origem='importacao_sem_evidencia' AND evidencia_referencia=?
                """, "importacao:" + id + ":sem-evidencia");
        String status = assisted.isEmpty() ? "CONCLUIDO" : "CORRECAO_ASSISTIDA";
        Map<String,Object> report = new LinkedHashMap<>();
        report.put("atendimentosRemovidos", removed);
        report.put("itensParaCorrecaoAssistida", assisted);
        db.update("""
                UPDATE integracao_importacao SET rollback_status=?,rollback_relatorio=?::jsonb,
                    desfeita_em=now(),desfeita_por=? WHERE id_importacao=?
                """, status, toJson(report), author, id);
        return detail(id);
    }

    private static String toJson(Object value) {
        try { return JSON.writeValueAsString(value); }
        catch (JacksonException e) { throw new IllegalStateException("Não foi possível registrar o rollback", e); }
    }

    private Outcome importRow(long source, long importId, Parsed row) {
        int tenant = TenantContext.id();
        List<Map<String, Object>> existingPatient = db.queryForList("""
                SELECT id_paciente_externo,nome,telefone,atualizado_em_fonte FROM integracao_paciente_externo
                WHERE id_fonte=? AND identificador_externo=? FOR UPDATE
                """, source, row.patientId());
        long patient;
        if (existingPatient.isEmpty()) {
            patient = db.queryForObject("""
                    INSERT INTO integracao_paciente_externo(id_clinica,id_fonte,identificador_externo,nome,telefone,atualizado_em_fonte)
                    VALUES (?,?,?,?,?,?) RETURNING id_paciente_externo
                    """, Long.class, tenant, source, row.patientId(), row.patientName(), row.phone(), row.sourceUpdated());
        } else {
            Map<String, Object> found = existingPatient.getFirst();
            patient = ((Number) found.get("id_paciente_externo")).longValue();
            if (newer(row.sourceUpdated(), instant(found.get("atualizado_em_fonte")))) {
                db.update("""
                        UPDATE integracao_paciente_externo SET nome=?,telefone=?,atualizado_em_fonte=?,atualizado_em=now()
                        WHERE id_paciente_externo=?
                        """, row.patientName(), row.phone(), row.sourceUpdated(), patient);
            }
        }
        preservarConsentimentoImportado(importId, patient, row);

        List<Map<String, Object>> existing = db.queryForList("""
                SELECT id_atendimento_externo,id_paciente_externo,agendado_em,profissional,especialidade,tipo,
                    situacao_origem,situacao_normalizada,atualizado_em_fonte
                FROM integracao_atendimento_externo WHERE id_fonte=? AND identificador_externo=? FOR UPDATE
                """, source, row.appointmentId());
        if (existing.isEmpty()) {
            db.update("""
                    INSERT INTO integracao_atendimento_externo(id_clinica,id_fonte,id_paciente_externo,
                        identificador_externo,agendado_em,fuso_horario,profissional,especialidade,tipo,
                        situacao_origem,situacao_normalizada,atualizado_em_fonte,id_importacao)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """, tenant, source, patient, row.appointmentId(), row.scheduled(), row.zone(), row.professional(),
                    row.specialty(), row.type(), row.sourceStatus(), row.status(), row.sourceUpdated(), importId);
            return new Outcome("IMPORTADA");
        }
        Map<String, Object> found = existing.getFirst();
        if (((Number) found.get("id_paciente_externo")).longValue() != patient) {
            return new Outcome("REVISAO", "atendimento_paciente_divergente", "atendimentoId",
                    "O identificador do atendimento já pertence a outro paciente externo.");
        }
        Instant previousUpdate = instant(found.get("atualizado_em_fonte"));
        if (!newer(row.sourceUpdated(), previousUpdate)) return new Outcome("SEM_ALTERACAO");
        boolean same = same(found, row);
        if (same) return new Outcome("SEM_ALTERACAO");
        db.update("""
                UPDATE integracao_atendimento_externo SET agendado_em=?,fuso_horario=?,profissional=?,especialidade=?,
                    tipo=?,situacao_origem=?,situacao_normalizada=?,atualizado_em_fonte=?,id_importacao=?,atualizado_em=now()
                WHERE id_atendimento_externo=?
                """, row.scheduled(), row.zone(), row.professional(), row.specialty(), row.type(), row.sourceStatus(),
                row.status(), row.sourceUpdated(), importId, found.get("id_atendimento_externo"));
        return new Outcome("ATUALIZADA");
    }

    private void preservarConsentimentoImportado(long importId, long patient, Parsed row) {
        String decision = row.consentDecision();
        boolean hasEvidence = decision != null;
        String source = hasEvidence ? "importacao" : "importacao_sem_evidencia";
        String evidence = hasEvidence ? blankToNull(row.consentEvidence())
                : "importacao:" + importId + ":sem-evidencia";
        OffsetDateTime occurred = hasEvidence ? row.consentOccurred() : OffsetDateTime.now(clock);
        String channel = hasEvidence ? row.consentChannel() : "todos";
        String purpose = hasEvidence ? row.consentPurpose() : "todos";
        String effectiveDecision = hasEvidence ? decision : "revogado";
        db.update("""
                INSERT INTO comunicacao_consentimento_evento(
                    id_paciente_externo,canal,finalidade,decisao,origem,texto_aceito,
                    evidencia_referencia,ocorrido_em
                ) SELECT ?,?,?,?,?,?,?,?
                WHERE NOT EXISTS (
                    SELECT 1 FROM comunicacao_consentimento_evento
                    WHERE id_paciente_externo=? AND origem=? AND coalesce(evidencia_referencia,'')=coalesce(?,'')
                      AND canal=? AND finalidade=? AND decisao=?
                )
                """, patient, channel, purpose, effectiveDecision, source,
                hasEvidence ? blankToNull(row.consentText()) : null, evidence, occurred,
                patient, source, evidence, channel, purpose, effectiveDecision);
    }

    private void saveLine(long importId, int line, Parsed parsed, String result) {
        Problem p = parsed.problem();
        saveLine(importId, line, parsed, new Outcome(result,
                p == null ? null : p.code(), p == null ? null : p.field(), p == null ? null : p.message()));
    }

    private void saveLine(long importId, int line, Parsed parsed, Outcome outcome) {
        db.update("""
                INSERT INTO integracao_importacao_linha(id_importacao,numero_linha,paciente_externo,
                    atendimento_externo,resultado,codigo,campo,mensagem) VALUES (?,?,?,?,?,?,?,?)
                """, importId, line, blankToNull(parsed.patientId()), blankToNull(parsed.appointmentId()),
                outcome.result(), outcome.code(), outcome.field(), outcome.message());
    }

    private long source(PosVendaCsvConfigRequest config) {
        Integer tenant = TenantContext.id();
        return db.queryForObject("""
                INSERT INTO integracao_fonte_dados(id_clinica,codigo,nome,fuso_horario)
                VALUES (?,?,?,?) ON CONFLICT(id_clinica,codigo) DO UPDATE
                SET nome=excluded.nome,fuso_horario=excluded.fuso_horario,atualizado_em=now()
                RETURNING id_fonte
                """, Long.class, tenant, config.fonteCodigo(), config.fonteNome().trim(), config.fusoHorario());
    }

    private static boolean same(Map<String, Object> found, Parsed row) {
        return Objects.equals(instant(found.get("agendado_em")), row.scheduled())
                && Objects.equals(found.get("profissional"), row.professional())
                && Objects.equals(found.get("especialidade"), row.specialty())
                && Objects.equals(found.get("tipo"), row.type())
                && Objects.equals(found.get("situacao_origem"), row.sourceStatus())
                && Objects.equals(found.get("situacao_normalizada"), row.status());
    }

    static boolean newer(Instant incoming, Instant existing) {
        if (incoming == null) return existing == null;
        return existing == null || incoming.isAfter(existing);
    }

    private static Instant instant(Object value) {
        if (value == null) return null;
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof OffsetDateTime dateTime) return dateTime.toInstant();
        return Instant.parse(value.toString());
    }

    private static Parsed parse(CsvRow row, List<String> headers, PosVendaCsvConfigRequest config) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String field : CAMPOS) {
            Integer position = index(headers, config.colunas().get(field));
            values.put(field, position == null ? "" : cell(row, position).trim());
        }
        for (String field : OBRIGATORIOS) {
            if (values.get(field).isBlank()) {
                boolean review = field.equals("pacienteId") || field.equals("atendimentoId");
                return Parsed.problem(values, new Problem(review ? "identificador_ausente" : "campo_obrigatorio",
                        field, "O campo obrigatório está vazio.", review));
            }
        }
        for (var limit : LIMITES.entrySet()) {
            if (values.get(limit.getKey()).length() > limit.getValue()) {
                return Parsed.problem(values, new Problem("campo_muito_longo", limit.getKey(),
                        "O valor excede o limite de " + limit.getValue() + " caracteres.", false));
            }
        }
        String phone = digits(values.get("telefone"));
        if (phone.length() < 10 || phone.length() > 15) {
            return Parsed.problem(values, new Problem("telefone_invalido", "telefone",
                    "Informe um telefone com DDD e entre 10 e 15 dígitos.", false));
        }
        ZoneId zone;
        Instant scheduled;
        Instant updated = null;
        try {
            zone = zone(config.fusoHorario());
            scheduled = date(values.get("dataHora"), zone);
            if (!values.get("atualizadoEm").isBlank()) updated = date(values.get("atualizadoEm"), zone);
        } catch (ResponseStatusException | DateTimeParseException exception) {
            return Parsed.problem(values, new Problem("data_invalida", "dataHora",
                    "Use data e hora ISO ou dd/MM/aaaa HH:mm e confira o fuso da fonte.", false));
        }
        String status = status(config.situacoes(), values.get("situacao"));
        if (status == null) {
            return Parsed.problem(values, new Problem("situacao_sem_mapeamento", "situacao",
                    "Mapeie esta situação para uma situação reconhecida pelo Clinisis.", false));
        }
        String consentDecision;
        String consentChannel;
        String consentPurpose;
        try {
            consentDecision = consentDecision(values.get("consentimentoDecisao"));
            consentChannel = consentValue(values.get("consentimentoCanal"), "todos",
                    Set.of("whatsapp", "email", "sms", "telefone", "todos"));
            consentPurpose = consentValue(values.get("consentimentoFinalidade"), "todos",
                    Set.of("atendimento", "pos_venda", "campanha", "transacional", "todos"));
        } catch (ResponseStatusException exception) {
            return Parsed.problem(values, new Problem("consentimento_invalido", "consentimentoDecisao",
                    exception.getReason(), false));
        }
        OffsetDateTime consentOccurred = null;
        if (consentDecision != null) {
            if (consentDecision.equals("concedido") && values.get("consentimentoTexto").isBlank()) {
                return Parsed.problem(values, new Problem("consentimento_sem_texto", "consentimentoTexto",
                        "Um consentimento concedido precisa preservar o texto aceito.", false));
            }
            if (values.get("consentimentoData").isBlank()) {
                return Parsed.problem(values, new Problem("consentimento_sem_data", "consentimentoData",
                        "Informe quando o consentimento foi concedido ou revogado.", false));
            }
            try {
                consentOccurred = date(values.get("consentimentoData"), zone).atZone(zone).toOffsetDateTime();
            } catch (DateTimeParseException exception) {
                return Parsed.problem(values, new Problem("consentimento_data_invalida", "consentimentoData",
                        "Use data e hora ISO ou dd/MM/aaaa HH:mm.", false));
            }
        }
        return new Parsed(values.get("pacienteId"), values.get("pacienteNome"), phone,
                values.get("atendimentoId"), scheduled, zone.getId(), values.get("profissional"),
                values.get("especialidade"), values.get("tipo"), values.get("situacao"), status, updated, null,
                values, consentDecision, consentChannel, consentPurpose, values.get("consentimentoTexto"),
                consentOccurred, values.get("consentimentoEvidencia"));
    }

    private static Map<String, Object> previewRow(Parsed row) {
        var result = new LinkedHashMap<String, Object>();
        result.put("pacienteId", row.patientId());
        result.put("paciente", row.patientName());
        result.put("telefone", row.phone());
        result.put("atendimentoId", row.appointmentId());
        result.put("dataHora", row.scheduled());
        result.put("situacao", row.sourceStatus());
        result.put("situacaoNormalizada", row.status());
        result.put("consentimento", row.consentDecision() == null ? "Bloqueado até comprovação" : row.consentDecision());
        result.put("valida", row.problem() == null);
        if (row.problem() != null) {
            result.put("codigo", row.problem().code());
            result.put("campo", row.problem().field());
            result.put("mensagem", row.problem().message());
        }
        return result;
    }

    private static String status(Map<String, String> mapping, String raw) {
        for (var entry : mapping.entrySet()) {
            if (normalize(entry.getKey()).equals(normalize(raw))) {
                String status = entry.getValue() == null ? "" : entry.getValue().trim().toUpperCase(Locale.ROOT);
                return SITUACOES.contains(status) ? status : null;
            }
        }
        return null;
    }

    static String sugerirSituacao(String value) {
        String normalized = normalize(value);
        if (Set.of("faltou", "falta", "nao_compareceu", "no_show").contains(normalized)) return "FALTOU";
        if (Set.of("realizado", "atendido", "presente", "compareceu", "concluido", "completed").contains(normalized)) return "REALIZADO";
        if (Set.of("cancelado", "cancelada", "desmarcado", "canceled", "cancelled").contains(normalized)) return "CANCELADO";
        if (Set.of("confirmado", "confirmada", "confirmed").contains(normalized)) return "CONFIRMADO";
        if (Set.of("agendado", "agendada", "marcado", "scheduled").contains(normalized)) return "AGENDADO";
        if (Set.of("autorizado", "faturado", "pago", "em_aberto").contains(normalized)) return "DESCONHECIDO";
        return "";
    }

    private static Instant date(String value, ZoneId zone) {
        try { return OffsetDateTime.parse(value).toInstant(); } catch (DateTimeParseException ignored) {}
        try { return Instant.parse(value); } catch (DateTimeParseException ignored) {}
        for (DateTimeFormatter format : LOCAL_FORMATS) {
            try { return LocalDateTime.parse(value, format).atZone(zone).toInstant(); }
            catch (DateTimeParseException ignored) {}
        }
        throw new DateTimeParseException("Data inválida", value, 0);
    }

    private static ZoneId zone(String value) {
        try { return ZoneId.of(value); }
        catch (DateTimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fuso horário inválido");
        }
    }

    private static void validateConfig(PosVendaCsvConfigRequest config) {
        for (String required : OBRIGATORIOS) {
            if (config.colunas().get(required) == null || config.colunas().get(required).isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mapeie a coluna obrigatória: " + required);
            }
        }
        for (String value : config.situacoes().values()) {
            if (value == null || !SITUACOES.contains(value.toUpperCase(Locale.ROOT))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mapeamento de situação inválido");
            }
        }
    }

    private static void validateHeaders(List<String> headers, Map<String, String> mapping) {
        for (var entry : mapping.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isBlank() && index(headers, entry.getValue()) == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "A coluna mapeada não existe no arquivo: " + entry.getValue());
            }
        }
    }

    static Map<String, String> detectarMapeamento(List<String> headers) {
        Map<String, String> mapping = new LinkedHashMap<>();
        for (String field : CAMPOS) {
            String detected = "";
            for (String synonym : SINONIMOS.getOrDefault(field, List.of())) {
                for (String header : headers) {
                    if (normalize(header).equals(synonym)) { detected = header; break; }
                }
                if (!detected.isEmpty()) break;
            }
            mapping.put(field, detected);
        }
        return mapping;
    }

    private static CsvDocument read(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione um arquivo CSV");
        if (file.getSize() > MAX_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                "O CSV deve ter no máximo 5 MB");
        String contentType = Objects.toString(file.getContentType(), "").toLowerCase(Locale.ROOT);
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Envie um CSV com tipo de conteúdo permitido");
        }
        String name = Objects.toString(file.getOriginalFilename(), "").toLowerCase(Locale.ROOT);
        if (!name.endsWith(".csv")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Envie um arquivo com extensão .csv");
        try {
            byte[] bytes = file.getBytes();
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            if (text.startsWith("\uFEFF")) text = text.substring(1);
            if (text.chars().anyMatch(c -> c == 0 || (c < 32 && c != '\n' && c != '\r' && c != '\t')))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O CSV contém caracteres de controle inválidos");
            char delimiter = detectDelimiter(text);
            List<List<String>> records = parseCsv(text, delimiter);
            if (records.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O CSV está vazio");
            List<String> headers = records.getFirst().stream().map(String::trim).toList();
            if (headers.stream().anyMatch(String::isBlank)
                    || new HashSet<>(headers.stream().map(PosVendaCsvImportService::normalize).toList()).size() != headers.size()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O cabeçalho contém colunas vazias ou repetidas");
            }
            if (records.size() - 1 > MAX_ROWS) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "O CSV deve ter no máximo 20.000 linhas");
            List<CsvRow> rows = new ArrayList<>();
            for (int i = 1; i < records.size(); i++) {
                List<String> values = records.get(i);
                if (values.stream().allMatch(String::isBlank)) continue;
                rows.add(new CsvRow(i + 1, values));
            }
            if (rows.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O CSV não possui linhas de dados");
            }
            return new CsvDocument(headers, rows, delimiter);
        } catch (CharacterCodingException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Salve o arquivo CSV em UTF-8");
        } catch (java.io.IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Não foi possível ler o arquivo CSV");
        }
    }

    static List<List<String>> parseCsv(String text, char delimiter) {
        List<List<String>> records = new ArrayList<>();
        List<String> record = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (current == '"') {
                if (quoted && i + 1 < text.length() && text.charAt(i + 1) == '"') { field.append('"'); i++; }
                else quoted = !quoted;
            } else if (current == delimiter && !quoted) {
                record.add(field.toString()); field.setLength(0);
            } else if ((current == '\n' || current == '\r') && !quoted) {
                if (current == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                record.add(field.toString()); field.setLength(0); records.add(record); record = new ArrayList<>();
            } else field.append(current);
        }
        if (quoted) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O CSV possui aspas não fechadas");
        if (!record.isEmpty() || !field.isEmpty()) { record.add(field.toString()); records.add(record); }
        return records;
    }

    private static char detectDelimiter(String text) {
        int comma = 0, semicolon = 0;
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (current == '"') quoted = !quoted;
            else if (!quoted && (current == '\r' || current == '\n')) break;
            else if (!quoted && current == ',') comma++;
            else if (!quoted && current == ';') semicolon++;
        }
        if (comma == 0 && semicolon == 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Não foi possível identificar o separador do CSV");
        return semicolon >= comma ? ';' : ',';
    }

    private static Integer index(List<String> headers, String header) {
        if (header == null || header.isBlank()) return null;
        for (int i = 0; i < headers.size(); i++) if (headers.get(i).equals(header)) return i;
        return null;
    }

    private static String cell(CsvRow row, int index) { return index < row.values().size() ? row.values().get(index) : ""; }
    private static String digits(String value) { return value == null ? "" : value.replaceAll("\\D", ""); }
    private static String consentDecision(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) return null;
        if (Set.of("sim", "true", "1", "aceito", "concedido", "opt_in").contains(normalized)) return "concedido";
        if (Set.of("nao", "false", "0", "revogado", "recusado", "opt_out").contains(normalized)) return "revogado";
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valor de consentimento não reconhecido");
    }
    private static String consentValue(String value, String fallback, Set<String> allowed) {
        String normalized = normalize(value);
        if (normalized.isBlank()) return fallback;
        if (!allowed.contains(normalized)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Canal ou finalidade de consentimento inválido");
        return normalized;
    }
    private static String normalize(String value) {
        String plain = Normalizer.normalize(Objects.toString(value, ""), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return plain.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
    }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value; }
    private static String safeName(String value) {
        String name = Objects.toString(value, "arquivo.csv").replace("\\", "_").replace("/", "_");
        return name.length() <= 255 ? name : name.substring(name.length() - 255);
    }
    private static String sha256(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = new ByteArrayInputStream(file.getBytes())) {
                byte[] buffer = new byte[8192]; int read;
                while ((read = input.read(buffer)) >= 0) digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException | java.io.IOException exception) {
            throw new IllegalStateException("Não foi possível identificar o arquivo", exception);
        }
    }

    private static byte[] fileBytes(MultipartFile file) {
        try { return file.getBytes(); }
        catch (java.io.IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Não foi possível ler o arquivo CSV");
        }
    }

    private record CsvDocument(List<String> headers, List<CsvRow> rows, char delimiter) {}
    private record CsvRow(int number, List<String> values) {}
    private record Problem(String code, String field, String message, boolean review) {}
    private record Parsed(String patientId, String patientName, String phone, String appointmentId,
                          Instant scheduled, String zone, String professional, String specialty, String type,
                          String sourceStatus, String status, Instant sourceUpdated, Problem problem,
                          Map<String, String> values, String consentDecision, String consentChannel,
                          String consentPurpose, String consentText, OffsetDateTime consentOccurred,
                          String consentEvidence) {
        Parsed(String patientId, String patientName, String phone, String appointmentId, Instant scheduled,
               String zone, String professional, String specialty, String type, String sourceStatus,
               String status, Instant sourceUpdated, Problem problem) {
            this(patientId, patientName, phone, appointmentId, scheduled, zone, professional, specialty,
                    type, sourceStatus, status, sourceUpdated, problem, Map.of(), null, "todos", "todos",
                    null, null, null);
        }
        static Parsed problem(Map<String, String> values, Problem problem) {
            return new Parsed(values.get("pacienteId"), values.get("pacienteNome"), digits(values.get("telefone")),
                    values.get("atendimentoId"), null, null, values.get("profissional"), values.get("especialidade"),
                    values.get("tipo"), values.get("situacao"), null, null, problem, values,
                    null, "todos", "todos", null, null, null);
        }
    }
    private record Outcome(String result, String code, String field, String message) {
        Outcome(String result) { this(result, null, null, null); }
    }
    private static final class Counters { int imported, updated, unchanged, review, errors; }
}
