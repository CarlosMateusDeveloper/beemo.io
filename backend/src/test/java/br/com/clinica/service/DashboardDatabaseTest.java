package br.com.clinica.service;

import br.com.clinica.dto.DashboardRequest;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.*;
import java.util.UUID;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

/** PostgreSQL real, schema descartável, role sem SUPERUSER/BYPASSRLS. */
@EnabledIfEnvironmentVariable(named = "DASHBOARD_TEST_DB_URL", matches = ".+")
class DashboardDatabaseTest {
    static Connection connection;
    static SessionFactory factory;
    static Session session;
    static String schema;
    DashboardService service;
    @BeforeAll static void setup() throws Exception {
        String url = System.getenv("DASHBOARD_TEST_DB_URL");
        String user = System.getenv("DASHBOARD_TEST_DB_USER");
        String password = System.getenv().getOrDefault("DASHBOARD_TEST_DB_PASSWORD", "");
        connection = DriverManager.getConnection(url, user, password);
        try (var statement = connection.createStatement(); var result = statement.executeQuery(
            "SELECT rolsuper OR rolbypassrls FROM pg_roles WHERE rolname = current_user")) {
            assertTrue(result.next()); assertFalse(result.getBoolean(1), "Use uma role de teste sem SUPERUSER/BYPASSRLS.");
        }
        schema = "dashboard_test_" + UUID.randomUUID().toString().replace("-", "");
        sql("CREATE SCHEMA " + schema);
        sql("SET search_path TO " + schema);
        sql("""
            CREATE TABLE especialidade(id_especialidade int PRIMARY KEY, nome text);
            CREATE TABLE medico(id_medico int PRIMARY KEY, nome text, id_especialidade int, id_clinica int);
            CREATE TABLE paciente(id_paciente int PRIMARY KEY, nome text, id_convenio int, id_clinica int);
            CREATE TABLE agenda(id_agenda int PRIMARY KEY, id_medico int, data_slot date, hora_slot time, situacao text, id_clinica int);
            CREATE TABLE consulta(id_consulta int PRIMARY KEY, id_agenda int UNIQUE, id_paciente int, status_consulta text, tipo text, id_clinica int);
            CREATE TABLE fatura(id_fatura int PRIMARY KEY, id_consulta int UNIQUE, valor numeric(12,2), id_clinica int);
            INSERT INTO especialidade VALUES (1, 'Clínica geral');
            INSERT INTO medico VALUES (1, 'Médico A', 1, 1), (2, 'Médico B', 1, 1), (20, 'Outro tenant', 1, 2), (40, 'Sem consultas', 1, 4);
            INSERT INTO paciente VALUES (1,'Paciente A',NULL,1),(2,'Paciente B',10,1),(3,'Paciente C',10,1),(20,'Outro tenant',NULL,2);
            INSERT INTO agenda VALUES
                (1,1,'2026-09-01','09:00','Ocupado',1),
                (2,1,'2026-10-01','09:00','Ocupado',1),
                (3,1,'2026-10-08','09:00','Ocupado',1),
                (4,1,'2026-10-08','10:00','Ocupado',1),
                (5,2,'2026-10-08','09:00','Ocupado',1),
                (6,2,'2026-10-08','10:00','Livre',1),
                (7,1,'2026-10-09','11:00','Ocupado',1),
                (20,20,'2026-10-01','09:00','Ocupado',2),
                (40,40,'2026-10-01','09:00','Livre',4);
            INSERT INTO consulta VALUES
                (1,1,1,'Realizada','Consulta',1),(2,2,1,'Realizada','Retorno',1),
                (3,3,2,'Faltou','Consulta',1),(4,4,2,'Cancelada','Consulta',1),
                (5,5,3,'Realizada','Consulta',1),(7,7,1,'Em Espera','Retorno',1),
                (20,20,20,'Realizada','Consulta',2);
            INSERT INTO fatura VALUES (1,1,200,1),(2,2,100,1),(5,5,300,1),(20,20,99999,2);
            INSERT INTO medico SELECT n, 'Médico ' || n, 1, 1 FROM generate_series(3,10) n;
            INSERT INTO agenda SELECT 100+n,n,'2026-10-09'::date,'12:00'::time,'Ocupado',1 FROM generate_series(3,10) n;
            INSERT INTO consulta SELECT 100+n,100+n,1,'Agendada','Consulta',1 FROM generate_series(3,10) n;
            INSERT INTO fatura SELECT 100+n,100+n,1,1 FROM generate_series(3,10) n;
            """);
        for (String table : new String[]{"medico", "paciente", "agenda", "consulta", "fatura"}) {
            sql("ALTER TABLE " + table + " ENABLE ROW LEVEL SECURITY");
            sql("ALTER TABLE " + table + " FORCE ROW LEVEL SECURITY");
            sql("CREATE POLICY tenant_isolation ON " + table
                + " USING (id_clinica = nullif(current_setting('app.tenant_id', true),'')::integer)");
        }
        sql(java.nio.file.Files.readString(java.nio.file.Path.of("../database/migrations/014_dashboard_indices.sql")));
        factory = new Configuration().setProperty("hibernate.connection.url", url)
            .setProperty("hibernate.connection.username", user).setProperty("hibernate.connection.password", password)
            .setProperty("hibernate.connection.pool_size", "1")
            .setProperty("hibernate.hbm2ddl.auto", "none").setProperty("hibernate.show_sql", "false")
            .buildSessionFactory();
        session = factory.withOptions().connection(connection).openSession();
    }
    @BeforeEach void tenant() throws Exception {
        sql("SET app.tenant_id TO '1'");
        service = new DashboardService(session, Clock.fixed(Instant.parse("2026-10-09T13:00:00Z"), ZoneId.of("America/Fortaleza")));
    }
    @AfterAll static void cleanup() throws Exception {
        try {
            if (session != null) session.close();
            if (connection != null && schema != null) {
                sql("SET search_path TO public");
                sql("DROP SCHEMA " + schema + " CASCADE");
            }
        } finally {
            if (connection != null) connection.close();
            if (factory != null) factory.close();
        }
    }
    static void sql(String sql) throws Exception {
        try (var statement = connection.createStatement()) { statement.execute(sql); }
    }
    DashboardRequest intervalo(Integer medico) {
        return new DashboardRequest("Personalizado", medico, LocalDate.of(2026,10,1), LocalDate.of(2026,10,8));
    }
    @Test void agregaPeriodoInclusivoSemDuplicarPacientesENaoContaOutroTenant() throws Exception {
        connection.setReadOnly(true);
        try {
            var result = service.calcular(intervalo(null));
            assertEquals(4, result.totalConsultas());
            assertEquals(0, new BigDecimal("400").compareTo(result.faturamento()));
            assertEquals(80.0, result.ocupacao().percentual());
            assertEquals(5, result.ocupacao().totalSlots());
            assertEquals(33.3, result.noShow().percentual());
            assertEquals(2, result.novosRetornos().novos());
            assertEquals(1, result.novosRetornos().retornos());
            assertEquals(75.0, result.pagador().convenioPercentual());
            assertEquals(8, result.serieTemporal().size());
            assertEquals(0, result.serieTemporal().get(1).receita().signum());
            assertEquals(2, result.ranking().getFirst().id());
            assertEquals(50.0, result.ranking().get(1).noShowPct());
        } finally { connection.setReadOnly(false); }
    }
    @Test void filtroProfissionalAfetaTodosIndicadoresETopCincoEhLimitado() {
        var filtered = service.calcular(intervalo(1));
        assertEquals(3, filtered.totalConsultas());
        assertEquals(100.0, filtered.faturamento().doubleValue());
        assertEquals(1, filtered.ranking().size());
        var month = service.calcular(new DashboardRequest("Mês", null, null, null));
        assertEquals(5, month.ranking().size());
        assertEquals(5, month.serieTemporal().size());
        assertEquals(9, month.hoje().consultas());
        assertEquals(1, month.hoje().filaAguardando());
        assertEquals(5, month.hoje().proximas().size());
        assertEquals("11:00", month.hoje().proximas().getFirst().hora());
        assertEquals("por semana", month.serieUnidade());
    }
    @Test void tenantSemDadosRecebeZerosEHistoricoNaoAlteraBlocoHoje() throws Exception {
        sql("SET app.tenant_id TO '3'");
        var empty = service.calcular(intervalo(null));
        assertTrue(empty.empty()); assertEquals(0, empty.totalConsultas());
        assertEquals(0.0, empty.ocupacao().percentual()); assertEquals(0.0, empty.noShow().percentual());
        assertTrue(empty.ranking().isEmpty()); assertTrue(empty.hoje().proximas().isEmpty());
        sql("SET app.tenant_id TO '2'");
        var other = service.calcular(intervalo(null));
        assertEquals(1, other.totalConsultas()); assertEquals(99999.0, other.faturamento().doubleValue());
        sql("SET app.tenant_id TO '4'");
        var slots = service.calcular(intervalo(null));
        assertFalse(slots.empty(), "Não esconder a ocupação quando existem slots sem consultas.");
        assertEquals(1, slots.ocupacao().totalSlots()); assertEquals(0, slots.totalConsultas());
    }
}
