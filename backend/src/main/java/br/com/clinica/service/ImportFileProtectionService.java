package br.com.clinica.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ImportFileProtectionService {
    private static final int NONCE_BYTES = 12;
    private final SecretKeySpec key;
    private final SecureRandom random;

    @Autowired
    public ImportFileProtectionService(@Value("${app.import-file.encryption-key:}") String encodedKey) {
        this(encodedKey, new SecureRandom());
    }

    ImportFileProtectionService(String encodedKey, SecureRandom random) {
        this.random = random;
        if (encodedKey == null || encodedKey.isBlank()) {
            this.key = null;
            return;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(encodedKey.trim());
            if (decoded.length != 32) throw new IllegalArgumentException("A chave precisa ter 32 bytes");
            this.key = new SecretKeySpec(decoded, "AES");
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("IMPORT_FILE_ENCRYPTION_KEY deve ser uma chave Base64 de 32 bytes", exception);
        }
    }

    ProtectedFile protect(byte[] content, int tenant, String sha256) {
        requireKey();
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            cipher.updateAAD(aad(tenant, sha256));
            return new ProtectedFile(cipher.doFinal(content), nonce, content.length, (short) 1);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Não foi possível proteger o arquivo de origem", exception);
        }
    }

    byte[] unprotect(ProtectedFile file, int tenant, String sha256) {
        requireKey();
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, file.nonce()));
            cipher.updateAAD(aad(tenant, sha256));
            return cipher.doFinal(file.ciphertext());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("O arquivo protegido não passou na verificação de integridade", exception);
        }
    }

    private void requireKey() {
        if (key == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Configure IMPORT_FILE_ENCRYPTION_KEY antes de importar arquivos");
    }

    private static byte[] aad(int tenant, String sha256) {
        return (tenant + ":" + sha256).getBytes(StandardCharsets.UTF_8);
    }

    record ProtectedFile(byte[] ciphertext, byte[] nonce, int originalSize, short keyVersion) {}
}
