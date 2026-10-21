package br.com.clinica.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** Exportação assíncrona, cifrada e limitada ao tenant da sessão atual. */
@Service
public class ExportacaoDadosService {
    private static final int MAX_TABELAS = 120;
    private static final long MAX_ARQUIVO_BYTES = 50L * 1024 * 1024;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcTemplate db;
    private final AccessControlService access;
    private final Executor executor;
    private final TransactionTemplate transaction;
    private final String keyText;

    public ExportacaoDadosService(JdbcTemplate db, AccessControlService access,
            @Qualifier("exportacaoExecutor") Executor executor, PlatformTransactionManager transactionManager,
            @Value("${app.export.encryption-key:}") String keyText) {
        this.db = db; this.access = access; this.executor = executor;
        this.transaction = new TransactionTemplate(transactionManager);
        this.keyText = keyText;
    }

    public Map<String,Object> solicitar(int userId) {
        access.require("dados.exportar");
        SecretKey key = encryptionKey(); // nunca cria arquivo em texto puro quando a chave não foi configurada
        int tenant = TenantContext.id();
        UUID id = UUID.randomUUID();
        byte[] token = new byte[32]; RANDOM.nextBytes(token);
        String downloadToken = Base64.getUrlEncoder().withoutPadding().encodeToString(token);
        OffsetDateTime expires = OffsetDateTime.now().plusHours(24);
        db.update("INSERT INTO exportacao_dados(id,id_clinica,id_solicitante,token_hash,token_expira_em) VALUES (?,?,?,?,?)",
                id, tenant, userId, sha256(token), expires);
        Runnable work = () -> processar(id, tenant, key);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { executor.execute(work); }
            });
        } else executor.execute(work);
        Map<String,Object> response = resumo(id);
        response.put("tokenDownload", downloadToken); // entregue uma única vez; só o hash permanece no banco
        response.put("urlDownload", "/api/exportacoes/" + id + "/download?token=" + downloadToken);
        return response;
    }

    public List<Map<String,Object>> listar() {
        access.require("dados.exportar");
        return db.queryForList("SELECT id,status,progresso,escopo,tamanho_bytes,tabelas_exportadas,erro,criado_em,iniciado_em,concluido_em,token_expira_em,baixado_em,revogado_em FROM exportacao_dados ORDER BY criado_em DESC LIMIT 50");
    }

    public Map<String,Object> resumo(UUID id) {
        access.require("dados.exportar");
        return db.queryForMap("SELECT id,status,progresso,escopo,tamanho_bytes,tabelas_exportadas,erro,criado_em,iniciado_em,concluido_em,token_expira_em,baixado_em,revogado_em FROM exportacao_dados WHERE id=?", id);
    }

    public Arquivo baixar(UUID id, String token) {
        access.require("dados.exportar");
        if (token == null || token.isBlank()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Exportação não encontrada");
        Map<String,Object> row = db.queryForMap("SELECT status,token_hash,token_expira_em,arquivo_criptografado,arquivo_nonce,arquivo_sha256 FROM exportacao_dados WHERE id=?", id);
        if (!"PRONTA".equals(row.get("status")) || ((OffsetDateTime) row.get("token_expira_em")).isBefore(OffsetDateTime.now())
                || !MessageDigest.isEqual((byte[]) row.get("token_hash"), sha256(decodeToken(token)))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Exportação não encontrada ou link expirado");
        }
        byte[] plain = decrypt((byte[]) row.get("arquivo_criptografado"), (byte[]) row.get("arquivo_nonce"), encryptionKey());
        if (!MessageDigest.isEqual((byte[]) row.get("arquivo_sha256"), sha256(plain)))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A integridade do arquivo não pôde ser confirmada");
        db.update("UPDATE exportacao_dados SET baixado_em=coalesce(baixado_em,now()) WHERE id=?", id);
        return new Arquivo(plain, "exportacao-clinica-" + id + ".zip");
    }

    public void revogar(UUID id) {
        access.require("dados.exportar");
        int changed = db.update("UPDATE exportacao_dados SET status='REVOGADA',revogado_em=now(),arquivo_criptografado=NULL,arquivo_nonce=NULL,arquivo_sha256=NULL WHERE id=? AND status IN ('PENDENTE','PROCESSANDO','PRONTA')", id);
        if (changed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Exportação não encontrada ou já indisponível");
    }

    private void processar(UUID id, int tenant, SecretKey key) {
        try {
            transaction.executeWithoutResult(s -> withTenant(tenant, () -> db.update("UPDATE exportacao_dados SET status='PROCESSANDO',progresso=5,iniciado_em=now() WHERE id=? AND status='PENDENTE'", id)));
            Export archive = transaction.execute(s -> withTenantResult(tenant, () -> criarArquivo(id)));
            if (archive == null) return;
            byte[] nonce = new byte[12]; RANDOM.nextBytes(nonce);
            byte[] encrypted = encrypt(archive.bytes(), nonce, key);
            transaction.executeWithoutResult(s -> withTenant(tenant, () -> db.update("UPDATE exportacao_dados SET status='PRONTA',progresso=100,arquivo_criptografado=?,arquivo_nonce=?,arquivo_sha256=?,tamanho_bytes=?,tabelas_exportadas=CAST(? AS jsonb),concluido_em=now() WHERE id=? AND status='PROCESSANDO'",
                    encrypted, nonce, sha256(archive.bytes()), archive.bytes().length, jsonText(archive.tables()), id)));
        } catch (Exception error) {
            transaction.executeWithoutResult(s -> withTenant(tenant, () -> db.update("UPDATE exportacao_dados SET status='FALHA',erro=?,concluido_em=now() WHERE id=? AND status<>'REVOGADA'", safeError(error), id)));
        }
    }

    private Export criarArquivo(UUID id) {
        List<String> tables = db.queryForList("SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relkind='r' AND c.relrowsecurity AND c.relforcerowsecurity AND EXISTS (SELECT 1 FROM pg_attribute a WHERE a.attrelid=c.oid AND a.attname='id_clinica' AND NOT a.attisdropped) AND c.relname<>'exportacao_dados' ORDER BY c.relname", String.class);
        if (tables.isEmpty() || tables.size() > MAX_TABELAS) throw new IllegalStateException("Catálogo de exportação inválido");
        try (var output = new ByteArrayOutputStream(); var zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            String manifest = "{\"formato\":\"clinisis-export-v1\",\"geradoEm\":\"" + OffsetDateTime.now()
                    + "\",\"tabelas\":" + jsonText(tables)
                    + ",\"observacao\":\"Cada arquivo .ndjson preserva uma linha JSON por registro.\"}";
            write(zip, "manifest.json", manifest.getBytes(StandardCharsets.UTF_8));
            int processed = 0;
            for (String table : tables) {
                StringBuilder rows = new StringBuilder();
                db.query("SELECT row_to_json(t)::text FROM " + quote(table) + " t", rs -> {
                    while (rs.next()) {
                        rows.append(rs.getString(1)).append('\n');
                        if (rows.length() > MAX_ARQUIVO_BYTES * 3) throw new IllegalStateException("A exportação excede o limite configurado");
                    }
                });
                write(zip, "dados/" + table + ".ndjson", rows.toString().getBytes(StandardCharsets.UTF_8));
                processed++;
                db.update("UPDATE exportacao_dados SET progresso=? WHERE id=? AND status='PROCESSANDO'", Math.min(95, 5 + (processed * 90 / tables.size())), id);
            }
            zip.finish();
            if (output.size() > MAX_ARQUIVO_BYTES) throw new IllegalStateException("A exportação excede 50 MB; solicite exportação assistida");
            return new Export(output.toByteArray(), tables);
        } catch (Exception e) { throw new IllegalStateException("Não foi possível montar a exportação", e); }
    }

    private static String quote(String name) { return "\"" + name.replace("\"", "\"\"") + "\""; }
    private static void write(ZipOutputStream zip, String name, byte[] bytes) throws java.io.IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }
    private static byte[] sha256(byte[] value) { try { return MessageDigest.getInstance("SHA-256").digest(value); } catch (Exception e) { throw new IllegalStateException(e); } }
    private static byte[] decodeToken(String token) { try { return Base64.getUrlDecoder().decode(token); } catch (IllegalArgumentException e) { return new byte[0]; } }
    private SecretKey encryptionKey() {
        try {
            byte[] key = Base64.getDecoder().decode(keyText == null ? "" : keyText.trim());
            if (key.length != 32) throw new IllegalArgumentException();
            return new SecretKeySpec(key, "AES");
        } catch (Exception e) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "A chave de criptografia de exportações não foi configurada"); }
    }
    private static byte[] encrypt(byte[] plain, byte[] nonce, SecretKey key) { return crypt(Cipher.ENCRYPT_MODE, plain, nonce, key); }
    private static byte[] decrypt(byte[] cipher, byte[] nonce, SecretKey key) { return crypt(Cipher.DECRYPT_MODE, cipher, nonce, key); }
    private static byte[] crypt(int mode, byte[] bytes, byte[] nonce, SecretKey key) { try { Cipher c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(mode,key,new GCMParameterSpec(128,nonce)); return c.doFinal(bytes); } catch(Exception e) { throw new ResponseStatusException(HttpStatus.CONFLICT,"Não foi possível abrir a exportação"); } }
    private static String safeError(Exception e) { String message=e.getMessage()==null?"Falha ao gerar exportação":e.getMessage(); return message.substring(0, Math.min(message.length(),500)); }
    private static String jsonText(List<String> values) {
        return values.stream().map(value -> "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }
    private void withTenant(int tenant, Runnable work) { db.queryForObject("SELECT set_config('app.tenant_id',?,true)", String.class, Integer.toString(tenant)); work.run(); }
    private <T> T withTenantResult(int tenant, java.util.concurrent.Callable<T> work) { try { db.queryForObject("SELECT set_config('app.tenant_id',?,true)", String.class, Integer.toString(tenant)); return work.call(); } catch(Exception e) { throw new IllegalStateException(e); } }
    private record Export(byte[] bytes, List<String> tables) {}
    public record Arquivo(byte[] bytes, String filename) {}
}
