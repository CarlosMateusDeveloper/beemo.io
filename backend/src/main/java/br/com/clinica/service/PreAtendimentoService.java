package br.com.clinica.service;

import br.com.clinica.dto.PreAtendimentoDtos;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Service
public class PreAtendimentoService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_FILE_SIZE = 8 * 1024 * 1024;
    private static final Set<String> SCOPES = Set.of("cadastro","formulario","documentos","orientacoes");
    private static final Set<String> PATIENT_FIELDS = Set.of("nome","email","ddd","numero","cep","logradouro",
            "numeroEndereco","complemento","bairro","cidade","uf","dataNascimento","cpf");
    private static final String DEFAULT_FIELDS = """
            [{"chave":"telefone","rotulo":"Telefone","tipo":"telefone","obrigatorio":true,"revisao":false},
             {"chave":"endereco","rotulo":"Endereço","tipo":"endereco","obrigatorio":false,"revisao":false},
             {"chave":"alergias","rotulo":"Possui alguma alergia?","tipo":"texto","obrigatorio":true,"revisao":true},
             {"chave":"medicamentos","rotulo":"Medicamentos em uso","tipo":"texto","obrigatorio":false,"revisao":true},
             {"chave":"motivo","rotulo":"Motivo da consulta","tipo":"textarea","obrigatorio":true,"revisao":true}]
            """;
    private static final String DEFAULT_GUIDANCE = """
            [{"titulo":"Antes da consulta","texto":"Chegue com 15 minutos de antecedência e traga um documento com foto."},
             {"titulo":"Documentos","texto":"Envie somente documentos solicitados pela clínica."}]
            """;

    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final String frontend;

    public PreAtendimentoService(JdbcTemplate db, ObjectMapper json,
            @Value("${app.auth.frontend-url:http://localhost:5173}") String frontend) {
        this.db = db;
        this.json = json;
        this.frontend = frontend.replaceAll("/+$", "");
    }

    @Transactional
    public Map<String,Object> criarLink(int consultaId, PreAtendimentoDtos.CriarLink input, int actor) {
        var consultation = db.queryForList("""
                SELECT c.id_consulta,c.id_paciente,p.nome,p.ddd,p.numero
                FROM consulta c JOIN paciente p ON p.id_paciente=c.id_paciente
                WHERE c.id_consulta=?
                """, consultaId).stream().findFirst().orElseThrow(() -> notFound("Consulta não encontrada"));
        long form = formularioPadrao(actor);
        var records = db.queryForList("""
                INSERT INTO pre_atendimento(id_consulta,id_paciente,id_formulario)
                VALUES (?,?,?) ON CONFLICT(id_clinica,id_consulta) DO UPDATE SET atualizado_em=now()
                WHERE pre_atendimento.id_paciente=EXCLUDED.id_paciente
                RETURNING id_pre_atendimento
                """, consultaId, consultation.get("id_paciente"), form);
        if (records.isEmpty()) throw conflict("A consulta mudou de paciente. Revise o pré-atendimento anterior antes de gerar outro link.");
        long pre = ((Number)records.getFirst().get("id_pre_atendimento")).longValue();
        db.update("UPDATE auth_pre_atendimento_acesso SET revogado_em=now() WHERE id_clinica=? AND id_consulta=? AND revogado_em IS NULL",
                TenantContext.id(), consultaId);
        String token = randomToken();
        int hours = input != null && input.validadeHoras() != null ? Math.max(1, Math.min(168, input.validadeHoras())) : 48;
        List<String> scopes = input == null || input.escopo() == null || input.escopo().isEmpty()
                ? List.copyOf(SCOPES) : input.escopo().stream().filter(SCOPES::contains).distinct().toList();
        if (input != null && input.escopo() != null && !SCOPES.containsAll(input.escopo()))
            throw bad("O link contém ações desconhecidas");
        if (scopes.isEmpty()) throw bad("Informe ao menos uma ação permitida");
        db.update("""
                INSERT INTO auth_pre_atendimento_acesso(token_hash,id_clinica,id_consulta,id_pre_atendimento,escopo,expira_em,criado_por)
                VALUES (?,?,?,?,CAST(string_to_array(?, ',') AS varchar(40)[]),now()+make_interval(hours => ?),?)
                """, sha256(token), TenantContext.id(), consultaId, pre, String.join(",",scopes), hours, actor);
        evento(pre,"link_criado",actor,Map.of("validadeHoras",hours,"escopo",scopes));
        String url = frontend + "/pre-atendimento#token=" + token;
        String phone = "55" + consultation.get("ddd") + consultation.get("numero");
        String message = "Olá, " + consultation.get("nome") + ". Complete seu pré-atendimento com segurança: " + url;
        return Map.of("preAtendimentoId",pre,"url",url,"expiraEmHoras",hours,"escopo",scopes,
                "whatsappUrl","https://wa.me/"+phone+"?text="+URLEncoder.encode(message,StandardCharsets.UTF_8));
    }

    public List<Map<String,Object>> fila(String status) {
        String filter = status == null || status.isBlank() ? "" : " AND pa.status=?";
        Object[] args = filter.isEmpty() ? new Object[]{} : new Object[]{status};
        return db.queryForList("""
                SELECT pa.id_pre_atendimento AS id,pa.id_consulta AS "consultaId",pa.id_paciente AS "pacienteId",
                       p.nome AS paciente,pa.status,pa.versao,pa.campos_pendentes::text AS "camposPendentes",
                       pa.enviado_em AS "enviadoEm",pa.atualizado_em AS "atualizadoEm",count(a.id_anexo) AS anexos
                FROM pre_atendimento pa JOIN paciente p ON p.id_paciente=pa.id_paciente
                LEFT JOIN pre_atendimento_anexo a ON a.id_pre_atendimento=pa.id_pre_atendimento
                WHERE 1=1""" + filter + """
                GROUP BY pa.id_pre_atendimento,p.nome ORDER BY
                  CASE pa.status WHEN 'enviado' THEN 0 WHEN 'pendente' THEN 1 ELSE 2 END,pa.atualizado_em DESC
                """, args).stream().map(this::decodeJsonColumns).toList();
    }

    public Map<String,Object> detalhe(long id) {
        var result = new LinkedHashMap<>(db.queryForMap("""
                SELECT pa.id_pre_atendimento AS id,pa.id_consulta AS "consultaId",pa.id_paciente AS "pacienteId",
                       p.nome AS paciente,pa.status,pa.versao,pa.dados_propostos::text AS "dadosPropostos",
                       pa.respostas::text AS respostas,pa.campos_pendentes::text AS "camposPendentes",
                       f.titulo AS formulario,f.versao AS "formularioVersao",f.campos::text AS campos,
                       f.orientacoes::text AS orientacoes,pa.enviado_em AS "enviadoEm",pa.revisado_em AS "revisadoEm"
                FROM pre_atendimento pa JOIN paciente p ON p.id_paciente=pa.id_paciente
                JOIN pre_atendimento_formulario_versao f ON f.id_formulario=pa.id_formulario
                WHERE pa.id_pre_atendimento=?
                """, id));
        result.putAll(Map.of("anexos", anexos(id), "historico", historico(id)));
        return decodeJsonColumns(result);
    }

    @Transactional
    public Map<String,Object> revisar(long id, PreAtendimentoDtos.Revisar input, int actor) {
        var row = db.queryForMap("SELECT id_paciente,dados_propostos::text,status FROM pre_atendimento WHERE id_pre_atendimento=? FOR UPDATE",id);
        if (!Set.of("enviado","pendente").contains(Objects.toString(row.get("status")))) throw conflict("Este pré-atendimento não aguarda revisão");
        List<String> approved = input.camposAprovados() == null ? List.of() : input.camposAprovados().stream().filter(PATIENT_FIELDS::contains).distinct().toList();
        Map<String,Object> proposed = map(Objects.toString(row.get("dados_propostos"),"{}"));
        aplicarCadastro(((Number)row.get("id_paciente")).intValue(),proposed,approved);
        List<String> pending = input.camposPendentes() == null ? List.of() : input.camposPendentes().stream().distinct().toList();
        if (input.status().equals("aprovado") && !pending.isEmpty()) throw bad("Um pré-atendimento aprovado não pode manter pendências");
        db.update("""
                UPDATE pre_atendimento SET status=?,campos_pendentes=CAST(? AS jsonb),revisado_em=now(),
                    revisado_por=?,atualizado_em=now(),versao=versao+1 WHERE id_pre_atendimento=?
                """, input.status(), write(pending), actor, id);
        evento(id,"revisao_"+input.status(),actor,Map.of("camposAprovados",approved,"camposPendentes",pending,
                "observacao",Objects.toString(input.observacao(),"")));
        return detalhe(id);
    }

    @Transactional
    public void revogar(long id, int actor) {
        int changed = db.update("UPDATE auth_pre_atendimento_acesso SET revogado_em=now() WHERE id_clinica=? AND id_pre_atendimento=? AND revogado_em IS NULL",TenantContext.id(),id);
        if (changed == 0) throw notFound("Acesso ativo não encontrado");
        evento(id,"link_revogado",actor,Map.of());
    }

    @Transactional
    public Map<String,Object> estadoPublico(String token) {
        var access = acesso(token,false);
        return Map.of("status","ativo","clinica",access.get("clinica"),"requerVerificacao",true,
                "expiraEm",access.get("expira_em"),"escopo",scopes(access));
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public Map<String,Object> verificar(String token, PreAtendimentoDtos.Verificar input) {
        var access = acesso(token,true);
        boolean matches = Boolean.TRUE.equals(db.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM consulta c JOIN paciente p ON p.id_paciente=c.id_paciente
                  WHERE c.id_consulta=? AND p.data_nascimento=? AND right(p.cpf,4)=?)
                """, Boolean.class, access.get("id_consulta"), Date.valueOf(input.dataNascimento()), input.cpfUltimos4()));
        if (!matches) {
            db.update("UPDATE auth_pre_atendimento_acesso SET tentativas=tentativas+1 WHERE token_hash=?",sha256(token));
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Dados de confirmação inválidos");
        }
        String session = randomToken();
        db.update("DELETE FROM auth_pre_atendimento_sessao WHERE token_hash=? OR expira_em<=now()",sha256(token));
        db.update("INSERT INTO auth_pre_atendimento_sessao(sessao_hash,token_hash,expira_em) VALUES (?,?,now()+interval '30 minutes')",sha256(session),sha256(token));
        db.update("UPDATE auth_pre_atendimento_acesso SET tentativas=0,ultimo_acesso_em=now() WHERE token_hash=?",sha256(token));
        return Map.of("sessao",session,"expiraEmMinutos",30);
    }

    @Transactional
    public Map<String,Object> dadosPublicos(String token, String session) {
        var access = sessao(token,session);
        var granted = scopes(access);
        var row = new LinkedHashMap<>(db.queryForMap("""
                SELECT pa.id_pre_atendimento AS id,pa.status,pa.versao,pa.dados_propostos::text AS "dadosPropostos",
                       pa.respostas::text AS respostas,p.nome,p.email,p.ddd,p.numero,p.cep,p.logradouro,
                       p.numero_endereco AS "numeroEndereco",p.complemento,p.bairro,p.cidade,p.uf,
                       a.data_slot AS "dataConsulta",a.hora_slot AS "horaConsulta",m.nome AS profissional,
                       f.titulo AS formulario,f.versao AS "formularioVersao",f.campos::text AS campos,
                       f.orientacoes::text AS orientacoes
                FROM pre_atendimento pa JOIN paciente p ON p.id_paciente=pa.id_paciente
                JOIN consulta c ON c.id_consulta=pa.id_consulta JOIN agenda a ON a.id_agenda=c.id_agenda
                JOIN medico m ON m.id_medico=a.id_medico
                JOIN pre_atendimento_formulario_versao f ON f.id_formulario=pa.id_formulario
                WHERE pa.id_pre_atendimento=?
                """, access.get("id_pre_atendimento")));
        row.put("escopo", granted);
        if (granted.contains("documentos"))
            row.put("anexos",anexos(((Number)access.get("id_pre_atendimento")).longValue()));
        if (!granted.contains("cadastro"))
            for (String field : List.of("dadosPropostos","nome","email","ddd","numero","cep","logradouro",
                    "numeroEndereco","complemento","bairro","cidade","uf")) row.remove(field);
        if (!granted.contains("formulario"))
            for (String field : List.of("respostas","formulario","formularioVersao","campos")) row.remove(field);
        if (!granted.contains("orientacoes")) row.remove("orientacoes");
        return decodeJsonColumns(row);
    }

    @Transactional
    public Map<String,Object> salvar(String token, String session, PreAtendimentoDtos.Salvar input) {
        var access = sessao(token,session);
        long id = ((Number)access.get("id_pre_atendimento")).longValue();
        boolean cadastro = scopes(access).contains("cadastro");
        boolean formulario = scopes(access).contains("formulario");
        if (!cadastro && !input.dadosPropostos().isEmpty()) requireScope(access,"cadastro");
        if (!formulario && !input.respostas().isEmpty()) requireScope(access,"formulario");
        validarChaves(input.dadosPropostos());
        int changed = db.update("""
                UPDATE pre_atendimento SET dados_propostos=CASE WHEN ? THEN CAST(? AS jsonb) ELSE dados_propostos END,
                  respostas=CASE WHEN ? THEN CAST(? AS jsonb) ELSE respostas END,
                  versao=versao+1,atualizado_em=now() WHERE id_pre_atendimento=? AND versao=? AND status IN ('rascunho','pendente')
                """,cadastro,write(input.dadosPropostos()),formulario,write(input.respostas()),id,input.versao());
        if(changed==0) throw conflict("Os dados foram alterados em outro acesso. Recarregue antes de continuar.");
        evento(id,"rascunho_salvo",null,Map.of("versaoAnterior",input.versao()));
        return dadosPublicos(token,session);
    }

    @Transactional
    public Map<String,Object> enviar(String token, String session, int versao) {
        var access = sessao(token,session);
        if (scopes(access).stream().noneMatch(Set.of("cadastro","formulario","documentos")::contains))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Este link não permite envio");
        long id = ((Number)access.get("id_pre_atendimento")).longValue();
        int changed=db.update("""
                UPDATE pre_atendimento SET status='enviado',enviado_em=now(),atualizado_em=now(),versao=versao+1
                WHERE id_pre_atendimento=? AND versao=? AND status IN ('rascunho','pendente')
                """,id,versao);
        if(changed==0) throw conflict("O formulário mudou ou já foi enviado. Recarregue antes de continuar.");
        evento(id,"enviado",null,Map.of("versaoAnterior",versao));
        return dadosPublicos(token,session);
    }

    @Transactional
    public Map<String,Object> anexar(String token, String session, MultipartFile file) {
        var access=sessao(token,session);
        requireScope(access,"documentos");
        long pre=((Number)access.get("id_pre_atendimento")).longValue();
        String status=db.queryForObject("SELECT status FROM pre_atendimento WHERE id_pre_atendimento=? FOR UPDATE",String.class,pre);
        if (!Set.of("rascunho","pendente").contains(status)) throw conflict("Este pré-atendimento já foi enviado");
        if (file.getSize() > MAX_FILE_SIZE) throw bad("O arquivo deve ter no máximo 8 MB");
        byte[] bytes;
        try { bytes=file.getBytes(); } catch(Exception error) { throw bad("Não foi possível ler o arquivo"); }
        String mime=validarArquivo(file.getOriginalFilename(),file.getContentType(),bytes);
        String digest=sha256(bytes);
        var inserted=db.queryForList("""
                    INSERT INTO pre_atendimento_anexo(id_pre_atendimento,nome_original,tipo_mime,tamanho,sha256,conteudo)
                    VALUES (?,?,?,?,?,?) ON CONFLICT (id_clinica,id_pre_atendimento,sha256) DO NOTHING
                    RETURNING id_anexo
                    """,pre,safeName(file.getOriginalFilename()),mime,bytes.length,digest,bytes);
        if (!inserted.isEmpty()) {
            long id=((Number)inserted.getFirst().get("id_anexo")).longValue();
            evento(pre,"anexo_enviado",null,Map.of("anexoId",id,"tipo",mime,"tamanho",bytes.length));
            return Map.of("id",id,"nome",safeName(file.getOriginalFilename()),"tipo",mime,"tamanho",bytes.length);
        }
        return db.queryForMap("SELECT id_anexo AS id,nome_original AS nome,tipo_mime AS tipo,tamanho FROM pre_atendimento_anexo WHERE id_pre_atendimento=? AND sha256=?",pre,digest);
    }

    @Transactional(readOnly=true)
    public Download baixar(long pre,long attachment) {
        var row=db.queryForMap("SELECT nome_original,tipo_mime,conteudo FROM pre_atendimento_anexo WHERE id_pre_atendimento=? AND id_anexo=?",pre,attachment);
        return new Download(Objects.toString(row.get("nome_original")),Objects.toString(row.get("tipo_mime")),(byte[])row.get("conteudo"));
    }

    @Transactional
    public Download baixarPublico(String token,String session,long attachment) {
        var access=sessao(token,session);
        requireScope(access,"documentos");
        return baixar(((Number)access.get("id_pre_atendimento")).longValue(),attachment);
    }

    private Map<String,Object> acesso(String raw, boolean countLocked) {
        if(raw==null || raw.length()<32) throw notFound("Acesso não encontrado");
        var row=db.queryForList("""
                SELECT a.*,c.nome AS clinica,CASE WHEN a.revogado_em IS NOT NULL THEN 'revogado'
                  WHEN a.expira_em<=now() THEN 'expirado' WHEN a.tentativas>=a.max_tentativas THEN 'bloqueado' ELSE 'ativo' END AS estado
                FROM auth_pre_atendimento_acesso a JOIN clinica c ON c.id_clinica=a.id_clinica WHERE a.token_hash=?
                """ + (countLocked ? " FOR UPDATE OF a" : ""),sha256(raw))
                .stream().findFirst().orElseThrow(() -> notFound("Acesso não encontrado"));
        String state=Objects.toString(row.get("estado"));
        if(!state.equals("ativo")) throw new ResponseStatusException(HttpStatus.GONE,
                state.equals("expirado")?"Este link expirou.":state.equals("revogado")?"Este link foi revogado.":"Este link foi bloqueado após tentativas inválidas.");
        tenant(((Number)row.get("id_clinica")).intValue());
        return row;
    }

    private Map<String,Object> sessao(String token,String rawSession) {
        var access=acesso(token,false);
        if(rawSession==null || rawSession.length()<32) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Confirme sua identidade para continuar");
        boolean valid=Boolean.TRUE.equals(db.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM auth_pre_atendimento_sessao WHERE sessao_hash=? AND token_hash=? AND expira_em>now())
                """,Boolean.class,sha256(rawSession),sha256(token)));
        if(!valid) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Sua confirmação expirou. Confirme sua identidade novamente.");
        db.update("UPDATE auth_pre_atendimento_sessao SET ultimo_acesso_em=now() WHERE sessao_hash=?",sha256(rawSession));
        return access;
    }

    private long formularioPadrao(int actor) {
        var existing=db.queryForList("SELECT id_formulario FROM pre_atendimento_formulario_versao WHERE codigo='pre-consulta' AND status='aprovado' ORDER BY versao DESC LIMIT 1");
        if(!existing.isEmpty()) return ((Number)existing.getFirst().get("id_formulario")).longValue();
        return db.queryForObject("""
                INSERT INTO pre_atendimento_formulario_versao(codigo,versao,titulo,campos,orientacoes,criado_por)
                VALUES ('pre-consulta',1,'Pré-atendimento',CAST(? AS jsonb),CAST(? AS jsonb),?) RETURNING id_formulario
                """,Long.class,DEFAULT_FIELDS,DEFAULT_GUIDANCE,actor);
    }

    private void aplicarCadastro(int patient,Map<String,Object> data,List<String> approved) {
        for(String field:approved) {
            if(!data.containsKey(field)) continue;
            Object value=data.get(field);
            String column=switch(field) {
                case "numeroEndereco" -> "numero_endereco";
                case "dataNascimento" -> "data_nascimento";
                default -> field;
            };
            if(field.equals("dataNascimento")) value=Date.valueOf(LocalDate.parse(Objects.toString(value)));
            if(field.equals("cpf") && !Objects.toString(value).matches("\\d{11}")) throw bad("CPF inválido");
            if(field.equals("ddd") && !Objects.toString(value).matches("\\d{2}")) throw bad("DDD inválido");
            if(field.equals("uf") && !Objects.toString(value).matches("[A-Za-z]{2}")) throw bad("UF inválida");
            db.update("UPDATE paciente SET "+column+"=? WHERE id_paciente=?",value,patient);
        }
    }

    private List<Map<String,Object>> anexos(long pre) {
        return db.queryForList("SELECT id_anexo AS id,nome_original AS nome,tipo_mime AS tipo,tamanho,criado_em AS \"criadoEm\" FROM pre_atendimento_anexo WHERE id_pre_atendimento=? ORDER BY criado_em",pre);
    }
    private List<Map<String,Object>> historico(long pre) {
        return db.queryForList("SELECT tipo,detalhes::text,criado_em AS \"criadoEm\" FROM pre_atendimento_evento WHERE id_pre_atendimento=? ORDER BY criado_em",pre)
                .stream().map(this::decodeJsonColumns).toList();
    }
    private void evento(long pre,String type,Integer actor,Map<String,Object> details) {
        db.update("INSERT INTO pre_atendimento_evento(id_pre_atendimento,tipo,id_usuario,detalhes) VALUES (?,?,?,CAST(? AS jsonb))",pre,type,actor,write(details));
    }
    private void tenant(int id) { db.queryForObject("SELECT set_config('app.tenant_id',?,true)",String.class,Integer.toString(id)); }
    private void requireScope(Map<String,Object> access,String scope) {
        boolean allowed=scopes(access).contains(scope);
        if(!allowed) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Este link não permite essa ação");
    }
    private List<String> scopes(Map<String,Object> access) {
        Object value=access.get("escopo");
        if(value instanceof java.sql.Array array) return arrayToList(array);
        if(value instanceof String[] values) return List.of(values);
        return List.of();
    }
    private List<String> arrayToList(java.sql.Array value) {
        try { return List.of((String[])value.getArray()); } catch(Exception error) { return List.of(); }
    }
    static String validarArquivo(String name,String informed,byte[] bytes) {
        if(bytes.length==0 || bytes.length>MAX_FILE_SIZE) throw bad("O arquivo deve ter no máximo 8 MB");
        String mime;
        if(starts(bytes,new int[]{0x25,0x50,0x44,0x46})) mime="application/pdf";
        else if(starts(bytes,new int[]{0xFF,0xD8,0xFF})) mime="image/jpeg";
        else if(starts(bytes,new int[]{0x89,0x50,0x4E,0x47,0x0D,0x0A,0x1A,0x0A})) mime="image/png";
        else throw bad("Envie um arquivo PDF, JPG ou PNG válido");
        if(informed!=null && !informed.equals("application/octet-stream") && !informed.equals(mime)) throw bad("O conteúdo do arquivo não corresponde ao tipo informado");
        if(mime.equals("application/pdf")) {
            String text=new String(bytes,StandardCharsets.ISO_8859_1).toLowerCase();
            if(text.contains("/javascript") || text.contains("/openaction") || text.contains("/launch")) throw bad("PDF com conteúdo ativo não é permitido");
        }
        return mime;
    }
    private static boolean starts(byte[] bytes,int[] signature) {
        if(bytes.length<signature.length) return false;
        for(int i=0;i<signature.length;i++) if((bytes[i]&0xff)!=signature[i]) return false;
        return true;
    }
    private static String safeName(String value) {
        String name=Objects.toString(value,"documento").replaceAll("[\\r\\n\\\\/]","_").replaceAll("[^\\p{L}\\p{N}._ -]","_").strip();
        return name.isBlank()?"documento":name.substring(0,Math.min(180,name.length()));
    }
    private void validarChaves(Map<String,Object> data) {
        var invalid=new ArrayList<>(data.keySet()); invalid.removeAll(PATIENT_FIELDS);
        if(!invalid.isEmpty()) throw bad("Campos cadastrais não permitidos: "+invalid);
    }
    private Map<String,Object> decodeJsonColumns(Map<String,Object> source) {
        var result=new LinkedHashMap<String,Object>(source);
        for(String key:List.of("dadosPropostos","respostas","camposPendentes","campos","orientacoes","detalhes")) {
            Object value=result.get(key); if(value instanceof String text) result.put(key,read(text));
        }
        return result;
    }
    @SuppressWarnings("unchecked") private Map<String,Object> map(String value) { return (Map<String,Object>)read(value); }
    private Object read(String value) { try { return json.readValue(value,Object.class); } catch(Exception error) { throw new IllegalStateException("JSON inválido persistido",error); } }
    private String write(Object value) { try { return json.writeValueAsString(value); } catch(Exception error) { throw new IllegalArgumentException("Dados inválidos",error); } }
    static String randomToken() { byte[] bytes=new byte[32]; RANDOM.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    static String sha256(String value) { return sha256(value.getBytes(StandardCharsets.UTF_8)); }
    private static String sha256(byte[] value) { try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); } catch(Exception error) { throw new IllegalStateException(error); } }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT,message); }
    private static ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND,message); }
    public record Download(String name,String type,byte[] content) {}
}
