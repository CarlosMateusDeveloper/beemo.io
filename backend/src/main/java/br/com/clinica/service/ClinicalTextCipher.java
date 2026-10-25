package br.com.clinica.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Envelope enc:version:nonce:ciphertext; the GCM tag is part of ciphertext. */
@Component
public class ClinicalTextCipher {
    private static final String PREFIX = "enc:";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final Set<String> CHART_FIELDS = Set.of(
            "queixa_principal", "historia_doenca_atual", "descricao", "exame_fisico",
            "hipotese_diagnostica", "diagnostico", "prescricao", "plano_terapeutico", "conduta");
    private final SecureRandom random = new SecureRandom();
    private final Map<String, SecretKey> keys;
    private final String activeVersion;
    private final boolean allowLegacyRead;

    public ClinicalTextCipher(
            @Value("${app.clinical-encryption.key-version:v1}") String activeVersion,
            @Value("${app.clinical-encryption.key:}") String activeKey,
            @Value("${app.clinical-encryption.previous-keys:}") String previousKeys,
            @Value("${app.clinical-encryption.allow-legacy-read:false}") boolean allowLegacyRead) {
        if (!activeVersion.matches("v[1-9][0-9]*")) throw new IllegalArgumentException("Invalid clinical key version");
        this.activeVersion = activeVersion;
        this.allowLegacyRead = allowLegacyRead;
        Map<String, SecretKey> loaded = new HashMap<>();
        if (!activeKey.isBlank()) loaded.put(activeVersion, decodeKey(activeKey));
        if (!previousKeys.isBlank()) {
            for (String entry : previousKeys.split(",")) {
                String[] pair = entry.trim().split(":", 2);
                if (pair.length != 2 || !pair[0].matches("v[1-9][0-9]*") || loaded.putIfAbsent(pair[0], decodeKey(pair[1])) != null) {
                    throw new IllegalArgumentException("Invalid clinical previous key configuration");
                }
            }
        }
        keys = Map.copyOf(loaded);
    }

    public boolean isEncrypted(String value) { return value != null && value.startsWith(PREFIX); }
    public String activeVersion() { return activeVersion; }
    public void requireActiveKey() { requireKey(activeVersion); }
    public void requireStrictProductionMode() {
        requireActiveKey();
        if (allowLegacyRead) throw new IllegalStateException("Legacy clinical reads must be disabled in production");
    }

    public String encrypt(String value, int tenantId, String table, String field, long recordId) {
        if (value == null) return null;
        validateContext(tenantId, table, field, recordId);
        SecretKey key = requireKey(activeVersion);
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(aad(tenantId, table, field, recordId));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return PREFIX + activeVersion + ":" + Base64.getUrlEncoder().withoutPadding().encodeToString(nonce)
                    + ":" + Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Clinical encryption failed", e);
        }
    }

    public String decrypt(String value, int tenantId, String table, String field, long recordId) {
        if (value == null) return null;
        validateContext(tenantId, table, field, recordId);
        if (!isEncrypted(value)) {
            if (allowLegacyRead) return value;
            throw new IllegalStateException("Unencrypted clinical value found; run the migration");
        }
        String[] parts = value.split(":", 4);
        if (parts.length != 4 || !parts[1].matches("v[1-9][0-9]*")) {
            throw new IllegalStateException("Invalid clinical ciphertext envelope");
        }
        SecretKey key = requireKey(parts[1]);
        try {
            byte[] nonce = Base64.getUrlDecoder().decode(parts[2]);
            byte[] encrypted = Base64.getUrlDecoder().decode(parts[3]);
            if (nonce.length != NONCE_BYTES || encrypted.length < TAG_BITS / 8) {
                throw new IllegalStateException("Invalid clinical ciphertext envelope");
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(aad(tenantId, table, field, recordId));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // Never include the value, nonce, AAD or key in an exception or log.
            throw new IllegalStateException("Clinical ciphertext authentication failed", e);
        }
    }

    private SecretKey requireKey(String version) {
        SecretKey key = keys.get(version);
        if (key == null) throw new IllegalStateException("Clinical encryption key unavailable for " + version);
        return key;
    }

    private static SecretKey decodeKey(String encoded) {
        try {
            byte[] raw = Base64.getDecoder().decode(encoded.trim());
            if (raw.length != 32) throw new IllegalArgumentException("Clinical key must be 32 bytes");
            return new SecretKeySpec(raw, "AES");
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid clinical encryption key configuration");
        }
    }

    private static byte[] aad(int tenantId, String table, String field, long recordId) {
        return ("clinisis:clinical:v1:" + tenantId + ":" + table + ":" + field + ":" + recordId)
                .getBytes(StandardCharsets.UTF_8);
    }

    private static void validateContext(int tenantId, String table, String field, long recordId) {
        if (tenantId <= 0 || recordId <= 0 || !(table.equals("prontuario") && CHART_FIELDS.contains(field)
                || table.equals("documento_clinico") && field.equals("texto"))) {
            throw new IllegalArgumentException("Invalid clinical encryption context");
        }
    }
}
