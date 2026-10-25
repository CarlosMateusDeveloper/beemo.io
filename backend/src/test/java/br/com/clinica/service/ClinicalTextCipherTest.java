package br.com.clinica.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class ClinicalTextCipherTest {
    private static String key(int seed) {
        byte[] bytes = new byte[32];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) (seed + i);
        return Base64.getEncoder().encodeToString(bytes);
    }

    @Test void roundTripDoesNotExposePlaintextOrReuseNonce() {
        var cipher = new ClinicalTextCipher("v1", key(1), "", false);
        String plain = "Evolução: dor há três dias; ação clínica realizada.";
        String first = cipher.encrypt(plain, 1, "prontuario", "descricao", 42);
        String second = cipher.encrypt(plain, 1, "prontuario", "descricao", 42);
        assertTrue(first.startsWith("enc:v1:"));
        assertFalse(first.contains(plain));
        assertNotEquals(first, second);
        assertEquals(plain, cipher.decrypt(first, 1, "prontuario", "descricao", 42));
        assertEquals(plain, cipher.decrypt(second, 1, "prontuario", "descricao", 42));
    }

    @Test void differentTenantFieldTableOrRecordCannotRead() {
        var cipher = new ClinicalTextCipher("v1", key(2), "", false);
        String encrypted = cipher.encrypt("sigilo", 1, "prontuario", "descricao", 42);
        assertThrows(IllegalStateException.class, () -> cipher.decrypt(encrypted, 2, "prontuario", "descricao", 42));
        assertThrows(IllegalStateException.class, () -> cipher.decrypt(encrypted, 1, "prontuario", "diagnostico", 42));
        assertThrows(IllegalStateException.class, () -> cipher.decrypt(encrypted, 1, "prontuario", "descricao", 43));
        String document = cipher.encrypt("sigilo", 1, "documento_clinico", "texto", 42);
        assertThrows(IllegalStateException.class, () -> cipher.decrypt(document, 1, "prontuario", "descricao", 42));
    }

    @Test void tamperingWithCiphertextOrNonceFailsClosed() {
        var cipher = new ClinicalTextCipher("v1", key(3), "", false);
        String encrypted = cipher.encrypt("dado clínico", 1, "documento_clinico", "texto", 9);
        String[] parts = encrypted.split(":", 4);
        byte[] payload = Base64.getUrlDecoder().decode(parts[3]);
        payload[0] ^= 1;
        String tampered = parts[0] + ":" + parts[1] + ":" + parts[2] + ":"
                + Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
        assertThrows(IllegalStateException.class, () -> cipher.decrypt(tampered, 1, "documento_clinico", "texto", 9));
        byte[] nonce = Base64.getUrlDecoder().decode(parts[2]);
        nonce[0] ^= 1;
        String tamperedNonce = parts[0] + ":" + parts[1] + ":"
                + Base64.getUrlEncoder().withoutPadding().encodeToString(nonce) + ":" + parts[3];
        assertThrows(IllegalStateException.class, () -> cipher.decrypt(tamperedNonce, 1, "documento_clinico", "texto", 9));
    }

    @Test void rotationReadsOldVersionAndWritesNewVersion() {
        var old = new ClinicalTextCipher("v1", key(4), "", false);
        String original = old.encrypt("história", 1, "prontuario", "historia_doenca_atual", 10);
        var rotated = new ClinicalTextCipher("v2", key(5), "v1:" + key(4), false);
        assertEquals("história", rotated.decrypt(original, 1, "prontuario", "historia_doenca_atual", 10));
        String replacement = rotated.encrypt("história", 1, "prontuario", "historia_doenca_atual", 10);
        assertTrue(replacement.startsWith("enc:v2:"));
        assertEquals("história", rotated.decrypt(replacement, 1, "prontuario", "historia_doenca_atual", 10));
        assertThrows(IllegalStateException.class, () -> old.decrypt(replacement, 1, "prontuario", "historia_doenca_atual", 10));
    }

    @Test void legacyReadRequiresExplicitMigrationModeAndMissingKeyFails() {
        var strict = new ClinicalTextCipher("v1", key(6), "", false);
        assertThrows(IllegalStateException.class, () -> strict.decrypt("texto legado", 1, "prontuario", "descricao", 1));
        var migration = new ClinicalTextCipher("v1", key(6), "", true);
        assertEquals("texto legado", migration.decrypt("texto legado", 1, "prontuario", "descricao", 1));
        var unconfigured = new ClinicalTextCipher("v1", "", "", false);
        assertThrows(IllegalStateException.class, () -> unconfigured.encrypt("x", 1, "prontuario", "descricao", 1));
        assertThrows(IllegalStateException.class, unconfigured::requireStrictProductionMode);
        assertThrows(IllegalStateException.class, migration::requireStrictProductionMode);
        assertDoesNotThrow(strict::requireStrictProductionMode);
        assertThrows(IllegalArgumentException.class, () -> new ClinicalTextCipher("v1", Base64.getEncoder()
                .encodeToString("too short".getBytes(StandardCharsets.UTF_8)), "", false));
    }
}
