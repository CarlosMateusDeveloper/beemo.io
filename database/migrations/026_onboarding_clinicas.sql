-- Issue #25: onboarding guiado, configuração inicial e rollback auditável de importações.
-- Aplicar depois de 025_automacao_eventos_clinicos.sql.
BEGIN;

CREATE TABLE IF NOT EXISTS onboarding_clinica (
    id_clinica INTEGER PRIMARY KEY
        DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer
        REFERENCES clinica(id_clinica),
    status VARCHAR(20) NOT NULL DEFAULT 'EM_ANDAMENTO'
        CHECK (status IN ('EM_ANDAMENTO','CONCLUIDO')),
    id_responsavel INTEGER,
    fuso_horario VARCHAR(80) NOT NULL DEFAULT 'America/Fortaleza',
    agenda_inicio TIME,
    agenda_fim TIME,
    dias_atendimento JSONB NOT NULL DEFAULT '[]'::jsonb
        CHECK (jsonb_typeof(dias_atendimento) = 'array'),
    canais JSONB NOT NULL DEFAULT '[]'::jsonb
        CHECK (jsonb_typeof(canais) = 'array'),
    politica_consentimento_confirmada BOOLEAN NOT NULL DEFAULT false,
    primeiro_fluxo VARCHAR(40)
        CHECK (primeiro_fluxo IS NULL OR primeiro_fluxo IN ('faltas','pos_atendimento','retorno_preventivo')),
    versao INTEGER NOT NULL DEFAULT 0,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    concluido_em TIMESTAMPTZ,
    FOREIGN KEY(id_clinica,id_responsavel) REFERENCES tenant_membro(id_clinica,id_usuario)
);

CREATE TABLE IF NOT EXISTS onboarding_evento (
    id_evento BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_clinica INTEGER NOT NULL
        DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer
        REFERENCES clinica(id_clinica),
    id_usuario INTEGER NOT NULL,
    tipo VARCHAR(40) NOT NULL CHECK (tipo IN ('CONFIGURACAO_ATUALIZADA','CONCLUIDO','REABERTO')),
    detalhes JSONB NOT NULL DEFAULT '{}'::jsonb,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(id_clinica,id_evento),
    FOREIGN KEY(id_clinica,id_usuario) REFERENCES tenant_membro(id_clinica,id_usuario)
);

ALTER TABLE integracao_importacao ADD COLUMN IF NOT EXISTS rollback_status VARCHAR(30)
    CHECK (rollback_status IS NULL OR rollback_status IN ('CONCLUIDO','CORRECAO_ASSISTIDA'));
ALTER TABLE integracao_importacao ADD COLUMN IF NOT EXISTS rollback_relatorio JSONB;
ALTER TABLE integracao_importacao ADD COLUMN IF NOT EXISTS desfeita_em TIMESTAMPTZ;
ALTER TABLE integracao_importacao ADD COLUMN IF NOT EXISTS desfeita_por INTEGER;
ALTER TABLE integracao_importacao ADD COLUMN IF NOT EXISTS arquivo_conteudo_criptografado BYTEA;
ALTER TABLE integracao_importacao ADD COLUMN IF NOT EXISTS arquivo_nonce BYTEA;
ALTER TABLE integracao_importacao ADD COLUMN IF NOT EXISTS arquivo_tamanho INTEGER;
ALTER TABLE integracao_importacao ADD COLUMN IF NOT EXISTS arquivo_chave_versao SMALLINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid='integracao_importacao'::regclass
          AND conname='integracao_importacao_desfeita_por_fk'
    ) THEN
        ALTER TABLE integracao_importacao
            ADD CONSTRAINT integracao_importacao_desfeita_por_fk
            FOREIGN KEY(id_clinica,desfeita_por) REFERENCES tenant_membro(id_clinica,id_usuario);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid='integracao_importacao'::regclass
          AND conname='integracao_importacao_arquivo_protegido_check'
    ) THEN
        ALTER TABLE integracao_importacao
            ADD CONSTRAINT integracao_importacao_arquivo_protegido_check CHECK (
                (arquivo_conteudo_criptografado IS NULL AND arquivo_nonce IS NULL
                    AND arquivo_tamanho IS NULL AND arquivo_chave_versao IS NULL)
                OR
                (arquivo_conteudo_criptografado IS NOT NULL AND octet_length(arquivo_nonce)=12
                    AND arquivo_tamanho BETWEEN 1 AND 5242880 AND arquivo_chave_versao=1)
            );
    END IF;
END $$;

CREATE OR REPLACE FUNCTION onboarding_versionar() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    NEW.versao := OLD.versao + 1;
    NEW.atualizado_em := now();
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_onboarding_clinica_versionar ON onboarding_clinica;
CREATE TRIGGER trg_onboarding_clinica_versionar
BEFORE UPDATE ON onboarding_clinica
FOR EACH ROW EXECUTE FUNCTION onboarding_versionar();

DO $rls$
DECLARE tabela TEXT;
BEGIN
    FOREACH tabela IN ARRAY ARRAY['onboarding_clinica','onboarding_evento'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', tabela);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', tabela);
        IF NOT EXISTS (
            SELECT 1 FROM pg_policies
            WHERE schemaname=current_schema() AND tablename=tabela AND policyname='tenant_isolation'
        ) THEN
            EXECUTE format('CREATE POLICY tenant_isolation ON %I USING(id_clinica=nullif(current_setting(''app.tenant_id'',true),'''')::integer) WITH CHECK(id_clinica=nullif(current_setting(''app.tenant_id'',true),'''')::integer)', tabela);
        END IF;
    END LOOP;
END $rls$;

INSERT INTO auth_schema_version(version) VALUES (26) ON CONFLICT DO NOTHING;
COMMIT;
