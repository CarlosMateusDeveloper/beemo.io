package br.com.clinica.service;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.junit.jupiter.api.Assertions.*;

class LocalAuthConfigTest {
    @TempDir Path directory;
    @Test void arquivoEnvAceitaFormatoPropertiesUtf8() throws Exception {
        Path file=directory.resolve(".env");
        Files.writeString(file,"AUTH_SETUP_TEST=Configuração local\n");
        new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withPropertyValues("spring.config.location=optional:"+file.toUri()+"[extension=.properties][encoding=utf-8]")
            .run(context -> {
                assertNull(context.getStartupFailure());
                assertEquals("Configuração local",context.getEnvironment().getProperty("AUTH_SETUP_TEST"));
            });
    }
}
