-- Issue #33: operação interna do SaaS. O painel lê apenas projeções agregadas.
BEGIN;

ALTER TABLE auth_session ADD COLUMN IF NOT EXISTS autenticado_em TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE TABLE IF NOT EXISTS auth_saas_funcionario (
    id_funcionario BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_usuario INTEGER NOT NULL UNIQUE REFERENCES usuario(id) ON DELETE CASCADE,
    papel VARCHAR(20) NOT NULL CHECK (papel IN ('operador','gestor','administrador')),
    ativo BOOLEAN NOT NULL DEFAULT true,
    exige_reautenticacao BOOLEAN NOT NULL DEFAULT true,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Não contém pacientes, prontuários, mensagens ou payloads de integrações.
CREATE TABLE IF NOT EXISTS auth_saas_clinica_indicador (
    id_clinica INTEGER PRIMARY KEY REFERENCES clinica(id_clinica) ON DELETE CASCADE,
    onboarding_status VARCHAR(20),
    onboarding_atualizado_em TIMESTAMPTZ,
    integracao_estado VARCHAR(24),
    integracao_falha_em TIMESTAMPTZ,
    chamados_abertos INTEGER NOT NULL DEFAULT 0 CHECK (chamados_abertos >= 0),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS auth_saas_clinica_responsavel (
    id_clinica INTEGER PRIMARY KEY REFERENCES clinica(id_clinica) ON DELETE CASCADE,
    id_funcionario BIGINT NOT NULL REFERENCES auth_saas_funcionario(id_funcionario),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS auth_saas_acao (
    id_acao BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_clinica INTEGER NOT NULL REFERENCES clinica(id_clinica) ON DELETE CASCADE,
    id_responsavel BIGINT REFERENCES auth_saas_funcionario(id_funcionario),
    titulo VARCHAR(180) NOT NULL,
    descricao VARCHAR(1000),
    tipo VARCHAR(30) NOT NULL CHECK (tipo IN ('onboarding','adocao','suporte','integracao','comercial','outro')),
    status VARCHAR(20) NOT NULL DEFAULT 'ABERTA' CHECK (status IN ('ABERTA','CONCLUIDA','CANCELADA')),
    vence_em TIMESTAMPTZ,
    concluida_em TIMESTAMPTZ,
    criado_por BIGINT NOT NULL REFERENCES auth_saas_funcionario(id_funcionario),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS auth_saas_acao_fila_idx ON auth_saas_acao(status,vence_em,id_clinica);

CREATE TABLE IF NOT EXISTS auth_saas_audit_log (
    id_evento BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_funcionario BIGINT REFERENCES auth_saas_funcionario(id_funcionario),
    acao VARCHAR(80) NOT NULL,
    recurso VARCHAR(255) NOT NULL,
    resultado INTEGER NOT NULL,
    endereco_ip VARCHAR(64),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE OR REPLACE FUNCTION auth_saas_atualizar_onboarding() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO auth_saas_clinica_indicador(id_clinica,onboarding_status,onboarding_atualizado_em,atualizado_em)
    VALUES (NEW.id_clinica,NEW.status,NEW.atualizado_em,now())
    ON CONFLICT(id_clinica) DO UPDATE SET onboarding_status=EXCLUDED.onboarding_status,
        onboarding_atualizado_em=EXCLUDED.onboarding_atualizado_em,atualizado_em=now();
    RETURN NEW;
END;
$$;

DO $triggers$
BEGIN
    IF to_regclass('public.onboarding_clinica') IS NOT NULL THEN
        DROP TRIGGER IF EXISTS trg_saas_onboarding_indicador ON onboarding_clinica;
        CREATE TRIGGER trg_saas_onboarding_indicador AFTER INSERT OR UPDATE ON onboarding_clinica
        FOR EACH ROW EXECUTE FUNCTION auth_saas_atualizar_onboarding();
    END IF;
    IF to_regclass('public.integracao_fonte_dados') IS NOT NULL THEN
        EXECUTE $sql$
          CREATE OR REPLACE FUNCTION auth_saas_atualizar_integracao() RETURNS trigger LANGUAGE plpgsql AS $fn$
          BEGIN
            INSERT INTO auth_saas_clinica_indicador(id_clinica,integracao_estado,integracao_falha_em,atualizado_em)
            VALUES (NEW.id_clinica,NEW.estado_conexao,NEW.ultima_falha_em,now())
            ON CONFLICT(id_clinica) DO UPDATE SET integracao_estado=EXCLUDED.integracao_estado,
              integracao_falha_em=EXCLUDED.integracao_falha_em,atualizado_em=now();
            RETURN NEW;
          END; $fn$;
        $sql$;
        DROP TRIGGER IF EXISTS trg_saas_integracao_indicador ON integracao_fonte_dados;
        CREATE TRIGGER trg_saas_integracao_indicador AFTER INSERT OR UPDATE ON integracao_fonte_dados
        FOR EACH ROW EXECUTE FUNCTION auth_saas_atualizar_integracao();
    END IF;
END $triggers$;

INSERT INTO auth_saas_clinica_indicador(id_clinica)
SELECT id_clinica FROM clinica ON CONFLICT DO NOTHING;

INSERT INTO auth_schema_version(version) VALUES (30) ON CONFLICT DO NOTHING;
COMMIT;
