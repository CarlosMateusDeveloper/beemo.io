package br.com.clinica.service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class ImportFileProtectionServiceTest {
    private static final String KEY = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef"
            .getBytes(StandardCharsets.UTF_8));

    @Test void protegeComAesGcmEVinculaAoTenantEHash() {
        var service = new ImportFileProtectionService(KEY, new SecureRandom());
        byte[] original = "paciente_id;nome\n1;Paciente fictício".getBytes(StandardCharsets.UTF_8);
        var protectedFile = service.protect(original, 7, "a".repeat(64));

        assertFalse(java.util.Arrays.equals(original, protectedFile.ciphertext()));
        assertEquals(12, protectedFile.nonce().length);
        assertEquals(original.length, protectedFile.originalSize());
        assertArrayEquals(original, service.unprotect(protectedFile, 7, "a".repeat(64)));
        assertThrows(IllegalStateException.class,
                () -> service.unprotect(protectedFile, 8, "a".repeat(64)));
    }

    @Test void recusaImportacaoSemChave() {
        var service = new ImportFileProtectionService("", new SecureRandom());
        assertThrows(ResponseStatusException.class,
                () -> service.protect(new byte[]{1}, 1, "b".repeat(64)));
    }

    @Test void recusaChaveComTamanhoInvalido() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        assertThrows(IllegalStateException.class,
                () -> new ImportFileProtectionService(shortKey, new SecureRandom()));
    }
}
