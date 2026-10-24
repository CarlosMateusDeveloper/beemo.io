package br.com.clinica.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProntuarioConteudoCifradoContractTest {
    @Test void usesAesGcmAndTenantBoundAad() throws Exception {
        String source = Files.readString(Path.of("src/main/java/br/com/clinica/service/ProntuarioConteudoCifradoService.java"));
        String migration = Files.readString(Path.of("../database/migrations/031_prontuario_conteudo_cifrado.sql"));
        assertTrue(source.contains("AES/GCM/NoPadding"));
        assertTrue(source.contains("clinisis:prontuario:"));
        assertTrue(source.contains("versao_chave"));
        assertTrue(migration.contains("FORCE ROW LEVEL SECURITY"));
        assertTrue(migration.contains("nonce BYTEA"));
    }
}
