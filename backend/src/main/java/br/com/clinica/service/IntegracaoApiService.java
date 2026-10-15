package br.com.clinica.service;

import br.com.clinica.dto.IntegracaoEventoRequest;
import br.com.clinica.integration.ClinicaIntegrationAdapters;
import br.com.clinica.integration.IntegracaoPrincipal;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.text.Normalizer;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.zone.ZoneRulesException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class IntegracaoApiService {
    private static final Set<String> RESULTADOS = Set.of(
            "PROCESSANDO","PROCESSADO","SEM_ALTERACAO","REVISAO","ERRO");
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final ClinicaIntegrationAdapters adapters;

    public IntegracaoApiService(JdbcTemplate db, ObjectMapper json, ClinicaIntegrationAdapters adapters) {
        this.db = db; this.json = json; this.adapters = adapters;
    }

    @Transactional
    public Map<String,Object> receber(IntegracaoPrincipal principal, IntegracaoEventoRequest input) {
        if (TenantContext.id() != principal.tenantId()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        Source source = source(principal.fonteId());
        IntegracaoEventoRequest request = adapters.obter(source.adapter()).adaptar(input);
        String payload = payload(request);
        String hash = IntegracaoCredencialService.sha256(payload);
        // Serializa reenvios simultâneos antes da consulta/insert para que a idempotência
        // não dependa de capturar uma violação de unicidade numa transação já abortada.
        String lockKey = principal.tenantId() + ":" + source.id() + ":" + request.eventoId();
        db.query("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", result -> null, lockKey);
        var current = db.queryForList("""
                SELECT id_evento,resultado,payload_hash,tentativas,codigo,mensagem
                FROM integracao_evento WHERE id_fonte=? AND identificador_externo=? FOR UPDATE
                """, source.id(), request.eventoId());
        long eventId;
        int attempts;
        if (current.isEmpty()) {
            eventId = db.queryForObject("""
                    INSERT INTO integracao_evento(
                        id_fonte,id_credencial,identificador_externo,tipo,versao,ocorrido_em,
                        atualizado_em_fonte,payload_hash,payload,resultado
                    ) VALUES (?,?,?,?,?,?,?,?,?::jsonb,'PROCESSANDO') RETURNING id_evento
                    """, Long.class, source.id(), principal.credencialId(), request.eventoId(), request.tipo(),
                    request.versao(), request.ocorridoEm(), request.atualizadoEmFonte(), hash, payload);
            attempts = 1;
        } else {
            var found = current.getFirst();
            if (!Objects.equals(hash, Objects.toString(found.get("payload_hash"), "").trim())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "O eventoId já foi utilizado com outro conteúdo");
            }
            eventId = ((Number) found.get("id_evento")).longValue();
            attempts = ((Number) found.get("tentativas")).intValue();
            String result = Objects.toString(found.get("resultado"));
            if (Set.of("PROCESSADO","SEM_ALTERACAO").contains(result)) {
                return response(eventId, request.eventoId(), result,
                        (String) found.get("codigo"), (String) found.get("mensagem"), attempts, true);
            }
            attempts++;
            db.update("""
                    UPDATE integracao_evento SET resultado='PROCESSANDO',codigo=NULL,mensagem=NULL,
                        tentativas=?,id_credencial=?,atualizado_em=now() WHERE id_evento=?
                    """, attempts, principal.credencialId(), eventId);
        }
        return process(eventId, request, source, attempts, false);
    }

    @Transactional
    public Map<String,Object> reprocessar(long eventId) {
        var row = db.queryForList("""
                SELECT ev.id_evento,ev.identificador_externo,ev.payload::text,ev.resultado,ev.tentativas,
                    f.id_fonte,f.codigo,f.fuso_horario,f.adaptador
                FROM integracao_evento ev JOIN integracao_fonte_dados f ON f.id_fonte=ev.id_fonte
                WHERE ev.id_evento=? FOR UPDATE OF ev
                """, eventId).stream().findFirst().orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Evento não encontrado"));
        String result = Objects.toString(row.get("resultado"));
        if (!Set.of("REVISAO","ERRO").contains(result)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Somente eventos com revisão ou erro podem ser reprocessados");
        }
        IntegracaoEventoRequest request;
        try { request = json.readValue(Objects.toString(row.get("payload")), IntegracaoEventoRequest.class); }
        catch (JacksonException exception) { throw new IllegalStateException("Payload interno inválido", exception); }
        int attempts = ((Number) row.get("tentativas")).intValue() + 1;
        db.update("UPDATE integracao_evento SET resultado='PROCESSANDO',tentativas=?,codigo=NULL,mensagem=NULL,atualizado_em=now() WHERE id_evento=?",
                attempts, eventId);
        return process(eventId, request, new Source(((Number) row.get("id_fonte")).longValue(),
                Objects.toString(row.get("codigo")), Objects.toString(row.get("fuso_horario")),
                Objects.toString(row.get("adaptador"))), attempts, false);
    }

    public Map<String,Object> estado(IntegracaoPrincipal principal) {
        if (TenantContext.id() != principal.tenantId()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return sourceState(principal.fonteId());
    }

    public List<Map<String,Object>> fontes() {
        return db.queryForList("""
                SELECT f.id_fonte AS id,f.codigo,f.nome,f.adaptador,f.estado_conexao AS estado,
                    f.ultima_sincronizacao::text AS "ultimaSincronizacao",
                    f.ultima_falha,f.ultima_falha_em::text AS "ultimaFalhaEm",
                    count(ev.id_evento) AS eventos,
                    count(ev.id_evento) FILTER(WHERE ev.resultado IN ('REVISAO','ERRO')) AS pendencias
                FROM integracao_fonte_dados f LEFT JOIN integracao_evento ev ON ev.id_fonte=f.id_fonte
                GROUP BY f.id_fonte ORDER BY f.nome
                """);
    }

    public List<Map<String,Object>> eventos(long source, String result) {
        if (result != null && !result.isBlank() && !RESULTADOS.contains(result)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resultado inválido");
        }
        String filter = result == null || result.isBlank() ? "" : result;
        return db.queryForList("""
                SELECT id_evento AS id,identificador_externo AS "eventoId",tipo,versao,
                    ocorrido_em::text AS "ocorridoEm",atualizado_em_fonte::text AS "atualizadoEmFonte",
                    resultado,codigo,mensagem,tentativas,recebido_em::text AS "recebidoEm",
                    processado_em::text AS "processadoEm"
                FROM integracao_evento WHERE id_fonte=? AND (?='' OR resultado=?)
                ORDER BY id_evento DESC LIMIT 200
                """, source, filter, filter);
    }

    private Map<String,Object> process(long eventId, IntegracaoEventoRequest request, Source source,
                                       int attempts, boolean replayed) {
        Outcome outcome = switch (request.tipo()) {
            case "PACIENTE_ATUALIZADO" -> patient(source.id(), request);
            case "ATENDIMENTO_ATUALIZADO" -> appointment(source, request);
            case "SITUACAO_ATENDIMENTO_ATUALIZADA" -> appointmentStatus(source.id(), request);
            default -> new Outcome("REVISAO", "tipo_nao_suportado", "Tipo de evento não suportado");
        };
        db.update("""
                UPDATE integracao_evento SET resultado=?,codigo=?,mensagem=?,processado_em=now(),atualizado_em=now()
                WHERE id_evento=?
                """, outcome.result(), outcome.code(), outcome.message(), eventId);
        if (Set.of("PROCESSADO","SEM_ALTERACAO").contains(outcome.result())) {
            db.update("""
                    UPDATE integracao_fonte_dados SET estado_conexao='ATIVA',ultima_sincronizacao=now(),
                        ultima_falha=NULL,ultima_falha_em=NULL,atualizado_em=now() WHERE id_fonte=?
                    """, source.id());
        } else {
            db.update("""
                    UPDATE integracao_fonte_dados SET estado_conexao='COM_PENDENCIAS',ultima_falha=?,
                        ultima_falha_em=now(),atualizado_em=now() WHERE id_fonte=?
                    """, outcome.message(), source.id());
        }
        return response(eventId, request.eventoId(), outcome.result(), outcome.code(), outcome.message(), attempts, replayed);
    }

    private Outcome patient(long source, IntegracaoEventoRequest request) {
        var data = request.paciente();
        if (data == null || blank(data.nome()) || blank(data.telefone())) {
            return review("paciente_incompleto", "Paciente exige idExterno, nome e telefone");
        }
        if (data.telefone().length() < 10 || data.telefone().length() > 15) {
            return review("telefone_invalido", "Telefone deve conter DDD e entre 10 e 15 dígitos");
        }
        var current = db.queryForList("""
                SELECT id_paciente_externo,nome,telefone,atualizado_em_fonte
                FROM integracao_paciente_externo WHERE id_fonte=? AND identificador_externo=? FOR UPDATE
                """, source, data.idExterno());
        if (current.isEmpty()) {
            db.update("""
                    INSERT INTO integracao_paciente_externo(
                        id_fonte,identificador_externo,nome,telefone,atualizado_em_fonte
                    ) VALUES (?,?,?,?,?)
                    """, source, data.idExterno(), data.nome(), data.telefone(), request.atualizadoEmFonte());
            return processed();
        }
        var found = current.getFirst();
        if (!newer(request.atualizadoEmFonte().toInstant(), instant(found.get("atualizado_em_fonte")))) {
            return unchanged("evento_antigo", "Evento ignorado porque existe uma versão igual ou mais recente");
        }
        if (Objects.equals(found.get("nome"), data.nome()) && Objects.equals(found.get("telefone"), data.telefone())) {
            return unchanged(null, null);
        }
        db.update("""
                UPDATE integracao_paciente_externo SET nome=?,telefone=?,atualizado_em_fonte=?,atualizado_em=now()
                WHERE id_paciente_externo=?
                """, data.nome(), data.telefone(), request.atualizadoEmFonte(), found.get("id_paciente_externo"));
        return processed();
    }

    private Outcome appointment(Source source, IntegracaoEventoRequest request) {
        var data = request.atendimento();
        if (data == null || blank(data.pacienteIdExterno()) || data.agendadoEm() == null
                || blank(data.fusoHorario()) || blank(data.profissional()) || blank(data.especialidade())
                || blank(data.tipo())) {
            return review("atendimento_incompleto",
                    "Atendimento exige paciente, horário, fuso, profissional, especialidade e tipo");
        }
        try { ZoneId.of(data.fusoHorario()); }
        catch (ZoneRulesException exception) { return review("fuso_invalido", "Fuso horário inválido"); }
        Long patient = db.queryForList("""
                SELECT id_paciente_externo FROM integracao_paciente_externo
                WHERE id_fonte=? AND identificador_externo=?
                """, source.id(), data.pacienteIdExterno()).stream()
                .map(row -> ((Number) row.get("id_paciente_externo")).longValue()).findFirst().orElse(null);
        if (patient == null) return review("paciente_nao_encontrado",
                "Cadastre o paciente externo antes do atendimento; nenhuma associação foi presumida");
        String normalized = mappedStatus(source.id(), data.situacao());
        if (normalized == null) return review("situacao_nao_mapeada",
                "A situação recebida não possui mapeamento aprovado para esta fonte");
        var current = db.queryForList("""
                SELECT id_atendimento_externo,id_paciente_externo,agendado_em,fuso_horario,profissional,
                    especialidade,tipo,situacao_origem,situacao_normalizada,atualizado_em_fonte
                FROM integracao_atendimento_externo WHERE id_fonte=? AND identificador_externo=? FOR UPDATE
                """, source.id(), data.idExterno());
        if (current.isEmpty()) {
            db.update("""
                    INSERT INTO integracao_atendimento_externo(
                        id_fonte,id_paciente_externo,identificador_externo,agendado_em,fuso_horario,
                        profissional,especialidade,tipo,situacao_origem,situacao_normalizada,
                        atualizado_em_fonte,id_importacao
                    ) VALUES (?,?,?,?,?,?,?,?,?,?,?,NULL)
                    """, source.id(), patient, data.idExterno(), data.agendadoEm(), data.fusoHorario(),
                    data.profissional(), data.especialidade(), data.tipo(), data.situacao(), normalized,
                    request.atualizadoEmFonte());
            return processed();
        }
        var found = current.getFirst();
        if (((Number) found.get("id_paciente_externo")).longValue() != patient) {
            return review("atendimento_paciente_divergente",
                    "O identificador do atendimento já pertence a outro paciente externo");
        }
        if (!newer(request.atualizadoEmFonte().toInstant(), instant(found.get("atualizado_em_fonte")))) {
            return unchanged("evento_antigo", "Evento ignorado porque existe uma versão igual ou mais recente");
        }
        db.update("""
                UPDATE integracao_atendimento_externo SET agendado_em=?,fuso_horario=?,profissional=?,
                    especialidade=?,tipo=?,situacao_origem=?,situacao_normalizada=?,
                    atualizado_em_fonte=?,id_importacao=NULL,atualizado_em=now()
                WHERE id_atendimento_externo=?
                """, data.agendadoEm(), data.fusoHorario(), data.profissional(), data.especialidade(),
                data.tipo(), data.situacao(), normalized, request.atualizadoEmFonte(),
                found.get("id_atendimento_externo"));
        return processed();
    }

    private Outcome appointmentStatus(long source, IntegracaoEventoRequest request) {
        var data = request.atendimento();
        if (data == null) return review("atendimento_ausente", "Informe o atendimento externo");
        String normalized = mappedStatus(source, data.situacao());
        if (normalized == null) return review("situacao_nao_mapeada",
                "A situação recebida não possui mapeamento aprovado para esta fonte");
        var current = db.queryForList("""
                SELECT id_atendimento_externo,atualizado_em_fonte,situacao_origem,situacao_normalizada
                FROM integracao_atendimento_externo WHERE id_fonte=? AND identificador_externo=? FOR UPDATE
                """, source, data.idExterno());
        if (current.isEmpty()) return review("atendimento_nao_encontrado",
                "Cadastre o atendimento externo antes de atualizar sua situação");
        var found = current.getFirst();
        if (!newer(request.atualizadoEmFonte().toInstant(), instant(found.get("atualizado_em_fonte")))) {
            return unchanged("evento_antigo", "Evento ignorado porque existe uma versão igual ou mais recente");
        }
        if (Objects.equals(found.get("situacao_origem"), data.situacao())
                && Objects.equals(found.get("situacao_normalizada"), normalized)) return unchanged(null, null);
        db.update("""
                UPDATE integracao_atendimento_externo SET situacao_origem=?,situacao_normalizada=?,
                    atualizado_em_fonte=?,id_importacao=NULL,atualizado_em=now()
                WHERE id_atendimento_externo=?
                """, data.situacao(), normalized, request.atualizadoEmFonte(), found.get("id_atendimento_externo"));
        return processed();
    }

    private String mappedStatus(long source, String raw) {
        String key = normalize(raw);
        return db.queryForList("""
                SELECT situacao_normalizada FROM integracao_situacao_mapeamento
                WHERE id_fonte=? AND situacao_chave=?
                """, source, key).stream().map(row -> Objects.toString(row.get("situacao_normalizada")))
                .findFirst().orElse(null);
    }

    private Source source(long id) {
        return db.query("""
                SELECT id_fonte,codigo,fuso_horario,adaptador FROM integracao_fonte_dados WHERE id_fonte=?
                """, rs -> rs.next() ? new Source(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4)) : null,
                id) instanceof Source source ? source : throwNotFound();
    }

    private Map<String,Object> sourceState(long id) {
        return db.queryForList("""
                SELECT f.codigo,f.nome,f.adaptador,f.estado_conexao AS estado,
                    f.ultima_sincronizacao::text AS "ultimaSincronizacao",
                    f.ultima_falha,f.ultima_falha_em::text AS "ultimaFalhaEm",
                    count(ev.id_evento) AS eventos,
                    count(ev.id_evento) FILTER(WHERE ev.resultado IN ('REVISAO','ERRO')) AS pendencias
                FROM integracao_fonte_dados f LEFT JOIN integracao_evento ev ON ev.id_fonte=f.id_fonte
                WHERE f.id_fonte=? GROUP BY f.id_fonte
                """, id).stream().findFirst().orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Fonte não encontrada"));
    }

    private String payload(IntegracaoEventoRequest request) {
        try { return json.writeValueAsString(request); }
        catch (JacksonException exception) { throw new IllegalStateException(exception); }
    }
    private static Map<String,Object> response(long id, String external, String result, String code,
                                                String message, int attempts, boolean replayed) {
        var map = new LinkedHashMap<String,Object>();
        map.put("id", id); map.put("eventoId", external); map.put("resultado", result);
        if (code != null) map.put("codigo", code);
        if (message != null) map.put("mensagem", message);
        map.put("tentativas", attempts); map.put("reenvio", replayed);
        return map;
    }
    private static boolean newer(Instant incoming, Instant existing) {
        return existing == null || incoming.isAfter(existing);
    }
    private static Instant instant(Object value) {
        if (value == null) return null;
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof OffsetDateTime date) return date.toInstant();
        return Instant.parse(value.toString());
    }
    private static String normalize(String value) {
        return Normalizer.normalize(Objects.toString(value, ""), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static Outcome processed() { return new Outcome("PROCESSADO", null, null); }
    private static Outcome unchanged(String code, String message) { return new Outcome("SEM_ALTERACAO", code, message); }
    private static Outcome review(String code, String message) { return new Outcome("REVISAO", code, message); }
    private static Source throwNotFound() { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Fonte não encontrada"); }
    private record Outcome(String result, String code, String message) {}
    private record Source(long id, String code, String timezone, String adapter) {}
}
