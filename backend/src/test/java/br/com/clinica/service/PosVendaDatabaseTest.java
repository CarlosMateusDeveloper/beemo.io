package br.com.clinica.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

/** PostgreSQL real: migration idempotente e isolamento RLS entre duas clínicas. */
@EnabledIfEnvironmentVariable(named = "DASHBOARD_TEST_DB_URL", matches = ".+")
class PosVendaDatabaseTest {
    static Connection connection;
    static String schema;

    @BeforeAll static void setup() throws Exception {
        connection = DriverManager.getConnection(
                System.getenv("DASHBOARD_TEST_DB_URL"),
                System.getenv("DASHBOARD_TEST_DB_USER"),
                System.getenv().getOrDefault("DASHBOARD_TEST_DB_PASSWORD", ""));
        try (var statement = connection.createStatement(); var result = statement.executeQuery(
                "SELECT rolsuper OR rolbypassrls FROM pg_roles WHERE rolname=current_user")) {
            assertTrue(result.next());
            assertFalse(result.getBoolean(1), "Use uma role de teste sem SUPERUSER/BYPASSRLS.");
        }
        schema = "pos_venda_test_" + UUID.randomUUID().toString().replace("-", "");
        sql("CREATE SCHEMA " + schema);
        sql("SET search_path TO " + schema);
        sql("""
                CREATE TABLE clinica(id_clinica integer PRIMARY KEY);
                CREATE TABLE tenant_membro(id_clinica integer NOT NULL,id_usuario integer NOT NULL,ativo boolean NOT NULL DEFAULT true,
                    PRIMARY KEY(id_clinica,id_usuario));
                CREATE TABLE paciente(id_paciente integer PRIMARY KEY,id_clinica integer NOT NULL,
                    UNIQUE(id_clinica,id_paciente));
                CREATE TABLE auth_schema_version(version integer PRIMARY KEY);
                INSERT INTO clinica VALUES (1),(2);
                INSERT INTO tenant_membro(id_clinica,id_usuario) VALUES (1,10),(2,20);
                INSERT INTO paciente VALUES (100,1),(200,2);
                """);
        String migration = Files.readString(Path.of("..","database","migrations","016_pos_venda_operacao.sql"));
        sql(migration);
        sql(migration);
    }

    @AfterAll static void cleanup() throws Exception {
        if (connection == null) return;
        try {
            if (schema != null) {
                sql("SET search_path TO public");
                sql("DROP SCHEMA " + schema + " CASCADE");
            }
        } finally {
            connection.close();
        }
    }

    @Test void migrationEhIdempotenteERegistraVersaoUmaVez() throws Exception {
        tenant(1);
        assertEquals(1, scalar("SELECT count(*) FROM auth_schema_version WHERE version=16"));
        assertEquals(7, scalar("SELECT count(*) FROM pg_tables WHERE schemaname=current_schema() AND rowsecurity"));
    }

    @Test void tenantNaoLeNemGravaDadosDaOutraClinica() throws Exception {
        tenant(1);
        sql("INSERT INTO pos_venda_tarefa(id_paciente,titulo,id_responsavel,prioridade,prazo,origem,criado_por,atualizado_por) "
                + "VALUES (100,'Retorno A',10,'alta',now(),'consulta',10,10)");
        assertEquals(1, scalar("SELECT count(*) FROM pos_venda_tarefa"));

        tenant(2);
        assertEquals(0, scalar("SELECT count(*) FROM pos_venda_tarefa"));
        sql("INSERT INTO pos_venda_tarefa(id_paciente,titulo,id_responsavel,prioridade,prazo,origem,criado_por,atualizado_por) "
                + "VALUES (200,'Retorno B',20,'media',now(),'consulta',20,20)");
        assertEquals(1, scalar("SELECT count(*) FROM pos_venda_tarefa"));

        tenant(1);
        assertEquals(1, scalar("SELECT count(*) FROM pos_venda_tarefa"));
        assertEquals(0, scalar("SELECT count(*) FROM pos_venda_tarefa WHERE titulo='Retorno B'"));
    }

    static void tenant(int id) throws Exception { sql("SET app.tenant_id TO '" + id + "'"); }
    static long scalar(String query) throws Exception {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(query)) {
            assertTrue(result.next()); return result.getLong(1);
        }
    }
    static void sql(String value) throws Exception {
        try (var statement = connection.createStatement()) { statement.execute(value); }
    }
}
