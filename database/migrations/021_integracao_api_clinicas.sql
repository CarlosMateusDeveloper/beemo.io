-- Issue #36: API autenticada e auditável para integrações de sistemas clínicos.
-- Aplicar depois de 020_pos_venda_conciliacao_externa.sql.
BEGIN;

ALTER TABLE integracao_atendimento_externo ALTER COLUMN id_importacao DROP NOT NULL;
ALTER TABLE integracao_fonte_dados
    ADD COLUMN IF NOT EXISTS adaptador VARCHAR(80) NOT NULL DEFAULT 'csv',
    ADD COLUMN IF NOT EXISTS estado_conexao VARCHAR(24) NOT NULL DEFAULT 'CONFIGURADA',
    ADD COLUMN IF NOT EXISTS ultima_falha VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS ultima_falha_em TIMESTAMPTZ;

ALTER TABLE integracao_fonte_dados DROP CONSTRAINT IF EXISTS integracao_fonte_dados_estado_conexao_check;
ALTER TABLE integracao_fonte_dados ADD CONSTRAINT integracao_fonte_dados_estado_conexao_check
    CHECK(estado_conexao IN ('CONFIGURADA','ATIVA','COM_PENDENCIAS','REVOGADA'));

CREATE TABLE IF NOT EXISTS integracao_situacao_mapeamento (
    id_mapeamento BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_clinica INTEGER NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer
        REFERENCES clinica(id_clinica),
    id_fonte BIGINT NOT NULL,
    situacao_chave VARCHAR(160) NOT NULL,
    situacao_origem VARCHAR(160) NOT NULL,
    situacao_normalizada VARCHAR(20) NOT NULL CHECK(situacao_normalizada IN (
        'AGENDADO','CONFIRMADO','FALTOU','REALIZADO','CANCELADO','DESCONHECIDO'
    )),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(id_clinica,id_mapeamento),
    UNIQUE(id_clinica,id_fonte,situacao_chave),
    FOREIGN KEY(id_clinica,id_fonte) REFERENCES integracao_fonte_dados(id_clinica,id_fonte) ON DELETE CASCADE
);

-- Tabela auth_* contém somente o identificador e o hash de uma chave aleatória.
-- Ela não guarda dados de pacientes e precisa ser consultável antes de o tenant ser conhecido.
CREATE TABLE IF NOT EXISTS auth_integracao_credencial (
    id_credencial BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    identificador VARCHAR(32) NOT NULL UNIQUE,
    segredo_hash CHAR(64) NOT NULL,
    id_clinica INTEGER NOT NULL REFERENCES clinica(id_clinica),
    id_fonte BIGINT NOT NULL,
    nome VARCHAR(120) NOT NULL,
    escopos TEXT[] NOT NULL DEFAULT ARRAY['eventos:escrever','estado:ler'],
    criado_por INTEGER NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    ultimo_uso_em TIMESTAMPTZ,
    revogada_em TIMESTAMPTZ,
    expira_em TIMESTAMPTZ,
    UNIQUE(id_clinica,id_credencial),
    FOREIGN KEY(id_clinica,id_fonte) REFERENCES integracao_fonte_dados(id_clinica,id_fonte) ON DELETE CASCADE,
    FOREIGN KEY(id_clinica,criado_por) REFERENCES tenant_membro(id_clinica,id_usuario)
);
CREATE INDEX IF NOT EXISTS auth_integracao_credencial_tenant_idx
    ON auth_integracao_credencial(id_clinica,id_fonte,revogada_em,criado_em DESC);

CREATE TABLE IF NOT EXISTS integracao_evento (
    id_evento BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_clinica INTEGER NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer
        REFERENCES clinica(id_clinica),
    id_fonte BIGINT NOT NULL,
    id_credencial BIGINT,
    identificador_externo VARCHAR(160) NOT NULL,
    tipo VARCHAR(40) NOT NULL CHECK(tipo IN (
        'PACIENTE_ATUALIZADO','ATENDIMENTO_ATUALIZADO','SITUACAO_ATENDIMENTO_ATUALIZADA'
    )),
    versao INTEGER NOT NULL DEFAULT 1 CHECK(versao > 0),
    ocorrido_em TIMESTAMPTZ NOT NULL,
    atualizado_em_fonte TIMESTAMPTZ NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    payload JSONB NOT NULL,
    resultado VARCHAR(20) NOT NULL CHECK(resultado IN (
        'PROCESSANDO','PROCESSADO','SEM_ALTERACAO','REVISAO','ERRO'
    )),
    codigo VARCHAR(80),
    mensagem VARCHAR(1000),
    tentativas INTEGER NOT NULL DEFAULT 1 CHECK(tentativas > 0),
    recebido_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    processado_em TIMESTAMPTZ,
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(id_clinica,id_evento),
    UNIQUE(id_clinica,id_fonte,identificador_externo),
    FOREIGN KEY(id_clinica,id_fonte) REFERENCES integracao_fonte_dados(id_clinica,id_fonte) ON DELETE CASCADE,
    FOREIGN KEY(id_clinica,id_credencial) REFERENCES auth_integracao_credencial(id_clinica,id_credencial)
);
CREATE INDEX IF NOT EXISTS integracao_evento_estado_idx
    ON integracao_evento(id_clinica,id_fonte,resultado,recebido_em DESC);

ALTER TABLE integracao_situacao_mapeamento ENABLE ROW LEVEL SECURITY;
ALTER TABLE integracao_situacao_mapeamento FORCE ROW LEVEL SECURITY;
ALTER TABLE integracao_evento ENABLE ROW LEVEL SECURITY;
ALTER TABLE integracao_evento FORCE ROW LEVEL SECURITY;
DO $rls$
DECLARE tabela TEXT;
BEGIN
    FOREACH tabela IN ARRAY ARRAY['integracao_situacao_mapeamento','integracao_evento'] LOOP
        IF NOT EXISTS (
            SELECT 1 FROM pg_policies WHERE schemaname=current_schema()
              AND tablename=tabela AND policyname='tenant_isolation'
        ) THEN
            EXECUTE format('CREATE POLICY tenant_isolation ON %I USING(id_clinica=nullif(current_setting(''app.tenant_id'',true),'''')::integer) WITH CHECK(id_clinica=nullif(current_setting(''app.tenant_id'',true),'''')::integer)', tabela);
        END IF;
    END LOOP;
END $rls$;

-- Mantém a origem correta quando uma falta nasce pela API em vez de CSV.
CREATE OR REPLACE FUNCTION pos_venda_acompanhar_atendimento_externo() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    caso pos_venda_caso%ROWTYPE;
    novo_id BIGINT;
    novo_status TEXT;
    fonte_codigo TEXT;
BEGIN
    IF TG_OP='UPDATE'
       AND NEW.situacao_normalizada IS NOT DISTINCT FROM OLD.situacao_normalizada
       AND NEW.situacao_origem IS NOT DISTINCT FROM OLD.situacao_origem THEN
        RETURN NEW;
    END IF;

    SELECT codigo INTO fonte_codigo FROM integracao_fonte_dados WHERE id_fonte=NEW.id_fonte;
    SELECT pv.* INTO caso FROM pos_venda_caso pv
    JOIN pos_venda_atendimento_externo v ON v.id_caso=pv.id
    WHERE v.id_atendimento_externo=NEW.id_atendimento_externo
    FOR UPDATE OF pv;

    IF FOUND THEN
        INSERT INTO pos_venda_evento(id_caso,tipo,descricao)
        VALUES (caso.id,'integracao',
            'Atendimento externo ' || NEW.identificador_externo ||
            ' atualizado para ' || NEW.situacao_normalizada ||
            ' pela fonte ' || coalesce(fonte_codigo,'#' || NEW.id_fonte) || '.');
        IF caso.status IN ('nao_deseja','nao_contatar') THEN RETURN NEW; END IF;
        IF caso.id_atendimento_externo_reagendado IS DISTINCT FROM NEW.id_atendimento_externo THEN
            RETURN NEW;
        END IF;
        novo_status := CASE NEW.situacao_normalizada
            WHEN 'REALIZADO' THEN 'recuperado'
            WHEN 'FALTOU' THEN 'nova_falta'
            WHEN 'CANCELADO' THEN 'pendente'
            ELSE 'reagendado' END;
        UPDATE pos_venda_caso SET
            status=novo_status,
            recorrente=recorrente OR novo_status='nova_falta',
            proxima_acao=CASE WHEN novo_status IN ('nova_falta','pendente') THEN now() ELSE NULL END
        WHERE id=caso.id;
        IF novo_status='nova_falta' THEN
            INSERT INTO pos_venda_alerta(id_caso,tipo,mensagem)
            VALUES (caso.id,'revisar_recorrencia',
                'Nova falta recorrente: revise o plano de contato e a manutenção da vaga.')
            ON CONFLICT(id_clinica,id_caso,tipo) WHERE status='aberto' DO NOTHING;
        END IF;
        RETURN NEW;
    END IF;

    SELECT pv.* INTO caso FROM pos_venda_caso pv
    WHERE pv.id_atendimento_externo_origem=NEW.id_atendimento_externo FOR UPDATE;

    IF NOT FOUND THEN
        IF NEW.situacao_normalizada='FALTOU' THEN
            INSERT INTO pos_venda_caso(
                id_consulta_origem,id_atendimento_externo_origem,data_falta,status,origem
            ) VALUES (
                NULL,NEW.id_atendimento_externo,
                NEW.agendado_em AT TIME ZONE NEW.fuso_horario,
                CASE WHEN EXISTS (
                    SELECT 1 FROM integracao_paciente_externo p
                    WHERE p.id_paciente_externo=NEW.id_paciente_externo AND p.nao_contatar
                ) THEN 'nao_contatar' ELSE 'pendente' END,
                CASE WHEN NEW.id_importacao IS NULL THEN 'integracao_api' ELSE 'importacao_csv' END
            ) RETURNING id INTO novo_id;
            INSERT INTO pos_venda_evento(id_caso,tipo,descricao)
            VALUES (novo_id,'falta',
                'Falta recebida da fonte externa ' || coalesce(fonte_codigo,'#' || NEW.id_fonte) || '.');
        END IF;
        RETURN NEW;
    END IF;

    INSERT INTO pos_venda_evento(id_caso,tipo,descricao)
    VALUES (caso.id,'importacao',
        'Atendimento externo de origem atualizado para ' || NEW.situacao_normalizada || '.');
    IF caso.status IN ('nao_deseja','nao_contatar','recuperado') THEN RETURN NEW; END IF;
    UPDATE pos_venda_caso SET
        status=CASE WHEN NEW.situacao_normalizada='FALTOU' THEN 'pendente' ELSE 'registro_corrigido' END,
        proxima_acao=CASE WHEN NEW.situacao_normalizada='FALTOU' THEN proxima_acao ELSE NULL END
    WHERE id=caso.id;
    RETURN NEW;
END;
$$;

INSERT INTO auth_schema_version(version) VALUES (21) ON CONFLICT DO NOTHING;
COMMIT;
