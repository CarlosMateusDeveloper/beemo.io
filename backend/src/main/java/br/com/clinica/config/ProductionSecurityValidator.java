package br.com.clinica.config;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Falha no boot quando a configuração marcada como produção é insegura ou incompleta. */
@Component
public class ProductionSecurityValidator implements ApplicationRunner {
    private static final Set<String> PLACEHOLDERS = Set.of("", "change-me", "changeme", "secret", "password", "example", "substitua");
    private final Environment env;
    public ProductionSecurityValidator(Environment env) { this.env = env; }

    @Override public void run(ApplicationArguments args) {
        if (!env.getProperty("app.production", Boolean.class, false)) return;
        validate(env);
    }

    static void validate(Environment env) {
        required(env, "DB_HOST", "DB_NAME", "DB_USER", "DB_PASSWORD", "JWT_SECRET", "EXPORT_ENCRYPTION_KEY",
                "RESEND_API_KEY", "RESEND_FROM", "APP_FRONTEND_URL", "APP_API_URL", "CORS_ALLOWED_ORIGINS");
        if (!env.getProperty("app.auth.secure-cookies", Boolean.class, false))
            fail("AUTH_SECURE_COOKIES deve ser true em produção");
        if (!"validate".equalsIgnoreCase(env.getProperty("spring.jpa.hibernate.ddl-auto", "")))
            fail("spring.jpa.hibernate.ddl-auto deve ser validate em produção");
        String dbHost = env.getProperty("DB_HOST", "");
        if (localHost(dbHost)) fail("DB_HOST não pode apontar para localhost em produção");
        strongSecret(env.getProperty("JWT_SECRET"), "JWT_SECRET");
        aes256Key(env.getProperty("EXPORT_ENCRYPTION_KEY"), "EXPORT_ENCRYPTION_KEY");
        publicHttps(env.getProperty("APP_FRONTEND_URL"), "APP_FRONTEND_URL");
        publicHttps(env.getProperty("APP_API_URL"), "APP_API_URL");
        List<String> origins = Arrays.stream(env.getProperty("CORS_ALLOWED_ORIGINS", "").split(","))
                .map(String::trim).filter(value -> !value.isEmpty()).toList();
        if (origins.isEmpty()) fail("CORS_ALLOWED_ORIGINS deve conter pelo menos uma origem HTTPS");
        origins.forEach(origin -> publicHttps(origin, "CORS_ALLOWED_ORIGINS"));
        String resendFrom = env.getProperty("RESEND_FROM", "");
        if (!resendFrom.contains("@")) fail("RESEND_FROM deve conter um remetente válido");
        oauthPair(env, "GOOGLE_CLIENT_ID", "GOOGLE_CLIENT_SECRET");
        oauthPair(env, "MICROSOFT_CLIENT_ID", "MICROSOFT_CLIENT_SECRET");
    }

    private static void required(Environment env, String... names) {
        for (String name : names) {
            String value = env.getProperty(name, "").trim();
            if (PLACEHOLDERS.contains(value.toLowerCase(Locale.ROOT)) || value.toLowerCase(Locale.ROOT).startsWith("substitua"))
                fail(name + " deve ser configurado por um segredo ou valor de produção");
        }
    }
    private static void strongSecret(String value, String name) {
        if (value == null || value.getBytes(StandardCharsets.UTF_8).length < 32 || looksPlaceholder(value))
            fail(name + " deve ter ao menos 32 bytes aleatórios");
    }
    private static void aes256Key(String value, String name) {
        try {
            if (Base64.getDecoder().decode(value).length != 32) throw new IllegalArgumentException();
        } catch (Exception exception) { fail(name + " deve ser Base64 de exatamente 32 bytes"); }
    }
    private static void publicHttps(String value, String name) {
        try {
            URI uri = URI.create(value);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || localHost(uri.getHost())
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))) throw new IllegalArgumentException();
        } catch (Exception exception) { fail(name + " deve ser uma origem HTTPS pública, sem caminho ou query"); }
    }
    private static void oauthPair(Environment env, String id, String secret) {
        boolean hasId = !env.getProperty(id, "").isBlank();
        boolean hasSecret = !env.getProperty(secret, "").isBlank();
        if (hasId != hasSecret) fail("Configure " + id + " e " + secret + " juntos");
    }
    private static boolean looksPlaceholder(String value) { return PLACEHOLDERS.contains(value.trim().toLowerCase(Locale.ROOT)); }
    private static boolean localHost(String value) { return "localhost".equalsIgnoreCase(value) || "127.0.0.1".equals(value) || "::1".equals(value); }
    private static void fail(String message) { throw new IllegalStateException("Configuração de produção inválida: " + message); }
}
