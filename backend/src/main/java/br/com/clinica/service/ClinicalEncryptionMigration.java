package br.com.clinica.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/** Opt-in, restartable migration. Run without a web listener and with old writers stopped. */
@Component
@ConditionalOnProperty(name = "app.clinical-encryption.migrate", havingValue = "true")
public class ClinicalEncryptionMigration implements ApplicationRunner {
    private static final int BATCH_SIZE = 50;
    private static final String[] CHART_FIELDS = {
            "queixa_principal", "historia_doenca_atual", "descricao", "exame_fisico",
            "hipotese_diagnostica", "diagnostico", "prescricao", "plano_terapeutico", "conduta"
    };
    private final JdbcTemplate db;
    private final TransactionTemplate transaction;
    private final ClinicalTextCipher cipher;
    private final ProntuarioConteudoCifradoService legacy;
    private final boolean rotate;

    public ClinicalEncryptionMigration(JdbcTemplate db, PlatformTransactionManager manager, ClinicalTextCipher cipher,
            ProntuarioConteudoCifradoService legacy,
            @Value("${app.clinical-encryption.rotate:false}") boolean rotate) {
        this.db = db;
        this.transaction = new TransactionTemplate(manager);
        this.cipher = cipher;
        this.legacy = legacy;
        this.rotate = rotate;
    }

    @Override
    public void run(ApplicationArguments args) {
        cipher.requireActiveKey();
        List<Integer> tenants = db.queryForList("SELECT id_clinica FROM clinica ORDER BY id_clinica", Integer.class);
        for (int tenant : tenants) {
            TenantContext.set(new TenantService.Access(tenant, "Migração clínica", "administrador"));
            try {
                migrateTable(tenant, "prontuario");
                migrateTable(tenant, "documento_clinico");
            } finally {
                TenantContext.clear();
            }
        }
    }

    private void migrateTable(int tenant, String table) {
        int batches = 0;
        while (true) {
            int changed = transaction.execute(status -> {
                db.queryForObject("SELECT set_config('app.tenant_id', ?, true)", String.class, Integer.toString(tenant));
                return table.equals("prontuario") ? migrateCharts(tenant) : migrateDocuments(tenant);
            });
            if (changed == 0) break;
            batches++;
            // Progress is counts only. Never log clinical values, nonces or keys.
            System.out.printf("clinical-encryption tenant=%d table=%s batch=%d rows=%d%n", tenant, table, batches, changed);
        }
        long remaining = transaction.execute(status -> {
            db.queryForObject("SELECT set_config('app.tenant_id', ?, true)", String.class, Integer.toString(tenant));
            String condition = rotate ? "NOT LIKE 'enc:" + cipher.activeVersion() + ":%'" : "NOT LIKE 'enc:%'";
            if (table.equals("documento_clinico")) {
                return db.queryForObject("SELECT count(*) FROM documento_clinico WHERE texto IS NOT NULL AND texto "
                        + condition, Long.class);
            }
            StringBuilder predicate = new StringBuilder();
            for (String field : CHART_FIELDS) {
                if (!predicate.isEmpty()) predicate.append(" OR ");
                predicate.append('(').append(field).append(" IS NOT NULL AND ").append(field).append(' ').append(condition).append(')');
            }
            predicate.append(" OR (descricao IS NULL AND EXISTS (SELECT 1 FROM prontuario_conteudo_cifrado c"
                    + " WHERE c.id_prontuario = prontuario.id_prontuario))");
            return db.queryForObject("SELECT count(*) FROM prontuario WHERE " + predicate, Long.class);
        });
        if (remaining != 0) throw new IllegalStateException("Clinical encryption migration incomplete");
    }

    private int migrateCharts(int tenant) {
        StringBuilder where = new StringBuilder();
        StringBuilder select = new StringBuilder("SELECT id_prontuario");
        for (String field : CHART_FIELDS) {
            select.append(", ").append(field);
            if (!where.isEmpty()) where.append(" OR ");
            where.append('(').append(field).append(" IS NOT NULL AND ").append(field)
                    .append(rotate ? " NOT LIKE 'enc:" + cipher.activeVersion() + ":%'" : " NOT LIKE 'enc:%'")
                    .append(')');
        }
        where.append(" OR (descricao IS NULL AND EXISTS (SELECT 1 FROM prontuario_conteudo_cifrado c"
                + " WHERE c.id_prontuario = prontuario.id_prontuario))");
        List<ChartRow> rows = db.query(select + " FROM prontuario WHERE " + where
                + " ORDER BY id_prontuario LIMIT " + BATCH_SIZE + " FOR UPDATE SKIP LOCKED", (rs, n) -> {
            String[] values = new String[CHART_FIELDS.length];
            for (int i = 0; i < values.length; i++) values[i] = rs.getString(i + 2);
            return new ChartRow(rs.getLong(1), values);
        });
        int fields = 0;
        for (ChartRow row : rows) {
            String[] values = row.values();
            if (values[2] == null) {
                var earlier = legacy.load(Math.toIntExact(row.id()));
                if (earlier != null) {
                    values = earlier.values();
                    for (int required : new int[]{2, 5, 6}) {
                        if (values[required] == null) values[required] = "";
                    }
                }
            }
            for (int i = 0; i < CHART_FIELDS.length; i++) {
                String value = values[i];
                if (value == null || (!rotate && cipher.isEncrypted(value))
                        || (rotate && value.startsWith("enc:" + cipher.activeVersion() + ":"))) continue;
                String plain = cipher.isEncrypted(value)
                        ? cipher.decrypt(value, tenant, "prontuario", CHART_FIELDS[i], row.id()) : value;
                String encrypted = cipher.encrypt(plain, tenant, "prontuario", CHART_FIELDS[i], row.id());
                verify(plain, encrypted, tenant, "prontuario", CHART_FIELDS[i], row.id());
                db.update("UPDATE prontuario SET " + CHART_FIELDS[i] + " = ? WHERE id_prontuario = ?", encrypted, row.id());
                fields++;
            }
        }
        recordProgress(tenant, "prontuario", rows.size(), fields);
        return rows.size();
    }

    private int migrateDocuments(int tenant) {
        String pending = rotate ? "enc:" + cipher.activeVersion() + ":%" : "enc:%";
        List<DocumentRow> rows = db.query("SELECT id_documento_clinico, texto FROM documento_clinico "
                + "WHERE texto IS NOT NULL AND texto NOT LIKE '" + pending + "' "
                + "ORDER BY id_documento_clinico LIMIT " + BATCH_SIZE + " FOR UPDATE SKIP LOCKED",
                (rs, n) -> new DocumentRow(rs.getLong(1), rs.getString(2)));
        for (DocumentRow row : rows) {
            String plain = cipher.isEncrypted(row.text())
                    ? cipher.decrypt(row.text(), tenant, "documento_clinico", "texto", row.id()) : row.text();
            String encrypted = cipher.encrypt(plain, tenant, "documento_clinico", "texto", row.id());
            verify(plain, encrypted, tenant, "documento_clinico", "texto", row.id());
            db.update("UPDATE documento_clinico SET texto = ? WHERE id_documento_clinico = ?", encrypted, row.id());
        }
        recordProgress(tenant, "documento_clinico", rows.size(), rows.size());
        return rows.size();
    }

    private void verify(String original, String encrypted, int tenant, String table, String field, long id) {
        if (!original.equals(cipher.decrypt(encrypted, tenant, table, field, id))) {
            throw new IllegalStateException("Clinical migration verification failed");
        }
    }

    private void recordProgress(int tenant, String table, int rows, int fields) {
        if (rows == 0) return;
        db.update("""
                INSERT INTO clinical_encryption_migration(id_clinica,tabela,registros,campos)
                VALUES (?,?,?,?) ON CONFLICT (id_clinica,tabela) DO UPDATE
                SET registros=clinical_encryption_migration.registros+EXCLUDED.registros,
                    campos=clinical_encryption_migration.campos+EXCLUDED.campos, atualizado_em=now()
                """, tenant, table, rows, fields);
    }

    private record ChartRow(long id, String[] values) {}
    private record DocumentRow(long id, String text) {}
}
