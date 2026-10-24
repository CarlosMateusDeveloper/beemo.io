package br.com.clinica.config;

import br.com.clinica.service.ClinicalTextCipher;
import java.util.Arrays;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Production must not start without the active clinical key. */
@Component
public class ClinicalEncryptionProductionSafety {
    public ClinicalEncryptionProductionSafety(ClinicalTextCipher cipher, Environment env) {
        boolean production = env.getProperty("app.production", Boolean.class, false)
                || Arrays.stream(env.getActiveProfiles())
                        .anyMatch(profile -> profile.equals("prod") || profile.equals("production"));
        if (production) {
            cipher.requireStrictProductionMode();
            String clinicalKey = env.getProperty("DATA_ENCRYPTION_KEY", "").trim();
            if (clinicalKey.equals(env.getProperty("EXPORT_ENCRYPTION_KEY", "").trim())
                    || clinicalKey.equals(env.getProperty("IMPORT_FILE_ENCRYPTION_KEY", "").trim())) {
                throw new IllegalStateException("Clinical data key must differ from export and import keys");
            }
        }
    }
}
