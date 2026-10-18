-- Issue #35: exportacao segura, rastreavel e temporaria por clinica.
BEGIN;

CREATE TABLE IF NOT EXISTS exportacao_dados (
    id UUID PRIMARY KEY,
    id_clinica INTEGER NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer
        REFERENCES clinica(id_clinica),
    id_solicitante INTEGER NOT NULL REFERENCES usuario(id),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDENTE'
        CHECK(status IN ('PENDENTE','PROCESSANDO','PRONTA','FALHA','REVOGADA','EXPIRADA')),
    progresso SMALLINT NOT NULL DEFAULT 0 CHECK(progresso BETWEEN 0 AND 100),
    escopo VARCHAR(30) NOT NULL DEFAULT 'DADOS_CLINICA' CHECK(escopo='DADOS_CLINICA'),
    token_hash BYTEA NOT NULL,
    token_expira_em TIMESTAMPTZ NOT NULL,
    arquivo_criptografado BYTEA,
    arquivo_nonce BYTEA,
    arquivo_sha256 BYTEA,
    tamanho_bytes BIGINT,
    tabelas_exportadas JSONB NOT NULL DEFAULT '[]'::jsonb,
    erro VARCHAR(500),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    iniciado_em TIMESTAMPTZ,
    concluido_em TIMESTAMPTZ,
    baixado_em TIMESTAMPTZ,
    revogado_em TIMESTAMPTZ,
    UNIQUE(id_clinica,id)
);

CREATE INDEX IF NOT EXISTS idx_exportacao_dados_clinica_status
    ON exportacao_dados(id_clinica,status,criado_em DESC);

ALTER TABLE exportacao_dados ENABLE ROW LEVEL SECURITY;
ALTER TABLE exportacao_dados FORCE ROW LEVEL SECURITY;
DO $rls$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname=current_schema()
                   AND tablename='exportacao_dados' AND policyname='tenant_isolation') THEN
        CREATE POLICY tenant_isolation ON exportacao_dados
            USING(id_clinica=nullif(current_setting('app.tenant_id',true),'')::integer)
            WITH CHECK(id_clinica=nullif(current_setting('app.tenant_id',true),'')::integer);
    END IF;
END $rls$;

INSERT INTO auth_schema_version(version) VALUES (29) ON CONFLICT DO NOTHING;
COMMIT;
