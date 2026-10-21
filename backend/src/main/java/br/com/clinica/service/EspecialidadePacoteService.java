package br.com.clinica.service;

import br.com.clinica.dto.EspecialidadePacoteDtos.AlterarStatusRequest;
import br.com.clinica.dto.EspecialidadePacoteDtos.AplicarRequest;
import br.com.clinica.dto.EspecialidadePacoteDtos.CriarVersaoRequest;
import br.com.clinica.dto.EspecialidadePacoteDtos.PersonalizarRequest;
import br.com.clinica.dto.EspecialidadePacoteDtos.PublicarRequest;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
public class EspecialidadePacoteService {
    private static final String STARTER_COMPONENTS = """
        [
          {"chave":"atendimento_retorno","tipo":"MODELO_ATENDIMENTO","nome":"Retorno","duracaoMinutos":30},
          {"chave":"documento_orientacoes","tipo":"DOCUMENTO","nome":"Orientações pós-atendimento","obrigatorio":false},
          {"chave":"formulario_experiencia","tipo":"FORMULARIO","nome":"Experiência após o atendimento","campos":["nota","comentario"]},
          {"chave":"fluxo_pos_atendimento","tipo":"FLUXO_ACOMPANHAMENTO","nome":"Acompanhamento pós-atendimento","canal":"whatsapp","statusInicial":"rascunho","etapas":[{"atrasoDias":1,"titulo":"Como foi o atendimento?"}]}
        ]
        """;

    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final EspecialidadePacotePlanner planner = new EspecialidadePacotePlanner();

    public EspecialidadePacoteService(JdbcTemplate db, ObjectMapper json) {
        this.db = db;
        this.json = json;
    }

    @Transactional
    public List<Map<String,Object>> catalogo() {
        ensureStarter();
        var rows = db.queryForList("""
            SELECT p.id_pacote AS "id",p.codigo,p.nome,p.especialidade,p.descricao,p.ativo,
                   v.id_versao AS "versaoId",v.versao,v.status,v.componentes::text AS componentes,
                   v.registro_revisor AS "registroRevisor",v.revisado_em AS "revisadoEm",
                   u.nome AS revisor,a.id_aplicacao AS "aplicacaoId",a.status AS "aplicacaoStatus",
                   a.id_versao AS "versaoAplicadaId",a.componentes_selecionados::text AS "componentesSelecionados",
                   coalesce(a.versao_lock,0) AS "versaoLock",a.iniciada_em AS "iniciadaEm",a.aplicada_em AS "aplicadaEm"
            FROM especialidade_pacote p
            JOIN LATERAL (SELECT * FROM especialidade_pacote_versao pv WHERE pv.id_pacote=p.id_pacote
                          ORDER BY pv.versao DESC LIMIT 1) v ON true
            LEFT JOIN usuario u ON u.id=v.id_revisor
            LEFT JOIN especialidade_pacote_aplicacao a ON a.id_pacote=p.id_pacote
            WHERE p.ativo ORDER BY p.especialidade,p.nome
            """);
        rows.forEach(row -> {
            row.put("componentes", parseList(row.get("componentes")));
            row.put("componentesSelecionados", parseStringList(row.get("componentesSelecionados")));
            if (row.get("aplicacaoId") != null) {
                long app = ((Number) row.get("aplicacaoId")).longValue();
                Integer ajustes = db.queryForObject("SELECT count(*) FROM especialidade_componente_aplicado WHERE id_aplicacao=? AND personalizado", Integer.class, app);
                row.put("ajustesLocais", ajustes == null ? 0 : ajustes);
            } else row.put("ajustesLocais", 0);
        });
        return rows;
    }

    public List<Map<String,Object>> revisores() {
        return db.queryForList("""
            SELECT DISTINCT u.id,u.nome FROM usuario u JOIN tenant_membro m ON m.id_usuario=u.id
            JOIN tenant_membro_papel p USING(id_clinica,id_usuario)
            WHERE m.id_clinica=? AND m.ativo AND p.papel='medico' ORDER BY u.nome
            """, TenantContext.id());
    }

    @Transactional
    public Map<String,Object> criarVersao(long packageId, CriarVersaoRequest request, int actor) {
        ensureStarter();
        if (!Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM especialidade_pacote WHERE id_pacote=? AND ativo)", Boolean.class, packageId)))
            throw notFound();
        // O planner também valida chaves, tipos e nomes obrigatórios antes da persistência.
        Set<String> keys = request.componentes().stream().map(item -> Objects.toString(item.get("chave"),"")).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        planner.planejar(request.componentes(),Map.of(),keys,1);
        Integer next = db.queryForObject("SELECT coalesce(max(versao),0)+1 FROM especialidade_pacote_versao WHERE id_pacote=?", Integer.class, packageId);
        Long id = db.queryForObject("INSERT INTO especialidade_pacote_versao(id_pacote,versao,status,componentes) VALUES (?,?,'RASCUNHO',?::jsonb) RETURNING id_versao",
                Long.class,packageId,next,toJson(request.componentes()));
        event(packageId,null,actor,"VERSAO_CRIADA",Map.of("novaVersaoId",id,"versao",next));
        return version(Objects.requireNonNull(id));
    }

    @Transactional
    public Map<String,Object> atualizarVersao(long versionId, CriarVersaoRequest request, int actor) {
        var current = version(versionId);
        if (!"RASCUNHO".equals(current.get("status"))) throw bad("Somente versões em rascunho podem ser editadas");
        Set<String> keys = request.componentes().stream().map(item -> Objects.toString(item.get("chave"),""))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        planner.planejar(request.componentes(),Map.of(),keys,((Number)current.get("versao")).intValue());
        db.update("UPDATE especialidade_pacote_versao SET componentes=?::jsonb WHERE id_versao=?",toJson(request.componentes()),versionId);
        event(number(current,"pacoteId"),null,actor,"VERSAO_ATUALIZADA",Map.of("versaoId",versionId));
        return version(versionId);
    }

    @Transactional
    public Map<String,Object> enviarRevisao(long versionId, int actor) {
        var current = version(versionId);
        int changed = db.update("UPDATE especialidade_pacote_versao SET status='EM_REVISAO' WHERE id_versao=? AND status='RASCUNHO'",versionId);
        if (changed == 0) throw bad("Somente versões em rascunho podem ser enviadas para revisão");
        event(number(current,"pacoteId"),null,actor,"VERSAO_ENVIADA_REVISAO",Map.of("versaoId",versionId));
        return version(versionId);
    }

    @Transactional
    public Map<String,Object> publicar(long versaoId, PublicarRequest request, int actor) {
        ensureStarter();
        boolean medico = Boolean.TRUE.equals(db.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM tenant_membro m JOIN tenant_membro_papel p USING(id_clinica,id_usuario)
              WHERE m.id_clinica=? AND m.id_usuario=? AND m.ativo AND p.papel='medico')
            """, Boolean.class, TenantContext.id(), request.revisorId()));
        if (!medico) throw bad("A revisão precisa ser atribuída a um médico ativo da clínica");
        var version = version(versaoId);
        if (!"EM_REVISAO".equals(version.get("status"))) throw bad("Envie o rascunho para revisão antes de publicar");
        long packageId = number(version, "pacoteId");
        db.update("UPDATE especialidade_pacote_versao SET status='DESATIVADA' WHERE id_pacote=? AND status='PUBLICADA'", packageId);
        db.update("""
            UPDATE especialidade_pacote_versao SET status='PUBLICADA',id_revisor=?,registro_revisor=?,
              revisado_em=now(),publicado_em=now() WHERE id_versao=?
            """, request.revisorId(), request.registroProfissional().strip(), versaoId);
        event(packageId, null, actor, "VERSAO_PUBLICADA", Map.of("versaoId", versaoId,
                "revisorId", request.revisorId(), "registro", request.registroProfissional().strip()));
        return version(versaoId);
    }

    @Transactional
    public Map<String,Object> preview(long versaoId, List<String> keys) {
        ensureStarter();
        var version = version(versaoId);
        var selected = validatedKeys(version, keys);
        long packageId = number(version, "pacoteId");
        var installed = installed(packageId);
        var plan = planner.planejar(components(version), installed, selected, ((Number)version.get("versao")).intValue());
        var result = new LinkedHashMap<String,Object>();
        result.put("pacoteId", packageId);
        result.put("versaoId", versaoId);
        result.put("versao", version.get("versao"));
        result.put("status", version.get("status"));
        result.put("itens", plan);
        result.put("avisos", validateDurations(plan));
        result.put("exigePoliticaComunicacao", plan.stream().anyMatch(item -> item.tipo().equals("FLUXO_ACOMPANHAMENTO")));
        result.put("personalizacoesPreservadas", plan.stream().filter(item -> item.acao().equals("PRESERVAR_PERSONALIZACAO")).count());
        return result;
    }

    @Transactional
    public Map<String,Object> aplicar(long versaoId, AplicarRequest request, int actor) {
        var version = version(versaoId);
        if (!"PUBLICADA".equals(version.get("status"))) throw bad("Somente uma versão revisada e publicada pode ser aplicada");
        var selected = validatedKeys(version, request.componentes());
        var plan = planner.planejar(components(version), installed(number(version,"pacoteId")), selected,
                ((Number)version.get("versao")).intValue());
        List<String> warnings = validateDurations(plan);
        if (!warnings.isEmpty()) throw bad(String.join(" ", warnings));
        boolean hasFlow = plan.stream().anyMatch(item -> item.tipo().equals("FLUXO_ACOMPANHAMENTO"));
        if (hasFlow && (!request.politicaComunicacaoAprovada() || !communicationPolicyConfirmed()))
            throw bad("Confirme a política de comunicação da clínica antes de aplicar fluxos de acompanhamento");

        long packageId = number(version, "pacoteId");
        List<Map<String,Object>> current = db.queryForList("SELECT id_aplicacao,versao_lock FROM especialidade_pacote_aplicacao WHERE id_pacote=?", packageId);
        long applicationId;
        boolean created = current.isEmpty();
        if (created) {
            if (request.versaoLock() != 0) throw conflict();
            applicationId = Objects.requireNonNull(db.queryForObject("""
                INSERT INTO especialidade_pacote_aplicacao(id_pacote,id_versao,componentes_selecionados,
                  politica_comunicacao_aprovada,id_aplicado_por) VALUES (?,?,?::jsonb,?,?) RETURNING id_aplicacao
                """, Long.class, packageId, versaoId, toJson(selected), request.politicaComunicacaoAprovada(), actor));
        } else {
            applicationId = number(current.getFirst(), "id_aplicacao");
            int changed = db.update("""
                UPDATE especialidade_pacote_aplicacao SET id_versao=?,status='ATIVA',componentes_selecionados=?::jsonb,
                  politica_comunicacao_aprovada=?,id_aplicado_por=?,aplicada_em=now(),desativada_em=NULL,
                  versao_lock=versao_lock+1 WHERE id_aplicacao=? AND versao_lock=?
                """, versaoId, toJson(selected), request.politicaComunicacaoAprovada(), actor, applicationId, request.versaoLock());
            if (changed == 0) throw conflict();
        }
        persistPlan(applicationId, plan, ((Number)version.get("versao")).intValue());
        db.queryForList("SELECT chave FROM especialidade_componente_aplicado WHERE id_aplicacao=?", String.class, applicationId)
                .stream().filter(key -> !selected.contains(key)).forEach(key -> db.update(
                        "UPDATE especialidade_componente_aplicado SET ativo=false,atualizado_em=now() WHERE id_aplicacao=? AND chave=?",
                        applicationId,key));
        event(packageId, applicationId, actor, created ? "APLICACAO_CRIADA" : "APLICACAO_ATUALIZADA",
                Map.of("versaoId", versaoId, "componentes", selected, "acoes", plan.stream().map(EspecialidadePacotePlanner.Item::acao).toList()));
        return application(applicationId);
    }

    @Transactional
    public Map<String,Object> personalizar(long applicationId, String key, PersonalizarRequest request, int actor) {
        int changed = db.update("""
            UPDATE especialidade_componente_aplicado c SET configuracao_local=?::jsonb,personalizado=true,atualizado_em=now()
            FROM especialidade_pacote_aplicacao a WHERE c.id_aplicacao=a.id_aplicacao AND c.id_aplicacao=?
              AND c.chave=? AND a.versao_lock=?
            """, toJson(request.configuracao()), applicationId, key, request.versaoLock());
        if (changed == 0) throw conflict();
        db.update("UPDATE especialidade_pacote_aplicacao SET versao_lock=versao_lock+1 WHERE id_aplicacao=?", applicationId);
        var app = application(applicationId);
        event(number(app,"pacoteId"), applicationId, actor, "APLICACAO_ATUALIZADA", Map.of("componentePersonalizado", key));
        return application(applicationId);
    }

    @Transactional
    public Map<String,Object> desativar(long applicationId, AlterarStatusRequest request, int actor) {
        return changeStatus(applicationId, request.versaoLock(), actor, "DESATIVADA", "APLICACAO_DESATIVADA");
    }

    @Transactional
    public Map<String,Object> reverter(long applicationId, AlterarStatusRequest request, int actor) {
        return changeStatus(applicationId, request.versaoLock(), actor, "REVERTIDA", "APLICACAO_REVERTIDA");
    }

    private Map<String,Object> changeStatus(long id, int lock, int actor, String status, String event) {
        int changed = db.update("UPDATE especialidade_pacote_aplicacao SET status=?,desativada_em=now(),versao_lock=versao_lock+1 WHERE id_aplicacao=? AND versao_lock=?", status,id,lock);
        if (changed == 0) throw conflict();
        db.update("UPDATE especialidade_componente_aplicado SET ativo=false WHERE id_aplicacao=?", id);
        var app = application(id);
        event(number(app,"pacoteId"),id,actor,event,Map.of("versaoAplicada",app.get("versao")));
        return app;
    }

    private void persistPlan(long applicationId, List<EspecialidadePacotePlanner.Item> plan, int version) {
        for (var item : plan) {
            String base = toJson(item.configuracao());
            String local = toJson(item.configuracaoLocal());
            if (item.acao().equals("CRIAR")) {
                db.update("""
                    INSERT INTO especialidade_componente_aplicado(id_aplicacao,chave,tipo,configuracao_base,
                      configuracao_local,versao_origem) VALUES (?,?,?,?::jsonb,?::jsonb,?)
                    ON CONFLICT(id_clinica,id_aplicacao,chave) DO NOTHING
                    """, applicationId,item.chave(),item.tipo(),base,local,version);
            } else if (item.acao().equals("PRESERVAR_PERSONALIZACAO")) {
                db.update("UPDATE especialidade_componente_aplicado SET configuracao_base=?::jsonb,versao_origem=?,ativo=true,atualizado_em=now() WHERE id_aplicacao=? AND chave=?",
                        base,version,applicationId,item.chave());
            } else {
                db.update("UPDATE especialidade_componente_aplicado SET configuracao_base=?::jsonb,configuracao_local=?::jsonb,versao_origem=?,personalizado=false,ativo=true,atualizado_em=now() WHERE id_aplicacao=? AND chave=?",
                        base,local,version,applicationId,item.chave());
            }
        }
    }

    private List<String> validateDurations(List<EspecialidadePacotePlanner.Item> plan) {
        var schedule = db.queryForList("SELECT to_char(agenda_inicio,'HH24:MI') AS agenda_inicio,to_char(agenda_fim,'HH24:MI') AS agenda_fim FROM onboarding_clinica WHERE id_clinica=?", TenantContext.id());
        var warnings = new ArrayList<String>();
        for (var item : plan) {
            if (!item.tipo().equals("MODELO_ATENDIMENTO")) continue;
            Object raw = item.configuracao().get("duracaoMinutos");
            int minutes = raw instanceof Number n ? n.intValue() : -1;
            if (minutes < 5 || minutes > 480) warnings.add(item.nome() + ": duração deve ficar entre 5 e 480 minutos.");
            if (schedule.isEmpty() || schedule.getFirst().get("agenda_inicio") == null || schedule.getFirst().get("agenda_fim") == null) {
                warnings.add("Configure o horário da agenda antes de aplicar modelos de atendimento.");
            } else {
                LocalTime start = LocalTime.parse(Objects.toString(schedule.getFirst().get("agenda_inicio")));
                LocalTime end = LocalTime.parse(Objects.toString(schedule.getFirst().get("agenda_fim")));
                if (minutes > Duration.between(start,end).toMinutes()) warnings.add(item.nome() + ": duração não cabe na janela da agenda.");
            }
        }
        return warnings.stream().distinct().toList();
    }

    private boolean communicationPolicyConfirmed() {
        return Boolean.TRUE.equals(db.queryForObject("SELECT coalesce((SELECT politica_consentimento_confirmada FROM onboarding_clinica WHERE id_clinica=?),false)", Boolean.class, TenantContext.id()));
    }

    private Map<String,EspecialidadePacotePlanner.Instalado> installed(long packageId) {
        List<Map<String,Object>> apps = db.queryForList("SELECT id_aplicacao FROM especialidade_pacote_aplicacao WHERE id_pacote=?", packageId);
        if (apps.isEmpty()) return Map.of();
        long id = number(apps.getFirst(), "id_aplicacao");
        var result = new LinkedHashMap<String,EspecialidadePacotePlanner.Instalado>();
        db.queryForList("SELECT chave,configuracao_base::text AS base,configuracao_local::text AS local,versao_origem,personalizado,ativo FROM especialidade_componente_aplicado WHERE id_aplicacao=?", id)
                .forEach(row -> result.put(Objects.toString(row.get("chave")), new EspecialidadePacotePlanner.Instalado(
                        Objects.toString(row.get("chave")), parseMap(row.get("base")), parseMap(row.get("local")),
                        ((Number)row.get("versao_origem")).intValue(), Boolean.TRUE.equals(row.get("personalizado")), Boolean.TRUE.equals(row.get("ativo")))));
        return result;
    }

    private Map<String,Object> application(long id) {
        var app = db.queryForList("""
            SELECT a.id_aplicacao AS "id",a.id_pacote AS "pacoteId",a.id_versao AS "versaoId",v.versao,
              a.status,a.componentes_selecionados::text AS "componentesSelecionados",a.versao_lock AS "versaoLock",
              a.iniciada_em AS "iniciadaEm",a.aplicada_em AS "aplicadaEm",a.desativada_em AS "desativadaEm"
            FROM especialidade_pacote_aplicacao a JOIN especialidade_pacote_versao v ON v.id_versao=a.id_versao
            WHERE a.id_aplicacao=?
            """, id).stream().findFirst().orElseThrow(EspecialidadePacoteService::notFound);
        app.put("componentesSelecionados", parseStringList(app.get("componentesSelecionados")));
        app.put("componentes", db.queryForList("SELECT chave,tipo,configuracao_base::text AS \"configuracaoBase\",configuracao_local::text AS \"configuracaoLocal\",versao_origem AS \"versaoOrigem\",personalizado,ativo FROM especialidade_componente_aplicado WHERE id_aplicacao=? ORDER BY id_componente", id));
        return app;
    }

    private Map<String,Object> version(long id) {
        var row = db.queryForList("""
            SELECT v.id_versao AS "id",v.id_pacote AS "pacoteId",v.versao,v.status,
              v.componentes::text AS componentes,v.registro_revisor AS "registroRevisor",
              v.revisado_em AS "revisadoEm",u.nome AS revisor
            FROM especialidade_pacote_versao v LEFT JOIN usuario u ON u.id=v.id_revisor WHERE v.id_versao=?
            """, id).stream().findFirst().orElseThrow(EspecialidadePacoteService::notFound);
        row.put("componentes", parseList(row.get("componentes")));
        return row;
    }

    private Set<String> validatedKeys(Map<String,Object> version, List<String> keys) {
        Set<String> available = components(version).stream().map(item -> Objects.toString(item.get("chave"))).collect(java.util.stream.Collectors.toSet());
        var selected = new LinkedHashSet<>(keys == null ? List.<String>of() : keys);
        if (selected.isEmpty()) throw bad("Selecione ao menos um componente");
        if (!available.containsAll(selected)) throw bad("A seleção contém componentes que não pertencem a esta versão");
        return selected;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String,Object>> components(Map<String,Object> version) { return (List<Map<String,Object>>)version.get("componentes"); }

    private void ensureStarter() {
        db.update("""
            INSERT INTO especialidade_pacote(codigo,nome,especialidade,descricao)
            VALUES ('pos-atendimento-geral','Pós-atendimento essencial','Clínica geral',
              'Modelo operacional inicial de retorno, documentos, formulário e acompanhamento. Exige revisão profissional antes da publicação.')
            ON CONFLICT(id_clinica,codigo) DO NOTHING
            """);
        db.update("""
            INSERT INTO especialidade_pacote_versao(id_pacote,versao,status,componentes)
            SELECT id_pacote,1,'EM_REVISAO',?::jsonb FROM especialidade_pacote WHERE codigo='pos-atendimento-geral'
            ON CONFLICT(id_clinica,id_pacote,versao) DO NOTHING
            """, STARTER_COMPONENTS);
    }

    private void event(long packageId, Long applicationId, int actor, String type, Map<String,?> details) {
        db.update("INSERT INTO especialidade_pacote_evento(id_pacote,id_aplicacao,id_usuario,tipo,detalhes) VALUES (?,?,?,?,?::jsonb)",
                packageId,applicationId,actor,type,toJson(details));
    }

    @SuppressWarnings("unchecked") private List<Map<String,Object>> parseList(Object value) {
        try { return value == null ? List.of() : json.readValue(String.valueOf(value), List.class); }
        catch (JacksonException e) { throw new IllegalStateException("Pacote com componentes inválidos", e); }
    }
    private List<String> parseStringList(Object value) {
        try {
            if (value == null) return List.of();
            List<?> parsed = json.readValue(String.valueOf(value), List.class);
            return parsed.stream().map(String::valueOf).toList();
        }
        catch (JacksonException e) { return List.of(); }
    }
    @SuppressWarnings("unchecked") private Map<String,Object> parseMap(Object value) {
        try { return value == null ? Map.of() : json.readValue(String.valueOf(value), Map.class); }
        catch (JacksonException e) { throw new IllegalStateException("Configuração de componente inválida", e); }
    }
    private String toJson(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JacksonException e) { throw new IllegalArgumentException("Não foi possível registrar a configuração", e); }
    }
    private static long number(Map<String,Object> map, String key) { return ((Number)map.get(key)).longValue(); }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
    private static ResponseStatusException conflict() { return new ResponseStatusException(HttpStatus.CONFLICT,"A configuração foi alterada em outra sessão. Atualize a página."); }
    private static ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND,"Pacote ou aplicação não encontrado"); }
}
