-- Issue #29: pacotes versionados de configuracao por especialidade.
-- O pacote pertence ao tenant; nenhuma clinica pode publicar ou aplicar configuracoes de outra.
BEGIN;

CREATE TABLE IF NOT EXISTS especialidade_pacote (
    id_pacote BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_clinica INTEGER NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer REFERENCES clinica(id_clinica),
    codigo VARCHAR(80) NOT NULL,
    nome VARCHAR(160) NOT NULL,
    especialidade VARCHAR(120) NOT NULL,
    descricao VARCHAR(1200) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT true,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(id_clinica,id_pacote),
    UNIQUE(id_clinica,codigo)
);

CREATE TABLE IF NOT EXISTS especialidade_pacote_versao (
    id_versao BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_clinica INTEGER NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer REFERENCES clinica(id_clinica),
    id_pacote BIGINT NOT NULL,
    versao INTEGER NOT NULL CHECK (versao > 0),
    status VARCHAR(20) NOT NULL DEFAULT 'EM_REVISAO'
        CHECK (status IN ('RASCUNHO','EM_REVISAO','PUBLICADA','DESATIVADA')),
    componentes JSONB NOT NULL CHECK (jsonb_typeof(componentes)='array'),
    id_revisor INTEGER,
    registro_revisor VARCHAR(80),
    revisado_em TIMESTAMPTZ,
    publicado_em TIMESTAMPTZ,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(id_clinica,id_versao),
    UNIQUE(id_clinica,id_pacote,id_versao),
    UNIQUE(id_clinica,id_pacote,versao),
    FOREIGN KEY(id_clinica,id_pacote) REFERENCES especialidade_pacote(id_clinica,id_pacote),
    FOREIGN KEY(id_clinica,id_revisor) REFERENCES tenant_membro(id_clinica,id_usuario),
    CHECK ((status <> 'PUBLICADA') OR
        (id_revisor IS NOT NULL AND registro_revisor IS NOT NULL AND revisado_em IS NOT NULL AND publicado_em IS NOT NULL))
);

CREATE TABLE IF NOT EXISTS especialidade_pacote_aplicacao (
    id_aplicacao BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_clinica INTEGER NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer REFERENCES clinica(id_clinica),
    id_pacote BIGINT NOT NULL,
    id_versao BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ATIVA' CHECK (status IN ('ATIVA','DESATIVADA','REVERTIDA')),
    componentes_selecionados JSONB NOT NULL CHECK (jsonb_typeof(componentes_selecionados)='array'),
    politica_comunicacao_aprovada BOOLEAN NOT NULL DEFAULT false,
    id_aplicado_por INTEGER NOT NULL,
    iniciada_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    aplicada_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    desativada_em TIMESTAMPTZ,
    versao_lock INTEGER NOT NULL DEFAULT 0,
    UNIQUE(id_clinica,id_aplicacao),
    UNIQUE(id_clinica,id_pacote),
    FOREIGN KEY(id_clinica,id_pacote) REFERENCES especialidade_pacote(id_clinica,id_pacote),
    FOREIGN KEY(id_clinica,id_pacote,id_versao) REFERENCES especialidade_pacote_versao(id_clinica,id_pacote,id_versao),
    FOREIGN KEY(id_clinica,id_aplicado_por) REFERENCES tenant_membro(id_clinica,id_usuario)
);

CREATE TABLE IF NOT EXISTS especialidade_componente_aplicado (
    id_componente BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_clinica INTEGER NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer REFERENCES clinica(id_clinica),
    id_aplicacao BIGINT NOT NULL,
    chave VARCHAR(100) NOT NULL,
    tipo VARCHAR(30) NOT NULL CHECK (tipo IN ('MODELO_ATENDIMENTO','DOCUMENTO','FORMULARIO','FLUXO_ACOMPANHAMENTO')),
    configuracao_base JSONB NOT NULL,
    configuracao_local JSONB NOT NULL,
    versao_origem INTEGER NOT NULL,
    personalizado BOOLEAN NOT NULL DEFAULT false,
    ativo BOOLEAN NOT NULL DEFAULT true,
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(id_clinica,id_aplicacao,chave),
    FOREIGN KEY(id_clinica,id_aplicacao) REFERENCES especialidade_pacote_aplicacao(id_clinica,id_aplicacao)
);

CREATE TABLE IF NOT EXISTS especialidade_pacote_evento (
    id_evento BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_clinica INTEGER NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer REFERENCES clinica(id_clinica),
    id_pacote BIGINT NOT NULL,
    id_aplicacao BIGINT,
    id_usuario INTEGER NOT NULL,
    tipo VARCHAR(40) NOT NULL CHECK (tipo IN ('VERSAO_CRIADA','VERSAO_ATUALIZADA','VERSAO_ENVIADA_REVISAO','VERSAO_PUBLICADA','APLICACAO_CRIADA','APLICACAO_ATUALIZADA','APLICACAO_DESATIVADA','APLICACAO_REVERTIDA')),
    detalhes JSONB NOT NULL DEFAULT '{}'::jsonb,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY(id_clinica,id_pacote) REFERENCES especialidade_pacote(id_clinica,id_pacote),
    FOREIGN KEY(id_clinica,id_aplicacao) REFERENCES especialidade_pacote_aplicacao(id_clinica,id_aplicacao),
    FOREIGN KEY(id_clinica,id_usuario) REFERENCES tenant_membro(id_clinica,id_usuario)
);

CREATE INDEX IF NOT EXISTS idx_especialidade_pacote_catalogo ON especialidade_pacote(id_clinica,ativo,especialidade);
CREATE INDEX IF NOT EXISTS idx_especialidade_pacote_versao_status ON especialidade_pacote_versao(id_clinica,id_pacote,status,versao DESC);
CREATE INDEX IF NOT EXISTS idx_especialidade_evento_data ON especialidade_pacote_evento(id_clinica,id_pacote,criado_em DESC);

DO $rls$
DECLARE tabela TEXT;
BEGIN
    FOREACH tabela IN ARRAY ARRAY['especialidade_pacote','especialidade_pacote_versao',
        'especialidade_pacote_aplicacao','especialidade_componente_aplicado','especialidade_pacote_evento'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', tabela);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', tabela);
        IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname=current_schema() AND tablename=tabela AND policyname='tenant_isolation') THEN
            EXECUTE format('CREATE POLICY tenant_isolation ON %I USING(id_clinica=nullif(current_setting(''app.tenant_id'',true),'''')::integer) WITH CHECK(id_clinica=nullif(current_setting(''app.tenant_id'',true),'''')::integer)', tabela);
        END IF;
    END LOOP;
END $rls$;

INSERT INTO auth_schema_version(version) VALUES (28) ON CONFLICT DO NOTHING;
COMMIT;
