package br.com.clinica.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ProductionSecurityValidatorTest {
    @Test void acceptsCompleteProductionConfiguration() {
        assertDoesNotThrow(() -> ProductionSecurityValidator.validate(valid()));
    }
    @Test void rejectsLocalOriginAndMissingEncryptionKey() {
        var local = valid().withProperty("APP_FRONTEND_URL", "http://localhost:5173");
        assertThrows(IllegalStateException.class, () -> ProductionSecurityValidator.validate(local));
        var missingKey = valid().withProperty("EXPORT_ENCRYPTION_KEY", "");
        assertThrows(IllegalStateException.class, () -> ProductionSecurityValidator.validate(missingKey));
    }
    @Test void rejectsWeakJwtAndUnsafeCookie() {
        assertThrows(IllegalStateException.class, () -> ProductionSecurityValidator.validate(valid().withProperty("JWT_SECRET", "short")));
        assertThrows(IllegalStateException.class, () -> ProductionSecurityValidator.validate(valid().withProperty("app.auth.secure-cookies", "false")));
    }
    private MockEnvironment valid() {
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        return new MockEnvironment()
                .withProperty("DB_HOST", "postgres").withProperty("DB_NAME", "clinisis")
                .withProperty("DB_USER", "clinisis_app").withProperty("DB_PASSWORD", "correct-horse-battery-staple")
                .withProperty("JWT_SECRET", "0123456789abcdef0123456789abcdef")
                .withProperty("EXPORT_ENCRYPTION_KEY", key).withProperty("RESEND_API_KEY", "re_123")
                .withProperty("DATA_ENCRYPTION_KEY", key)
                .withProperty("RESEND_FROM", "Clinisis <acesso@clinisis.com.br>")
                .withProperty("APP_FRONTEND_URL", "https://app.clinisis.com.br")
                .withProperty("APP_API_URL", "https://api.clinisis.com.br")
                .withProperty("CORS_ALLOWED_ORIGINS", "https://app.clinisis.com.br")
                .withProperty("app.auth.secure-cookies", "true")
                .withProperty("spring.jpa.hibernate.ddl-auto", "validate");
    }
}
