package br.com.clinica.config;

import br.com.clinica.service.ClinicalTextCipher;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClinicalEncryptionProductionSafetyTest {
    private static String key(byte fill) {
        byte[] bytes = new byte[32];
        java.util.Arrays.fill(bytes, fill);
        return Base64.getEncoder().encodeToString(bytes);
    }

    @Test void productionRequiresActiveKey() {
        var env = new MockEnvironment().withProperty("app.production", "true");
        assertThrows(IllegalStateException.class, () ->
                new ClinicalEncryptionProductionSafety(new ClinicalTextCipher("v1", "", "", false), env));
    }

    @Test void productionRejectsLegacyReadsAndSharedExportKey() {
        String clinical = key((byte) 1);
        var env = new MockEnvironment().withProperty("app.production", "true")
                .withProperty("DATA_ENCRYPTION_KEY", clinical)
                .withProperty("EXPORT_ENCRYPTION_KEY", key((byte) 2))
                .withProperty("IMPORT_FILE_ENCRYPTION_KEY", key((byte) 3));
        assertThrows(IllegalStateException.class, () ->
                new ClinicalEncryptionProductionSafety(new ClinicalTextCipher("v1", clinical, "", true), env));
        env.setProperty("EXPORT_ENCRYPTION_KEY", clinical);
        assertThrows(IllegalStateException.class, () ->
                new ClinicalEncryptionProductionSafety(new ClinicalTextCipher("v1", clinical, "", false), env));
        env.setProperty("EXPORT_ENCRYPTION_KEY", key((byte) 2));
        assertDoesNotThrow(() ->
                new ClinicalEncryptionProductionSafety(new ClinicalTextCipher("v1", clinical, "", false), env));
    }
}
