package br.com.clinica.service;

import br.com.clinica.dto.OnboardingConfigRequest;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OnboardingService {
    private static final Set<String> CHANNELS = Set.of("whatsapp", "email", "telefone");
    private final JdbcTemplate db;
    private final ObjectMapper json;

    public OnboardingService(JdbcTemplate db, ObjectMapper json) {
        this.db = db;
        this.json = json;
    }

    @Transactional
    public Map<String,Object> state() {
        ensureState();
        return read();
    }

    @Transactional
    public Map<String,Object> update(OnboardingConfigRequest request, int author) {
        validate(request);
        ensureState();
        int tenant = TenantContext.id();
        Integer member = db.queryForObject("""
                SELECT count(*) FROM tenant_membro WHERE id_clinica=? AND id_usuario=? AND ativo
                """, Integer.class, tenant, request.responsavelId());
        if (member == null || member == 0) throw bad("O responsável precisa ser um usuário ativo desta clínica");
        int changed = db.update("""
                UPDATE onboarding_clinica SET id_responsavel=?,fuso_horario=?,agenda_inicio=?::time,
                    agenda_fim=?::time,dias_atendimento=?::jsonb,canais=?::jsonb,
                    politica_consentimento_confirmada=?,primeiro_fluxo=?,status='EM_ANDAMENTO',concluido_em=NULL
                WHERE id_clinica=? AND versao=?
                """, request.responsavelId(), request.fusoHorario(), request.agendaInicio(), request.agendaFim(),
                toJson(request.diasAtendimento()), toJson(request.canais()),
                request.politicaConsentimentoConfirmada(), request.primeiroFluxo(), tenant, request.versao());
        if (changed == 0) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "A configuração foi alterada em outra sessão. Atualize a página e tente novamente.");
        event(author, "CONFIGURACAO_ATUALIZADA", Map.of("primeiroFluxo", request.primeiroFluxo()));
        return read();
    }

    @Transactional
    public Map<String,Object> complete(int author) {
        Map<String,Object> current = state();
        @SuppressWarnings("unchecked")
        Map<String,Boolean> checklist = (Map<String,Boolean>) current.get("checklist");
        List<String> pending = checklist.entrySet().stream().filter(entry -> !entry.getValue())
                .map(Map.Entry::getKey).toList();
        if (!pending.isEmpty()) throw bad("Conclua as etapas pendentes: " + String.join(", ", pending));
        db.update("UPDATE onboarding_clinica SET status='CONCLUIDO',concluido_em=now() WHERE id_clinica=?",
                TenantContext.id());
        event(author, "CONCLUIDO", Map.of());
        return read();
    }

    @Transactional
    public Map<String,Object> reopen(int author) {
        state();
        db.update("UPDATE onboarding_clinica SET status='EM_ANDAMENTO',concluido_em=NULL WHERE id_clinica=?",
                TenantContext.id());
        event(author, "REABERTO", Map.of());
        return read();
    }

    private Map<String,Object> read() {
        Map<String,Object> row = db.queryForMap("""
                SELECT o.status,o.id_responsavel AS "responsavelId",u.nome AS responsavel,
                    o.fuso_horario AS "fusoHorario",to_char(o.agenda_inicio,'HH24:MI') AS "agendaInicio",
                    to_char(o.agenda_fim,'HH24:MI') AS "agendaFim",o.dias_atendimento::text AS "diasAtendimento",
                    o.canais::text,o.politica_consentimento_confirmada AS "politicaConsentimentoConfirmada",
                    o.primeiro_fluxo AS "primeiroFluxo",o.versao,o.atualizado_em::text AS "atualizadoEm",
                    o.concluido_em::text AS "concluidoEm"
                FROM onboarding_clinica o LEFT JOIN usuario u ON u.id=o.id_responsavel
                WHERE o.id_clinica=?
                """, TenantContext.id());
        boolean imported = Boolean.TRUE.equals(db.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM integracao_importacao
                    WHERE status IN ('CONCLUIDA','CONCLUIDA_COM_ERROS') AND desfeita_em IS NULL)
                """, Boolean.class));
        Map<String,Boolean> checklist = new LinkedHashMap<>();
        checklist.put("responsável", row.get("responsavelId") != null);
        List<?> days = parseList(row.get("diasAtendimento"));
        checklist.put("agenda", row.get("agendaInicio") != null && row.get("agendaFim") != null && !days.isEmpty());
        checklist.put("importação", imported);
        checklist.put("canais", parseList(row.get("canais")).size() > 0);
        checklist.put("consentimento", Boolean.TRUE.equals(row.get("politicaConsentimentoConfirmada")));
        checklist.put("primeiro fluxo", row.get("primeiroFluxo") != null);
        row.put("canais", parseList(row.get("canais")));
        row.put("diasAtendimento", days);
        row.put("checklist", checklist);
        row.put("progresso", checklist.values().stream().filter(Boolean::booleanValue).count());
        row.put("totalEtapas", checklist.size());
        return row;
    }

    private void ensureState() {
        db.update("""
                INSERT INTO onboarding_clinica(id_clinica) VALUES (?)
                ON CONFLICT(id_clinica) DO NOTHING
                """, TenantContext.id());
    }

    private void validate(OnboardingConfigRequest request) {
        try { ZoneId.of(request.fusoHorario()); }
        catch (Exception ignored) { throw bad("Fuso horário inválido"); }
        LocalTime start = LocalTime.parse(request.agendaInicio());
        LocalTime end = LocalTime.parse(request.agendaFim());
        if (!end.isAfter(start)) throw bad("O fim da agenda deve ser posterior ao início");
        if (request.diasAtendimento().stream().anyMatch(day -> day == null || day < 1 || day > 7)
                || request.diasAtendimento().stream().distinct().count() != request.diasAtendimento().size()) {
            throw bad("Os dias de atendimento são inválidos");
        }
        if (request.canais().stream().anyMatch(channel -> !CHANNELS.contains(channel))
                || request.canais().stream().distinct().count() != request.canais().size()) {
            throw bad("Os canais selecionados são inválidos");
        }
    }

    private void event(int author, String type, Map<String,Object> details) {
        db.update("INSERT INTO onboarding_evento(id_usuario,tipo,detalhes) VALUES (?,?,?::jsonb)",
                author, type, toJson(details));
    }

    private String toJson(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JacksonException e) { throw new IllegalStateException("Não foi possível registrar a configuração", e); }
    }

    private List<?> parseList(Object value) {
        try { return json.readValue(String.valueOf(value), List.class); }
        catch (JacksonException e) { return List.of(); }
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
