-- Issue #34: chamados, SLA e acesso temporario de suporte.
BEGIN;

CREATE TABLE IF NOT EXISTS suporte_chamado (
    id_chamado BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_clinica INTEGER NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer REFERENCES clinica(id_clinica),
    protocolo VARCHAR(24) NOT NULL,
    titulo VARCHAR(160) NOT NULL,
    descricao VARCHAR(3000) NOT NULL,
    categoria VARCHAR(30) NOT NULL CHECK (categoria IN ('implantacao','agenda','mensagens','acesso','financeiro','outro')),
    prioridade VARCHAR(15) NOT NULL CHECK (prioridade IN ('critica','alta','normal','baixa')),
    status VARCHAR(20) NOT NULL DEFAULT 'ABERTO' CHECK (status IN ('ABERTO','EM_ATENDIMENTO','AGUARDANDO_CLINICA','RESOLVIDO','FECHADO')),
    id_solicitante INTEGER NOT NULL,
    id_responsavel INTEGER,
    primeira_resposta_em TIMESTAMPTZ,
    resolvido_em TIMESTAMPTZ,
    satisfacao SMALLINT CHECK (satisfacao BETWEEN 1 AND 5),
    esforco_minutos INTEGER NOT NULL DEFAULT 0 CHECK (esforco_minutos >= 0),
    reaberturas INTEGER NOT NULL DEFAULT 0 CHECK (reaberturas >= 0),
    versao INTEGER NOT NULL DEFAULT 0,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(id_clinica,id_chamado),
    UNIQUE(id_clinica,protocolo),
    FOREIGN KEY(id_clinica,id_solicitante) REFERENCES tenant_membro(id_clinica,id_usuario),
    FOREIGN KEY(id_responsavel) REFERENCES usuario(id)
);

CREATE TABLE IF NOT EXISTS suporte_chamado_mensagem (
    id_mensagem BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_clinica INTEGER NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer REFERENCES clinica(id_clinica),
    id_chamado BIGINT NOT NULL,
    id_autor INTEGER NOT NULL REFERENCES usuario(id),
    mensagem VARCHAR(4000) NOT NULL,
    interna BOOLEAN NOT NULL DEFAULT false,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(id_clinica,id_mensagem),
    FOREIGN KEY(id_clinica,id_chamado) REFERENCES suporte_chamado(id_clinica,id_chamado)
);

CREATE TABLE IF NOT EXISTS suporte_chamado_evento (
    id_evento BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_clinica INTEGER NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer REFERENCES clinica(id_clinica),
    id_chamado BIGINT NOT NULL,
    id_autor INTEGER NOT NULL REFERENCES usuario(id),
    tipo VARCHAR(30) NOT NULL CHECK (tipo IN ('CRIADO','MENSAGEM','STATUS','ATRIBUICAO','SATISFACAO')),
    detalhes JSONB NOT NULL DEFAULT '{}'::jsonb,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY(id_clinica,id_chamado) REFERENCES suporte_chamado(id_clinica,id_chamado)
);

-- Esta tabela participa da autenticacao antes da selecao do tenant. Guarda apenas
-- a concessao minima; chamados e dados operacionais continuam sob RLS por tenant.
CREATE TABLE IF NOT EXISTS auth_suporte_acesso (
    id_acesso BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_clinica INTEGER NOT NULL REFERENCES clinica(id_clinica),
    id_agente INTEGER NOT NULL REFERENCES usuario(id),
    id_solicitante INTEGER NOT NULL,
    id_autorizador INTEGER,
    motivo VARCHAR(500) NOT NULL,
    escopos JSONB NOT NULL CHECK (jsonb_typeof(escopos)='array'),
    status VARCHAR(15) NOT NULL DEFAULT 'PENDENTE' CHECK (status IN ('PENDENTE','APROVADO','REVOGADO','NEGADO')),
    solicitado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    autorizado_em TIMESTAMPTZ,
    expira_em TIMESTAMPTZ NOT NULL,
    revogado_em TIMESTAMPTZ,
    CHECK (expira_em > solicitado_em),
    CHECK (expira_em <= solicitado_em + interval '8 hours'),
    CHECK (id_autorizador IS NULL OR id_autorizador <> id_solicitante),
    FOREIGN KEY(id_clinica,id_solicitante) REFERENCES tenant_membro(id_clinica,id_usuario),
    FOREIGN KEY(id_clinica,id_autorizador) REFERENCES tenant_membro(id_clinica,id_usuario)
);

CREATE UNIQUE INDEX IF NOT EXISTS auth_suporte_acesso_ativo_uk
    ON auth_suporte_acesso(id_clinica,id_agente)
    WHERE status IN ('PENDENTE','APROVADO') AND revogado_em IS NULL;

CREATE TABLE IF NOT EXISTS auth_suporte_acesso_evento (
    id_evento BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_acesso BIGINT NOT NULL REFERENCES auth_suporte_acesso(id_acesso),
    id_clinica INTEGER NOT NULL REFERENCES clinica(id_clinica),
    id_autor INTEGER REFERENCES usuario(id),
    tipo VARCHAR(30) NOT NULL CHECK (tipo IN ('SOLICITADO','APROVADO','NEGADO','REVOGADO','ACAO')),
    recurso VARCHAR(255),
    metodo VARCHAR(10),
    resultado INTEGER,
    detalhes JSONB NOT NULL DEFAULT '{}'::jsonb,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_suporte_chamado_fila ON suporte_chamado(id_clinica,status,prioridade,criado_em);
CREATE INDEX IF NOT EXISTS idx_suporte_mensagem_chamado ON suporte_chamado_mensagem(id_clinica,id_chamado,criado_em);
CREATE INDEX IF NOT EXISTS idx_suporte_acesso_agente ON auth_suporte_acesso(id_agente,status,expira_em);
CREATE INDEX IF NOT EXISTS idx_suporte_acesso_evento ON auth_suporte_acesso_evento(id_acesso,criado_em DESC);

CREATE OR REPLACE FUNCTION suporte_chamado_versionar() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    NEW.versao := OLD.versao + 1;
    NEW.atualizado_em := now();
    RETURN NEW;
END;
$$;
DROP TRIGGER IF EXISTS trg_suporte_chamado_versionar ON suporte_chamado;
CREATE TRIGGER trg_suporte_chamado_versionar BEFORE UPDATE ON suporte_chamado
FOR EACH ROW EXECUTE FUNCTION suporte_chamado_versionar();

DO $rls$
DECLARE tabela TEXT;
BEGIN
    FOREACH tabela IN ARRAY ARRAY['suporte_chamado','suporte_chamado_mensagem','suporte_chamado_evento'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', tabela);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', tabela);
        IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname=current_schema() AND tablename=tabela AND policyname='tenant_isolation') THEN
            EXECUTE format('CREATE POLICY tenant_isolation ON %I USING(id_clinica=nullif(current_setting(''app.tenant_id'',true),'''')::integer) WITH CHECK(id_clinica=nullif(current_setting(''app.tenant_id'',true),'''')::integer)', tabela);
        END IF;
    END LOOP;
END $rls$;

INSERT INTO auth_schema_version(version) VALUES (29) ON CONFLICT DO NOTHING;
COMMIT;
