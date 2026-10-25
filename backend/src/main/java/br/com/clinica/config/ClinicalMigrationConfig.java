package br.com.clinica.config;

import br.com.clinica.service.MigratingPasswordEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@Profile("clinical-migration")
public class ClinicalMigrationConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new MigratingPasswordEncoder();
    }
}
