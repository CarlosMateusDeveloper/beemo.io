package br.com.clinica.service;

import br.com.clinica.dto.CrmDtos.*;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class CrmOportunidadeService {
    private static final Set<String> ETAPAS = Set.of("novo","em_contato","qualificado","aguardando_agendamento","agendado","perdido");
    private static final Set<String> CANAIS_ORIGEM = Set.of("whatsapp","telefone","site","indicacao","instagram","presencial","outro");
    private static final Set<String> CANAIS_CONTATO = Set.of("whatsapp","telefone","email","presencial");
    private static final Map<String,Set<String>> TRANSICOES = Map.of(
            "novo", Set.of("em_contato","qualificado","perdido"),
            "em_contato", Set.of("qualificado","aguardando_agendamento","perdido"),
            "qualificado", Set.of("em_contato","aguardando_agendamento","perdido"),
            "aguardando_agendamento", Set.of("em_contato","qualificado","perdido"),
            "agendado", Set.of(), "perdido", Set.of());

    private final JdbcTemplate db;
    private final ObjectMapper json;
    public CrmOportunidadeService(JdbcTemplate db, ObjectMapper json) { this.db=db; this.json=json; }

    public Map<String,Object> listar(String etapa,String busca,String origem,Integer responsavel,
                                     LocalDate inicio,LocalDate fim,int pagina,int tamanho) {
        page(pagina,tamanho);
        String stage=blank(etapa)?"abertas":etapa;
        if(!stage.equals("todas")&&!stage.equals("abertas")&&!ETAPAS.contains(stage)) throw bad("Etapa inválida.");
        String term=clean(busca);
        String source=clean(origem);
        LocalDate from=inicio==null?LocalDate.now().minusDays(30):inicio;
        LocalDate to=fim==null?LocalDate.now():fim;
        if(to.isBefore(from)||ChronoUnit.DAYS.between(from,to)>366) throw bad("Período inválido. Use no máximo 366 dias.");
        String where="""
            WHERE (?='todas' OR (?='abertas' AND o.etapa NOT IN ('agendado','perdido')) OR o.etapa=?)
              AND (?='' OR strpos(lower(i.nome),lower(?))>0 OR strpos(coalesce(i.telefone,''),?)>0
                   OR strpos(lower(o.servico_interesse),lower(?))>0)
              AND (?='' OR o.canal_origem=?) AND (? IS NULL OR o.id_responsavel=?)
              AND o.primeiro_contato_em>=? AND o.primeiro_contato_em<?
            """;
        Object[] base={stage,stage,stage,term,term,digits(term),term,source,source,responsavel,responsavel,
                Date.valueOf(from),Date.valueOf(to.plusDays(1))};
        long total=db.queryForObject("SELECT count(*) FROM crm_oportunidade o JOIN crm_interessado i ON i.id_interessado=o.id_interessado "+where,Long.class,base);
        var args=new ArrayList<>(Arrays.asList(base)); args.add(tamanho); args.add(pagina*tamanho);
        var items=db.queryForList("""
            SELECT o.id_oportunidade AS id,i.nome,i.telefone,i.email,i.canal_preferido AS "canalPreferido",
              i.contato_permitido AS "contatoPermitido",o.canal_origem AS "canalOrigem",o.origem,
              o.servico_interesse AS "servicoInteresse",o.id_responsavel AS "responsavelId",u.nome AS responsavel,
              o.etapa,o.proxima_acao AS "proximaAcao",o.orcamento,o.motivo_perda AS "motivoPerda",
              o.primeiro_contato_em AS "primeiroContatoEm",o.primeira_resposta_humana_em AS "primeiraRespostaHumanaEm",
              o.id_paciente AS "pacienteId",o.id_consulta AS "consultaId",o.versao,
              (o.etapa NOT IN ('agendado','perdido') AND (o.proxima_acao IS NULL OR o.proxima_acao<now())) AS parada
            FROM crm_oportunidade o JOIN crm_interessado i ON i.id_interessado=o.id_interessado
            LEFT JOIN usuario u ON u.id=o.id_responsavel
            """+where+" ORDER BY CASE WHEN o.etapa NOT IN ('agendado','perdido') AND (o.proxima_acao IS NULL OR o.proxima_acao<now()) THEN 0 ELSE 1 END,o.proxima_acao NULLS FIRST,o.id_oportunidade DESC LIMIT ? OFFSET ?",args.toArray());
        Map<String,Object> summary=db.queryForMap("""
            SELECT count(*) FILTER(WHERE etapa NOT IN ('agendado','perdido')) AS abertas,
              count(*) FILTER(WHERE etapa NOT IN ('agendado','perdido') AND (proxima_acao IS NULL OR proxima_acao<now())) AS paradas,
              count(*) FILTER(WHERE primeira_resposta_humana_em IS NULL AND etapa NOT IN ('agendado','perdido')) AS "semRespostaHumana",
              count(*) FILTER(WHERE etapa='agendado') AS agendadas,
              round(avg(extract(epoch FROM (primeira_resposta_humana_em-primeiro_contato_em))/60)
                FILTER(WHERE primeira_resposta_humana_em IS NOT NULL),1) AS "tempoRespostaMin"
            FROM crm_oportunidade WHERE primeiro_contato_em>=? AND primeiro_contato_em<?
            """,Date.valueOf(from),Date.valueOf(to.plusDays(1)));
        return Map.of("itens",items,"total",total,"pagina",pagina,"tamanho",tamanho,"resumo",summary,
                "periodo",Map.of("inicio",from,"fim",to));
    }

    @Transactional
    public Map<String,Object> criar(int actor,CriarOportunidade input) {
        validateCreate(input); memberNullable(input.responsavelId());
        String key=cleanToNull(input.idempotencyKey());
        if(key!=null) {
            db.queryForObject("SELECT 1 FROM pg_advisory_xact_lock(hashtextextended(?,?))",Integer.class,key,TenantContext.id());
            var existing=db.queryForList("SELECT id_oportunidade FROM crm_oportunidade WHERE idempotency_key=?",key);
            if(!existing.isEmpty()) return detalhe(((Number)existing.getFirst().get("id_oportunidade")).longValue());
        }
        String phone=blank(input.telefone())?null:phone(input.telefone()); String email=lower(input.email());
        long interested=db.queryForObject("""
            INSERT INTO crm_interessado(nome,telefone,email,canal_preferido,contato_permitido)
            VALUES (?,?,?,?,?) RETURNING id_interessado
            """,Long.class,input.nome().strip(),phone,email,input.canalPreferido(),input.contatoPermitido());
        long id=db.queryForObject("""
            INSERT INTO crm_oportunidade(id_interessado,canal_origem,origem,servico_interesse,id_responsavel,
              proxima_acao,orcamento,idempotency_key,conversa_externa_id,criado_por,atualizado_por)
            VALUES (?,?,?,?,?,?,?,?,?,?,?) RETURNING id_oportunidade
            """,Long.class,interested,input.canalOrigem(),input.origem().strip(),input.servicoInteresse().strip(),
                input.responsavelId(),timestamp(input.proximaAcao()),input.orcamento(),key,cleanToNull(input.conversaExternaId()),actor,actor);
        event(id,"criada",null,"novo",actor,false,null,Map.of("origem",input.origem().strip()));
        return detalhe(id);
    }

    @Transactional
    public Map<String,Object> atualizar(long id,int actor,AtualizarOportunidade input) {
        memberNullable(input.responsavelId());
        int changed=db.update("""
            UPDATE crm_oportunidade SET servico_interesse=?,id_responsavel=?,proxima_acao=?,orcamento=?,atualizado_por=?
            WHERE id_oportunidade=? AND versao=? AND etapa NOT IN ('agendado','perdido')
            """,input.servicoInteresse().strip(),input.responsavelId(),timestamp(input.proximaAcao()),input.orcamento(),actor,id,input.versao());
        changed(changed);
        event(id,"atualizada",null,null,actor,false,null,Map.of("versaoAnterior",input.versao()));
        return detalhe(id);
    }

    @Transactional
    public Map<String,Object> transicionar(long id,int actor,Transicao input) {
        if(!ETAPAS.contains(input.etapa())||input.etapa().equals("agendado")) throw bad("Etapa inválida; use a conversão para registrar um agendamento.");
        String loss=cleanToNull(input.motivoPerda());
        if(input.etapa().equals("perdido")&&loss==null) throw bad("Informe o motivo da perda.");
        var current=lock(id);
        String before=Objects.toString(current.get("etapa"));
        if(((Number)current.get("versao")).intValue()!=input.versao()) conflict();
        if(!TRANSICOES.getOrDefault(before,Set.of()).contains(input.etapa())) throw bad("Transição de etapa inválida.");
        memberNullable(input.responsavelId());
        int changed=db.update("""
            UPDATE crm_oportunidade SET etapa=?,motivo_perda=?,proxima_acao=?,
              id_responsavel=coalesce(?,id_responsavel),atualizado_por=? WHERE id_oportunidade=? AND versao=?
            """,input.etapa(),loss,input.etapa().equals("perdido")?null:timestamp(input.proximaAcao()),
                input.responsavelId(),actor,id,input.versao());
        changed(changed);
        event(id,"etapa",before,input.etapa(),actor,false,null,loss==null?Map.of():Map.of("motivoPerda",loss));
        return detalhe(id);
    }

    @Transactional
    public Map<String,Object> interacao(long id,int actor,Interacao input) {
        if(!Set.of("mensagem_entrada","resposta_automatica","resposta_humana").contains(input.tipo())) throw bad("Tipo de interação inválido.");
        if(!CANAIS_CONTATO.contains(input.canal())) throw bad("Canal inválido.");
        var current=lock(id); String stage=Objects.toString(current.get("etapa"));
        if(Set.of("agendado","perdido").contains(stage)) throw bad("A oportunidade já está encerrada.");
        OffsetDateTime when=input.ocorridoEm()==null?OffsetDateTime.now():input.ocorridoEm();
        boolean human=input.tipo().equals("resposta_humana"); boolean automatic=input.tipo().equals("resposta_automatica");
        db.update("""
            UPDATE crm_oportunidade SET
              primeira_resposta_humana_em=CASE WHEN ? THEN coalesce(primeira_resposta_humana_em,?) ELSE primeira_resposta_humana_em END,
              primeira_resposta_automatica_em=CASE WHEN ? THEN coalesce(primeira_resposta_automatica_em,?) ELSE primeira_resposta_automatica_em END,
              etapa=CASE WHEN ? AND etapa='novo' THEN 'em_contato' ELSE etapa END,atualizado_por=? WHERE id_oportunidade=?
            """,human,Timestamp.from(when.toInstant()),automatic,Timestamp.from(when.toInstant()),human,actor,id);
        if(input.mensagemId()!=null) db.update("UPDATE mensagem SET id_crm_oportunidade=? WHERE id_mensagem=?",id,input.mensagemId());
        var data=new LinkedHashMap<String,Object>(); data.put("canal",input.canal());
        if(!blank(input.resumo())) data.put("resumo",input.resumo().strip());
        event(id,input.tipo(),stage,human&&stage.equals("novo")?"em_contato":stage,actor,automatic,input.mensagemId(),data,when);
        return detalhe(id);
    }

    @Transactional
    public Map<String,Object> converter(long id,int actor,Converter input) {
        var opportunity=lock(id);
        if(((Number)opportunity.get("versao")).intValue()!=input.versao()) conflict();
        String stage=Objects.toString(opportunity.get("etapa"));
        if(Set.of("agendado","perdido").contains(stage)) throw bad("A oportunidade já está encerrada.");
        Integer patient=input.pacienteId();
        if(patient!=null&&input.novoPaciente()!=null) throw bad("Escolha um paciente existente ou informe um novo cadastro.");
        if(patient==null&&input.novoPaciente()==null) throw bad("Informe o paciente do agendamento.");
        if(patient!=null) patient(patient);
        else {
            NovoPaciente fresh=input.novoPaciente();
            String normalized=phone(fresh.telefone());
            boolean cpfExists=Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM paciente WHERE cpf=?)",Boolean.class,fresh.cpf()));
            var candidates=db.queryForList("""
                SELECT id_paciente AS id,nome,
                  CASE WHEN cpf IS NULL THEN NULL ELSE concat('***.***.***-',right(cpf,2)) END AS cpf,
                  concat(ddd,numero) AS telefone
                FROM paciente WHERE cpf=? OR lower(coalesce(email,''))=lower(?)
                  OR regexp_replace(concat(ddd,numero),'\\D','','g') IN (?,?) ORDER BY id_paciente LIMIT 10
                """,fresh.cpf(),cleanToNull(fresh.email()),normalized,withoutCountry(normalized));
            if(!candidates.isEmpty()&&(!input.confirmarNovoPaciente()||cpfExists)) {
                String reason=cpfExists?"cpf_existente":"telefone_compartilhado";
                db.update("""
                    INSERT INTO crm_identidade_revisao(id_oportunidade,motivo,candidatos)
                    VALUES (?,?,CAST(? AS jsonb)) ON CONFLICT(id_clinica,id_oportunidade,motivo) WHERE status='pendente'
                    DO UPDATE SET candidatos=excluded.candidatos
                    """,id,reason,json(candidates));
                return Map.of("status","revisao_necessaria","motivo",reason,"candidatos",candidates,
                        "podeCriarNovo",!cpfExists,
                        "mensagem","Há cadastros semelhantes. Selecione o paciente correto ou confirme um novo cadastro.");
            }
            String local=withoutCountry(normalized);
            String ddd=local.substring(0,2),number=local.substring(2);
            patient=db.queryForObject("""
                INSERT INTO paciente(nome,cpf,data_nascimento,ddd,numero,email) VALUES (?,?,?,?,?,?) RETURNING id_paciente
                """,Integer.class,fresh.nome().strip(),fresh.cpf(),Date.valueOf(fresh.dataNascimento()),ddd,number,lower(fresh.email()));
        }
        Integer agenda=input.agendaId();
        if(agenda==null) {
            if(input.medicoId()==null||input.data()==null||blank(input.hora())) throw bad("Informe profissional, data e horário do agendamento.");
            LocalTime time;
            try { time=LocalTime.parse(input.hora()); } catch(Exception e) { throw bad("Horário inválido."); }
            try {
                agenda=db.queryForObject("""
                    INSERT INTO agenda(id_medico,situacao,data_slot,hora_slot)
                    VALUES (?,'Ocupado',?,?) RETURNING id_agenda
                    """,Integer.class,input.medicoId(),Date.valueOf(input.data()),java.sql.Time.valueOf(time));
            } catch(org.springframework.dao.DuplicateKeyException e) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,"O profissional já possui um compromisso nesse horário.");
            }
        } else {
            int occupied=db.update("UPDATE agenda SET situacao='Ocupado' WHERE id_agenda=? AND situacao='Livre'",agenda);
            if(occupied==0) throw new ResponseStatusException(HttpStatus.CONFLICT,"O horário não está livre. Atualize a agenda e escolha outro.");
        }
        Integer consultation=db.queryForObject("""
            INSERT INTO consulta(id_paciente,id_agenda,status_consulta,tipo,duracao_minutos)
            VALUES (?,?,CAST('Agendada' AS status_consulta_enum),CAST(? AS tipo_consulta_enum),?)
            RETURNING id_consulta
            """,Integer.class,patient,agenda,blank(input.tipo())?"Consulta":input.tipo(),
                input.duracaoMinutos()==null?30:input.duracaoMinutos());
        int changed=db.update("""
            UPDATE crm_oportunidade SET etapa='agendado',id_paciente=?,id_consulta=?,proxima_acao=NULL,
              motivo_perda=NULL,atualizado_por=? WHERE id_oportunidade=? AND versao=?
            """,patient,consultation,actor,id,input.versao());
        changed(changed);
        db.update("UPDATE crm_identidade_revisao SET status='resolvida',resolvido_por=?,resolvido_em=now() WHERE id_oportunidade=? AND status='pendente'",actor,id);
        event(id,"conversao",stage,"agendado",actor,false,null,Map.of("pacienteId",patient,"consultaId",consultation,"agendaId",agenda));
        var result=new LinkedHashMap<>(detalhe(id)); result.put("status","agendada"); return result;
    }

    public Map<String,Object> detalhe(long id) {
        var opportunity=db.queryForList("""
            SELECT o.id_oportunidade AS id,i.id_interessado AS "interessadoId",i.nome,i.telefone,i.email,
              i.canal_preferido AS "canalPreferido",i.contato_permitido AS "contatoPermitido",
              o.canal_origem AS "canalOrigem",o.origem,o.servico_interesse AS "servicoInteresse",
              o.id_responsavel AS "responsavelId",u.nome AS responsavel,o.etapa,o.proxima_acao AS "proximaAcao",
              o.orcamento,o.motivo_perda AS "motivoPerda",o.primeiro_contato_em AS "primeiroContatoEm",
              o.primeira_resposta_automatica_em AS "primeiraRespostaAutomaticaEm",
              o.primeira_resposta_humana_em AS "primeiraRespostaHumanaEm",o.id_paciente AS "pacienteId",
              o.id_consulta AS "consultaId",o.criado_em AS "criadoEm",o.atualizado_em AS "atualizadoEm",o.versao
            FROM crm_oportunidade o JOIN crm_interessado i ON i.id_interessado=o.id_interessado
            LEFT JOIN usuario u ON u.id=o.id_responsavel WHERE o.id_oportunidade=?
            """,id).stream().findFirst().orElseThrow(()->notFound("Oportunidade não encontrada."));
        var history=db.queryForList("""
            SELECT e.id_evento AS id,e.tipo,e.etapa_anterior AS "etapaAnterior",e.etapa_nova AS "etapaNova",
              e.automatico,e.dados,e.ocorrido_em AS "ocorridoEm",u.nome AS autor
            FROM crm_oportunidade_evento e LEFT JOIN usuario u ON u.id=e.id_usuario
            WHERE e.id_oportunidade=? ORDER BY e.ocorrido_em DESC,e.id_evento DESC LIMIT 100
            """,id);
        var reviews=db.queryForList("""
            SELECT id_revisao AS id,motivo,candidatos,status,criado_em AS "criadoEm",resolvido_em AS "resolvidoEm"
            FROM crm_identidade_revisao WHERE id_oportunidade=? ORDER BY id_revisao DESC
            """,id);
        var messages=db.queryForList("""
            SELECT id_mensagem AS id,direcao,remetente,conteudo,status_entrega AS "statusEntrega",criado_em AS "criadoEm"
            FROM mensagem WHERE id_crm_oportunidade=? ORDER BY criado_em,id_mensagem
            """,id);
        return Map.of("oportunidade",opportunity,"historico",history,"revisoes",reviews,"mensagens",messages);
    }

    public Map<String,Object> indicadores(LocalDate inicio,LocalDate fim) {
        LocalDate from=inicio==null?LocalDate.now().minusDays(30):inicio,to=fim==null?LocalDate.now():fim;
        if(to.isBefore(from)||ChronoUnit.DAYS.between(from,to)>366) throw bad("Período inválido. Use no máximo 366 dias.");
        Object[] dates={Date.valueOf(from),Date.valueOf(to.plusDays(1))};
        var sources=db.queryForList("""
            SELECT origem,count(*) AS interessados,count(*) AS oportunidades,
              count(*) FILTER(WHERE etapa='agendado') AS agendamentos,
              round(100.0*count(*) FILTER(WHERE etapa='agendado')/nullif(count(*),0),1) AS "conversaoPct"
            FROM crm_oportunidade WHERE primeiro_contato_em>=? AND primeiro_contato_em<? GROUP BY origem ORDER BY oportunidades DESC
            """,dates);
        var owners=db.queryForList("""
            SELECT coalesce(u.nome,'Sem responsável') AS responsavel,count(*) AS oportunidades,
              count(*) FILTER(WHERE o.etapa='agendado') AS agendamentos,
              round(100.0*count(*) FILTER(WHERE o.etapa='agendado')/nullif(count(*),0),1) AS "conversaoPct",
              round(avg(extract(epoch FROM (o.primeira_resposta_humana_em-o.primeiro_contato_em))/60)
                FILTER(WHERE o.primeira_resposta_humana_em IS NOT NULL),1) AS "tempoRespostaMin"
            FROM crm_oportunidade o LEFT JOIN usuario u ON u.id=o.id_responsavel
            WHERE o.primeiro_contato_em>=? AND o.primeiro_contato_em<? GROUP BY u.nome ORDER BY oportunidades DESC
            """,dates);
        return Map.of("porOrigem",sources,"porResponsavel",owners,"periodo",Map.of("inicio",from,"fim",to),
                "definicoes",Map.of("interessado","Pessoa identificada no primeiro contato, ainda sem prontuário.",
                    "oportunidade","Interesse comercial acompanhado no período pelo primeiro contato.",
                    "paciente","Cadastro administrativo criado ou associado na conversão.",
                    "agendamento","Consulta real vinculada à agenda; este é o numerador da conversão.",
                    "taxaConversao","Agendamentos reais divididos pelas oportunidades criadas no período."));
    }

    public List<Map<String,Object>> responsaveis() {
        return db.queryForList("""
            SELECT tm.id_usuario AS id,u.nome FROM tenant_membro tm JOIN usuario u ON u.id=tm.id_usuario
            WHERE tm.ativo ORDER BY u.nome
            """);
    }

    private Map<String,Object> lock(long id) { return db.queryForList("SELECT id_oportunidade,etapa,versao FROM crm_oportunidade WHERE id_oportunidade=? FOR UPDATE",id).stream().findFirst().orElseThrow(()->notFound("Oportunidade não encontrada.")); }
    private void validateCreate(CriarOportunidade i) {
        if(!CANAIS_ORIGEM.contains(i.canalOrigem())) throw bad("Canal de origem inválido.");
        if(!CANAIS_CONTATO.contains(i.canalPreferido())) throw bad("Canal preferido inválido.");
        if(blank(i.telefone())&&blank(i.email())) throw bad("Informe telefone ou e-mail.");
        if(!blank(i.telefone())) phone(i.telefone());
    }
    private void memberNullable(Integer id) { if(id!=null&&!Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM tenant_membro WHERE id_usuario=? AND ativo)",Boolean.class,id))) throw bad("Responsável não pertence à clínica."); }
    private void patient(int id) { if(!Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM paciente WHERE id_paciente=?)",Boolean.class,id))) throw notFound("Paciente não encontrado."); }
    private void event(long id,String type,String before,String after,Integer actor,boolean automatic,Long message,Map<String,Object> data) { event(id,type,before,after,actor,automatic,message,data,OffsetDateTime.now()); }
    private void event(long id,String type,String before,String after,Integer actor,boolean automatic,Long message,Map<String,Object> data,OffsetDateTime when) {
        db.update("""
            INSERT INTO crm_oportunidade_evento(id_oportunidade,tipo,etapa_anterior,etapa_nova,id_usuario,automatico,id_mensagem,dados,ocorrido_em)
            VALUES (?,?,?,?,?,?,?,CAST(? AS jsonb),?)
            """,id,type,before,after,actor,automatic,message,json(data),Timestamp.from(when.toInstant()));
    }
    private String json(Object value) { try { return json.writeValueAsString(value); } catch(JacksonException e) { throw new IllegalStateException(e); } }
    private static String phone(String value) { String p=digits(value); if(p.startsWith("55")&&p.length()>11) return p; if(p.length()==10||p.length()==11) return "55"+p; throw bad("Telefone inválido. Informe DDD e número."); }
    private static String withoutCountry(String value) { return value!=null&&value.startsWith("55")?value.substring(2):value; }
    private static String digits(String value) { return value==null?"":value.replaceAll("\\D",""); }
    private static String lower(String value) { return blank(value)?null:value.strip().toLowerCase(Locale.ROOT); }
    private static String clean(String value) { return blank(value)?"":value.strip(); }
    private static String cleanToNull(String value) { return blank(value)?null:value.strip(); }
    private static boolean blank(String value) { return value==null||value.isBlank(); }
    private static Timestamp timestamp(OffsetDateTime value) { return value==null?null:Timestamp.from(value.toInstant()); }
    private static void page(int page,int size) { if(page<0||size<1||size>100) throw bad("Paginação inválida."); }
    private static void changed(int count) { if(count==0) conflict(); }
    private static void conflict() { throw new ResponseStatusException(HttpStatus.CONFLICT,"A oportunidade foi alterada por outra pessoa. Atualize e tente novamente."); }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
    private static ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND,message); }
}
