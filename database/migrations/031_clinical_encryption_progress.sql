-- Apply after 030_saas_painel_interno.sql, before running the encryption job.
-- No clinical content or key material is stored here.
BEGIN;
CREATE TABLE IF NOT EXISTS clinical_encryption_migration (
    id_clinica INTEGER NOT NULL REFERENCES clinica(id_clinica),
    tabela VARCHAR(40) NOT NULL CHECK (tabela IN ('prontuario', 'documento_clinico')),
    registros BIGINT NOT NULL DEFAULT 0,
    campos BIGINT NOT NULL DEFAULT 0,
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (id_clinica, tabela)
);
ALTER TABLE clinical_encryption_migration ENABLE ROW LEVEL SECURITY;
ALTER TABLE clinical_encryption_migration FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON clinical_encryption_migration;
CREATE POLICY tenant_isolation ON clinical_encryption_migration
    USING (id_clinica = nullif(current_setting('app.tenant_id', true), '')::integer)
    WITH CHECK (id_clinica = nullif(current_setting('app.tenant_id', true), '')::integer);
INSERT INTO auth_schema_version(version) VALUES (31) ON CONFLICT DO NOTHING;
COMMIT;
