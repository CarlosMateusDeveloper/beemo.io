package br.com.clinica.service;

import br.com.clinica.dto.IntegracaoCredencialRequest;
import br.com.clinica.integration.ClinicaIntegrationAdapters;
import br.com.clinica.integration.IntegracaoPrincipal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.time.ZoneId;
import java.time.zone.ZoneRulesException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class IntegracaoCredencialService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Set<String> ESCOPOS = Set.of("eventos:escrever", "estado:ler");
    private static final Set<String> PRESENCA_EXPLICITA = Set.of("realizado", "atendido", "compareceu", "concluido");
    private static final Map<String,String> PADRAO = Map.of(
            "Agendado", "AGENDADO", "Confirmado", "CONFIRMADO", "Faltou", "FALTOU",
            "Realizado", "REALIZADO", "Cancelado", "CANCELADO");

    private final JdbcTemplate db;
    private final ClinicaIntegrationAdapters adapters;

    public IntegracaoCredencialService(JdbcTemplate db, ClinicaIntegrationAdapters adapters) {
        this.db = db;
        this.adapters = adapters;
    }

    @Transactional
    public Map<String,Object> criar(IntegracaoCredencialRequest request, int autor) {
        adapters.obter(request.adaptador());
        zone(request.fusoHorario());
        if (!ESCOPOS.containsAll(request.escopos())) throw bad("Escopo de integração inválido");
        int tenant = TenantContext.id();
        long source = db.queryForObject("""
                INSERT INTO integracao_fonte_dados(
                    id_clinica,codigo,nome,fuso_horario,adaptador,estado_conexao
                ) VALUES (?,?,?,?,?,'CONFIGURADA')
                ON CONFLICT(id_clinica,codigo) DO UPDATE SET
                    nome=excluded.nome,fuso_horario=excluded.fuso_horario,
                    adaptador=excluded.adaptador,estado_conexao='CONFIGURADA',atualizado_em=now()
                RETURNING id_fonte
                """, Long.class, tenant, request.fonteCodigo(), request.fonteNome().trim(),
                request.fusoHorario(), request.adaptador());
        Map<String,String> mappings = new LinkedHashMap<>(PADRAO);
        if (request.situacoes() != null) mappings.putAll(request.situacoes());
        for (var mapping : mappings.entrySet()) salvarMapeamento(source, mapping.getKey(), mapping.getValue());

        String identifier = random(12);
        String token = "cls." + identifier + "." + random(32);
        long credential = db.queryForObject("""
                INSERT INTO auth_integracao_credencial(
                    identificador,segredo_hash,id_clinica,id_fonte,nome,escopos,criado_por
                ) VALUES (?,?,?,?,?,string_to_array(?,','),?) RETURNING id_credencial
                """, Long.class, identifier, sha256(token), tenant, source, request.nome().trim(),
                String.join(",", request.escopos().stream().sorted().toList()), autor);
        var response = new LinkedHashMap<String,Object>();
        response.put("id", credential);
        response.put("fonte", request.fonteCodigo());
        response.put("chave", token);
        response.put("escopos", request.escopos());
        response.put("aviso", "Copie esta chave agora. Ela não será exibida novamente.");
        return response;
    }

    public Optional<IntegracaoPrincipal> autenticar(String token) {
        if (token == null || !token.startsWith("cls.")) return Optional.empty();
        String[] parts = token.split("\\.", 3);
        if (parts.length != 3 || parts[1].isBlank() || parts[2].length() < 32) return Optional.empty();
        var rows = db.queryForList("""
                SELECT id_credencial,id_clinica,id_fonte,segredo_hash,
                    array_to_string(escopos,',') AS escopos
                FROM auth_integracao_credencial
                WHERE identificador=? AND revogada_em IS NULL
                  AND (expira_em IS NULL OR expira_em>now())
                """, parts[1]);
        if (rows.size() != 1) return Optional.empty();
        var row = rows.getFirst();
        if (!MessageDigest.isEqual(sha256(token).getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                Objects.toString(row.get("segredo_hash"), "").trim()
                        .getBytes(java.nio.charset.StandardCharsets.US_ASCII))) return Optional.empty();
        long id = ((Number) row.get("id_credencial")).longValue();
        db.update("UPDATE auth_integracao_credencial SET ultimo_uso_em=now() WHERE id_credencial=?", id);
        Set<String> scopes = Set.of(Objects.toString(row.get("escopos"), "").split(","));
        return Optional.of(new IntegracaoPrincipal(id, ((Number) row.get("id_clinica")).intValue(),
                ((Number) row.get("id_fonte")).longValue(), scopes));
    }

    public List<Map<String,Object>> listar() {
        return db.queryForList("""
                SELECT c.id_credencial AS id,c.nome,array_to_string(c.escopos,',') AS escopos,
                    c.criado_em::text AS "criadaEm",c.ultimo_uso_em::text AS "ultimoUsoEm",
                    c.revogada_em::text AS "revogadaEm",f.codigo AS fonte,f.nome AS "fonteNome"
                FROM auth_integracao_credencial c
                JOIN integracao_fonte_dados f ON f.id_fonte=c.id_fonte
                WHERE c.id_clinica=? ORDER BY c.id_credencial DESC
                """, TenantContext.id());
    }

    @Transactional
    public void revogar(long id) {
        int changed = db.update("""
                UPDATE auth_integracao_credencial SET revogada_em=coalesce(revogada_em,now())
                WHERE id_credencial=? AND id_clinica=?
                """, id, TenantContext.id());
        if (changed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Credencial não encontrada");
    }

    private void salvarMapeamento(long source, String raw, String normalized) {
        String display = Objects.toString(raw, "").trim();
        String key = normalize(display);
        if (key.isBlank()) throw bad("Situação de origem inválida");
        if ("REALIZADO".equals(normalized) && !PRESENCA_EXPLICITA.contains(key)) {
            throw bad("Somente uma situação explícita de presença pode ser mapeada como REALIZADO");
        }
        db.update("""
                INSERT INTO integracao_situacao_mapeamento(
                    id_fonte,situacao_chave,situacao_origem,situacao_normalizada
                ) VALUES (?,?,?,?) ON CONFLICT(id_clinica,id_fonte,situacao_chave) DO UPDATE SET
                    situacao_origem=excluded.situacao_origem,
                    situacao_normalizada=excluded.situacao_normalizada,atualizado_em=now()
                """, source, key, display, normalized);
    }

    static String normalize(String value) {
        return Normalizer.normalize(Objects.toString(value, ""), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String random(int bytes) {
        byte[] value = new byte[bytes]; RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static void zone(String value) {
        try { ZoneId.of(value); }
        catch (ZoneRulesException exception) { throw bad("Fuso horário inválido"); }
    }
    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
