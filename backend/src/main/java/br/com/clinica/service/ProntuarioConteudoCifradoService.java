package br.com.clinica.service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** AES-GCM com AAD do tenant e prontuário: ciphertext não pode ser trocado entre registros. */
@Service
public class ProntuarioConteudoCifradoService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcTemplate db;
    private final Map<Integer, SecretKey> keys;
    private final int activeVersion;
    public ProntuarioConteudoCifradoService(JdbcTemplate db,
            @Value("${app.data-encryption.key:}") String primary,
            @Value("${app.data-encryption.keys:}") String keyRing,
            @Value("${app.data-encryption.active-version:1}") int activeVersion) {
        this.db=db; this.keys=parse(primary,keyRing); this.activeVersion=activeVersion;
    }
    public void save(int prontuario, Conteudo content) {
        int tenant=TenantContext.id(); SecretKey key=activeKey(); byte[] nonce=new byte[12]; RANDOM.nextBytes(nonce);
        byte[] cipher=crypt(Cipher.ENCRYPT_MODE, serialize(content), nonce, key, aad(tenant,prontuario,activeVersion));
        db.update("INSERT INTO prontuario_conteudo_cifrado(id_prontuario,id_clinica,versao_chave,nonce,conteudo,atualizado_em) VALUES(?,?,?,?,?,now()) ON CONFLICT(id_clinica,id_prontuario) DO UPDATE SET versao_chave=excluded.versao_chave,nonce=excluded.nonce,conteudo=excluded.conteudo,atualizado_em=now()",prontuario,tenant,activeVersion,nonce,cipher);
    }
    public Conteudo load(int prontuario) {
        int tenant=TenantContext.id();
        var rows=db.queryForList("SELECT versao_chave,nonce,conteudo FROM prontuario_conteudo_cifrado WHERE id_prontuario=?",prontuario);
        if(rows.isEmpty()) return null;
        var row=rows.getFirst(); int version=((Number)row.get("versao_chave")).intValue(); SecretKey key=keys.get(version);
        if(key==null) throw new ResponseStatusException(HttpStatus.CONFLICT,"A chave necessária para abrir este prontuário não está disponível");
        return deserialize(crypt(Cipher.DECRYPT_MODE,(byte[])row.get("conteudo"),(byte[])row.get("nonce"),key,aad(tenant,prontuario,version)));
    }
    public int migrateLegacy(int limit) {
        if(limit<1||limit>500) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Limite de migração inválido");
        var rows=db.queryForList("SELECT p.id_prontuario,p.queixa_principal,p.historia_doenca_atual,p.descricao,p.exame_fisico,p.hipotese_diagnostica,p.diagnostico,p.prescricao,p.plano_terapeutico,p.conduta FROM prontuario p LEFT JOIN prontuario_conteudo_cifrado c ON c.id_prontuario=p.id_prontuario WHERE c.id_prontuario IS NULL AND (p.queixa_principal IS NOT NULL OR p.historia_doenca_atual IS NOT NULL OR p.descricao IS NOT NULL OR p.exame_fisico IS NOT NULL OR p.hipotese_diagnostica IS NOT NULL OR p.diagnostico IS NOT NULL OR p.prescricao IS NOT NULL OR p.plano_terapeutico IS NOT NULL OR p.conduta IS NOT NULL) ORDER BY p.id_prontuario LIMIT ?",limit);
        for(var r:rows){ int id=((Number)r.get("id_prontuario")).intValue(); save(id, new Conteudo(s(r,"queixa_principal"),s(r,"historia_doenca_atual"),s(r,"descricao"),s(r,"exame_fisico"),s(r,"hipotese_diagnostica"),s(r,"diagnostico"),s(r,"prescricao"),s(r,"plano_terapeutico"),s(r,"conduta"))); db.update("UPDATE prontuario SET queixa_principal=NULL,historia_doenca_atual=NULL,descricao=NULL,exame_fisico=NULL,hipotese_diagnostica=NULL,diagnostico=NULL,prescricao=NULL,plano_terapeutico=NULL,conduta=NULL WHERE id_prontuario=?",id); }
        return rows.size();
    }
    private static String s(Map<String,Object> row,String key){ return (String)row.get(key); }
    private SecretKey activeKey(){ SecretKey key=keys.get(activeVersion); if(key==null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"A criptografia clínica não foi configurada"); return key; }
    private static Map<Integer,SecretKey> parse(String primary,String ring){ Map<Integer,SecretKey> result=new HashMap<>(); if(primary!=null&&!primary.isBlank()) result.put(1,key(primary)); if(ring!=null&&!ring.isBlank()) for(String item:ring.split(",")){String[] p=item.trim().split(":",2); if(p.length==2) result.put(Integer.parseInt(p[0]),key(p[1]));} return Map.copyOf(result); }
    private static SecretKey key(String raw){ try{byte[] bytes=Base64.getDecoder().decode(raw);if(bytes.length!=32)throw new IllegalArgumentException();return new SecretKeySpec(bytes,"AES");}catch(Exception e){throw new IllegalArgumentException("Chave clínica inválida");} }
    private static byte[] crypt(int mode,byte[] input,byte[] nonce,SecretKey key,byte[] aad){try{Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(mode,key,new GCMParameterSpec(128,nonce));c.updateAAD(aad);return c.doFinal(input);}catch(Exception e){throw new ResponseStatusException(HttpStatus.CONFLICT,"Não foi possível abrir o conteúdo clínico");}}
    private static byte[] aad(int tenant,int id,int version){return ("clinisis:prontuario:"+tenant+":"+id+":"+version).getBytes(StandardCharsets.UTF_8);}
    private static byte[] serialize(Conteudo c){try{var out=new ByteArrayOutputStream();var data=new DataOutputStream(out);for(String v:c.values()){data.writeBoolean(v!=null);if(v!=null){byte[] b=v.getBytes(StandardCharsets.UTF_8);data.writeInt(b.length);data.write(b);}}data.flush();return out.toByteArray();}catch(IOException e){throw new IllegalStateException(e);}}
    private static Conteudo deserialize(byte[] raw){try{var data=new DataInputStream(new ByteArrayInputStream(raw));String[] values=new String[9];for(int i=0;i<9;i++)if(data.readBoolean()){int n=data.readInt();if(n<0||n>1_000_000)throw new IOException();values[i]=new String(data.readNBytes(n),StandardCharsets.UTF_8);}return new Conteudo(values);}catch(Exception e){throw new ResponseStatusException(HttpStatus.CONFLICT,"O conteúdo clínico cifrado é inválido");}}
    public record Conteudo(String queixaPrincipal,String historiaDoencaAtual,String descricao,String exameFisico,String hipoteseDiagnostica,String diagnostico,String prescricao,String planoTerapeutico,String conduta){ Conteudo(String[] v){this(v[0],v[1],v[2],v[3],v[4],v[5],v[6],v[7],v[8]);} String[] values(){return new String[]{queixaPrincipal,historiaDoencaAtual,descricao,exameFisico,hipoteseDiagnostica,diagnostico,prescricao,planoTerapeutico,conduta};}}
}
