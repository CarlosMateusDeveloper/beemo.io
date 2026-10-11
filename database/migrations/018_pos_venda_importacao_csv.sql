-- Issue #37: contrato normalizado para pacientes e atendimentos externos e importação CSV auditável.
-- Aplicar depois de 017_pos_venda_metricas_indices.sql.
BEGIN;

CREATE TABLE IF NOT EXISTS integracao_fonte_dados (
    id_fonte BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_clinica INTEGER NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer REFERENCES clinica(id_clinica),
    codigo VARCHAR(80) NOT NULL CHECK (codigo ~ '^[a-z0-9][a-z0-9_-]{1,79}$'),
    nome VARCHAR(160) NOT NULL,
    fuso_horario VARCHAR(80) NOT NULL,
    ultima_sincronizacao TIMESTAMPTZ,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(id_clinica,id_fonte),
    UNIQUE(id_clinica,codigo)
);

CREATE TABLE IF NOT EXISTS integracao_importacao (
    id_importacao BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_clinica INTEGER NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer REFERENCES clinica(id_clinica),
    id_fonte BIGINT NOT NULL,
    arquivo_nome VARCHAR(255) NOT NULL,
    arquivo_sha256 CHAR(64) NOT NULL,
    fuso_horario VARCHAR(80) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PROCESSANDO','CONCLUIDA','CONCLUIDA_COM_ERROS','FALHOU')),
    total_linhas INTEGER NOT NULL DEFAULT 0,
    importadas INTEGER NOT NULL DEFAULT 0,
    atualizadas INTEGER NOT NULL DEFAULT 0,
    sem_alteracao INTEGER NOT NULL DEFAULT 0,
    revisao INTEGER NOT NULL DEFAULT 0,
    erros INTEGER NOT NULL DEFAULT 0,
    criado_por INTEGER NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    concluido_em TIMESTAMPTZ,
    UNIQUE(id_clinica,id_importacao),
    FOREIGN KEY(id_clinica,id_fonte) REFERENCES integracao_fonte_dados(id_clinica,id_fonte),
    FOREIGN KEY(id_clinica,criado_por) REFERENCES tenant_membro(id_clinica,id_usuario)
);

CREATE TABLE IF NOT EXISTS integracao_paciente_externo (
    id_paciente_externo BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_clinica INTEGER NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer REFERENCES clinica(id_clinica),
    id_fonte BIGINT NOT NULL,
    identificador_externo VARCHAR(160) NOT NULL,
    nome VARCHAR(200) NOT NULL,
    telefone VARCHAR(20) NOT NULL,
    nao_contatar BOOLEAN NOT NULL DEFAULT false,
    atualizado_em_fonte TIMESTAMPTZ,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(id_clinica,id_paciente_externo),
    UNIQUE(id_clinica,id_fonte,identificador_externo),
    FOREIGN KEY(id_clinica,id_fonte) REFERENCES integracao_fonte_dados(id_clinica,id_fonte)
);

CREATE TABLE IF NOT EXISTS integracao_atendimento_externo (
    id_atendimento_externo BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_clinica INTEGER NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer REFERENCES clinica(id_clinica),
    id_fonte BIGINT NOT NULL,
    id_paciente_externo BIGINT NOT NULL,
    identificador_externo VARCHAR(160) NOT NULL,
    agendado_em TIMESTAMPTZ NOT NULL,
    fuso_horario VARCHAR(80) NOT NULL,
    profissional VARCHAR(200) NOT NULL,
    especialidade VARCHAR(160) NOT NULL,
    tipo VARCHAR(160) NOT NULL,
    situacao_origem VARCHAR(120) NOT NULL,
    situacao_normalizada VARCHAR(20) NOT NULL
        CHECK (situacao_normalizada IN ('AGENDADO','CONFIRMADO','FALTOU','REALIZADO','CANCELADO','DESCONHECIDO')),
    atualizado_em_fonte TIMESTAMPTZ,
    id_importacao BIGINT NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(id_clinica,id_atendimento_externo),
    UNIQUE(id_clinica,id_fonte,identificador_externo),
    FOREIGN KEY(id_clinica,id_fonte) REFERENCES integracao_fonte_dados(id_clinica,id_fonte),
    FOREIGN KEY(id_clinica,id_paciente_externo) REFERENCES integracao_paciente_externo(id_clinica,id_paciente_externo),
    FOREIGN KEY(id_clinica,id_importacao) REFERENCES integracao_importacao(id_clinica,id_importacao)
);

CREATE TABLE IF NOT EXISTS integracao_importacao_linha (
    id_linha BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_clinica INTEGER NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer REFERENCES clinica(id_clinica),
    id_importacao BIGINT NOT NULL,
    numero_linha INTEGER NOT NULL CHECK (numero_linha >= 2),
    paciente_externo VARCHAR(160),
    atendimento_externo VARCHAR(160),
    resultado VARCHAR(20) NOT NULL CHECK (resultado IN ('IMPORTADA','ATUALIZADA','SEM_ALTERACAO','REVISAO','ERRO')),
    codigo VARCHAR(80),
    campo VARCHAR(80),
    mensagem VARCHAR(1000),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(id_clinica,id_importacao,numero_linha),
    FOREIGN KEY(id_clinica,id_importacao) REFERENCES integracao_importacao(id_clinica,id_importacao) ON DELETE CASCADE
);

ALTER TABLE pos_venda_caso ALTER COLUMN id_consulta_origem DROP NOT NULL;
ALTER TABLE pos_venda_caso ADD COLUMN IF NOT EXISTS id_atendimento_externo_origem BIGINT;
ALTER TABLE pos_venda_caso ADD COLUMN IF NOT EXISTS origem VARCHAR(20) NOT NULL DEFAULT 'agenda_interna'
    CHECK (origem IN ('agenda_interna','importacao_csv','integracao_api'));
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'pos_venda_caso'::regclass
          AND conname = 'pos_venda_caso_origem_check'
    ) THEN
        ALTER TABLE pos_venda_caso ADD CONSTRAINT pos_venda_caso_origem_check CHECK (
            (id_consulta_origem IS NOT NULL AND id_atendimento_externo_origem IS NULL)
            OR (id_consulta_origem IS NULL AND id_atendimento_externo_origem IS NOT NULL)
        );
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'pos_venda_caso'::regclass
          AND conname = 'pos_venda_caso_atendimento_externo_fk'
    ) THEN
        ALTER TABLE pos_venda_caso ADD CONSTRAINT pos_venda_caso_atendimento_externo_fk
            FOREIGN KEY(id_clinica,id_atendimento_externo_origem)
            REFERENCES integracao_atendimento_externo(id_clinica,id_atendimento_externo);
    END IF;
END $$;
CREATE UNIQUE INDEX IF NOT EXISTS pos_venda_caso_atendimento_externo_uk
    ON pos_venda_caso(id_clinica,id_atendimento_externo_origem)
    WHERE id_atendimento_externo_origem IS NOT NULL;

CREATE INDEX IF NOT EXISTS integracao_importacao_fonte_idx
    ON integracao_importacao(id_clinica,id_fonte,criado_em DESC);
CREATE INDEX IF NOT EXISTS integracao_importacao_linha_resultado_idx
    ON integracao_importacao_linha(id_clinica,id_importacao,resultado,numero_linha);
CREATE INDEX IF NOT EXISTS integracao_atendimento_periodo_idx
    ON integracao_atendimento_externo(id_clinica,id_fonte,agendado_em);

CREATE OR REPLACE FUNCTION pos_venda_acompanhar_atendimento_externo() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    caso pos_venda_caso%ROWTYPE;
    novo_id BIGINT;
BEGIN
    IF TG_OP = 'UPDATE' AND NEW.situacao_normalizada IS NOT DISTINCT FROM OLD.situacao_normalizada THEN
        RETURN NEW;
    END IF;

    SELECT pv.* INTO caso FROM pos_venda_caso pv
    WHERE pv.id_atendimento_externo_origem = NEW.id_atendimento_externo FOR UPDATE;

    IF NOT FOUND THEN
        IF NEW.situacao_normalizada = 'FALTOU' THEN
            INSERT INTO pos_venda_caso(
                id_consulta_origem,id_atendimento_externo_origem,data_falta,status,origem
            ) VALUES (
                NULL,NEW.id_atendimento_externo,
                NEW.agendado_em AT TIME ZONE NEW.fuso_horario,
                CASE WHEN EXISTS (
                    SELECT 1 FROM integracao_paciente_externo p
                    WHERE p.id_paciente_externo=NEW.id_paciente_externo AND p.nao_contatar
                ) THEN 'nao_contatar' ELSE 'pendente' END,
                'importacao_csv'
            ) RETURNING id INTO novo_id;
            INSERT INTO pos_venda_evento(id_caso,tipo,descricao)
            VALUES (novo_id,'falta','Falta importada da fonte externa.');
        END IF;
        RETURN NEW;
    END IF;

    INSERT INTO pos_venda_evento(id_caso,tipo,descricao)
    VALUES (caso.id,'importacao',
        'Atendimento externo atualizado para ' || NEW.situacao_normalizada || '.');

    IF caso.status IN ('nao_deseja','nao_contatar','recuperado') THEN RETURN NEW; END IF;
    UPDATE pos_venda_caso SET
        status = CASE WHEN NEW.situacao_normalizada='FALTOU' THEN 'pendente' ELSE 'registro_corrigido' END,
        proxima_acao = CASE WHEN NEW.situacao_normalizada='FALTOU' THEN proxima_acao ELSE NULL END
    WHERE id=caso.id;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_pos_venda_atendimento_externo ON integracao_atendimento_externo;
CREATE TRIGGER trg_pos_venda_atendimento_externo
AFTER INSERT OR UPDATE OF situacao_normalizada ON integracao_atendimento_externo
FOR EACH ROW EXECUTE FUNCTION pos_venda_acompanhar_atendimento_externo();

DO $rls$
DECLARE tabela TEXT;
BEGIN
    FOREACH tabela IN ARRAY ARRAY[
        'integracao_fonte_dados','integracao_importacao','integracao_paciente_externo',
        'integracao_atendimento_externo','integracao_importacao_linha'
    ] LOOP
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

INSERT INTO auth_schema_version(version) VALUES (18) ON CONFLICT DO NOTHING;
COMMIT;
