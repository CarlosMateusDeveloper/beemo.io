-- Issue #18: tarefas, jornadas e campanhas reais e isoladas por tenant.
-- Aplicar depois de 015_roles_permissions_audit.sql.
BEGIN;

CREATE TABLE IF NOT EXISTS pos_venda_tarefa (
    id_tarefa BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_clinica INTEGER NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer REFERENCES clinica(id_clinica),
    id_paciente INTEGER,
    paciente_referencia VARCHAR(160),
    titulo VARCHAR(160) NOT NULL,
    descricao VARCHAR(2000),
    id_responsavel INTEGER,
    prioridade VARCHAR(10) NOT NULL CHECK (prioridade IN ('alta','media','baixa')),
    prazo TIMESTAMPTZ NOT NULL,
    origem VARCHAR(160) NOT NULL,
    status VARCHAR(15) NOT NULL DEFAULT 'aberta' CHECK (status IN ('aberta','concluida','cancelada')),
    concluida_em TIMESTAMPTZ,
    criado_por INTEGER NOT NULL,
    atualizado_por INTEGER NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    versao INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY(id_clinica,id_paciente) REFERENCES paciente(id_clinica,id_paciente),
    FOREIGN KEY(id_clinica,id_responsavel) REFERENCES tenant_membro(id_clinica,id_usuario),
    FOREIGN KEY(id_clinica,criado_por) REFERENCES tenant_membro(id_clinica,id_usuario),
    FOREIGN KEY(id_clinica,atualizado_por) REFERENCES tenant_membro(id_clinica,id_usuario)
);

CREATE TABLE IF NOT EXISTS pos_venda_jornada (
    id_jornada BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_clinica INTEGER NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer REFERENCES clinica(id_clinica),
    nome VARCHAR(160) NOT NULL,
    descricao VARCHAR(2000),
    tipo VARCHAR(30) NOT NULL CHECK (tipo IN ('retorno','pos_atendimento','reativacao','preventiva')),
    regra_entrada JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(15) NOT NULL DEFAULT 'rascunho' CHECK (status IN ('rascunho','ativa','pausada','arquivada')),
    id_responsavel INTEGER,
    criado_por INTEGER NOT NULL,
    atualizado_por INTEGER NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    versao INTEGER NOT NULL DEFAULT 0,
    UNIQUE(id_clinica,id_jornada),
    FOREIGN KEY(id_clinica,id_responsavel) REFERENCES tenant_membro(id_clinica,id_usuario),
    FOREIGN KEY(id_clinica,criado_por) REFERENCES tenant_membro(id_clinica,id_usuario),
    FOREIGN KEY(id_clinica,atualizado_por) REFERENCES tenant_membro(id_clinica,id_usuario)
);

CREATE TABLE IF NOT EXISTS pos_venda_jornada_etapa (
    id_etapa BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_clinica INTEGER NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer REFERENCES clinica(id_clinica),
    id_jornada BIGINT NOT NULL,
    ordem INTEGER NOT NULL CHECK (ordem >= 0),
    atraso_dias INTEGER NOT NULL DEFAULT 0 CHECK (atraso_dias >= 0),
    titulo VARCHAR(160) NOT NULL,
    canal VARCHAR(20) NOT NULL CHECK (canal IN ('whatsapp','email','ligacao','tarefa')),
    conteudo VARCHAR(4000),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(id_clinica,id_jornada,ordem),
    FOREIGN KEY(id_clinica,id_jornada) REFERENCES pos_venda_jornada(id_clinica,id_jornada) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS pos_venda_jornada_inscricao (
    id_inscricao BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_clinica INTEGER NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer REFERENCES clinica(id_clinica),
    id_jornada BIGINT NOT NULL,
    id_paciente INTEGER NOT NULL,
    etapa_atual INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(15) NOT NULL DEFAULT 'ativa' CHECK (status IN ('ativa','concluida','cancelada')),
    inscrito_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    concluido_em TIMESTAMPTZ,
    criado_por INTEGER NOT NULL,
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    versao INTEGER NOT NULL DEFAULT 0,
    UNIQUE(id_clinica,id_jornada,id_paciente),
    FOREIGN KEY(id_clinica,id_jornada) REFERENCES pos_venda_jornada(id_clinica,id_jornada) ON DELETE CASCADE,
    FOREIGN KEY(id_clinica,id_paciente) REFERENCES paciente(id_clinica,id_paciente),
    FOREIGN KEY(id_clinica,criado_por) REFERENCES tenant_membro(id_clinica,id_usuario)
);

CREATE TABLE IF NOT EXISTS pos_venda_campanha (
    id_campanha BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_clinica INTEGER NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer REFERENCES clinica(id_clinica),
    nome VARCHAR(160) NOT NULL,
    objetivo VARCHAR(160) NOT NULL,
    publico JSONB NOT NULL,
    canal VARCHAR(20) NOT NULL CHECK (canal IN ('whatsapp','email','multicanal')),
    conteudo VARCHAR(4000) NOT NULL,
    programado_para TIMESTAMPTZ,
    status VARCHAR(15) NOT NULL DEFAULT 'rascunho' CHECK (status IN ('rascunho','agendada','em_andamento','pausada','concluida','cancelada')),
    id_responsavel INTEGER,
    destinatarios INTEGER NOT NULL DEFAULT 0,
    enviados INTEGER NOT NULL DEFAULT 0,
    entregues INTEGER NOT NULL DEFAULT 0,
    respostas INTEGER NOT NULL DEFAULT 0,
    conversoes INTEGER NOT NULL DEFAULT 0,
    criado_por INTEGER NOT NULL,
    atualizado_por INTEGER NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    versao INTEGER NOT NULL DEFAULT 0,
    UNIQUE(id_clinica,id_campanha),
    FOREIGN KEY(id_clinica,id_responsavel) REFERENCES tenant_membro(id_clinica,id_usuario),
    FOREIGN KEY(id_clinica,criado_por) REFERENCES tenant_membro(id_clinica,id_usuario),
    FOREIGN KEY(id_clinica,atualizado_por) REFERENCES tenant_membro(id_clinica,id_usuario)
);

CREATE TABLE IF NOT EXISTS pos_venda_campanha_publico (
    id_clinica INTEGER NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer REFERENCES clinica(id_clinica),
    id_campanha BIGINT NOT NULL,
    id_paciente INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'incluido' CHECK (status IN ('incluido','enviado','entregue','respondido','convertido','excluido')),
    incluido_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY(id_clinica,id_campanha,id_paciente),
    FOREIGN KEY(id_clinica,id_campanha) REFERENCES pos_venda_campanha(id_clinica,id_campanha) ON DELETE CASCADE,
    FOREIGN KEY(id_clinica,id_paciente) REFERENCES paciente(id_clinica,id_paciente)
);

CREATE TABLE IF NOT EXISTS pos_venda_historico (
    id_historico BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_clinica INTEGER NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer REFERENCES clinica(id_clinica),
    recurso VARCHAR(20) NOT NULL CHECK (recurso IN ('tarefa','jornada','inscricao','campanha')),
    recurso_id BIGINT NOT NULL,
    acao VARCHAR(40) NOT NULL,
    id_usuario INTEGER NOT NULL,
    dados JSONB NOT NULL DEFAULT '{}'::jsonb,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY(id_clinica,id_usuario) REFERENCES tenant_membro(id_clinica,id_usuario)
);

CREATE INDEX IF NOT EXISTS idx_pv_tarefa_fila ON pos_venda_tarefa(id_clinica,status,prazo);
CREATE INDEX IF NOT EXISTS idx_pv_tarefa_responsavel ON pos_venda_tarefa(id_clinica,id_responsavel,status);
CREATE INDEX IF NOT EXISTS idx_pv_jornada_status ON pos_venda_jornada(id_clinica,status);
CREATE INDEX IF NOT EXISTS idx_pv_inscricao_status ON pos_venda_jornada_inscricao(id_clinica,id_jornada,status);
CREATE INDEX IF NOT EXISTS idx_pv_campanha_status ON pos_venda_campanha(id_clinica,status,programado_para);
CREATE INDEX IF NOT EXISTS idx_pv_historico_recurso ON pos_venda_historico(id_clinica,recurso,recurso_id,id_historico DESC);

CREATE OR REPLACE FUNCTION pos_venda_operacao_versionar() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    NEW.versao := OLD.versao + 1;
    NEW.atualizado_em := now();
    RETURN NEW;
END;
$$;

DO $triggers$
DECLARE tabela TEXT;
BEGIN
    FOREACH tabela IN ARRAY ARRAY['pos_venda_tarefa','pos_venda_jornada','pos_venda_jornada_inscricao','pos_venda_campanha'] LOOP
        EXECUTE format('DROP TRIGGER IF EXISTS trg_%s_versionar ON %I', tabela, tabela);
        EXECUTE format('CREATE TRIGGER trg_%s_versionar BEFORE UPDATE ON %I FOR EACH ROW EXECUTE FUNCTION pos_venda_operacao_versionar()', tabela, tabela);
    END LOOP;
END $triggers$;

DO $rls$
DECLARE tabela TEXT;
BEGIN
    FOREACH tabela IN ARRAY ARRAY['pos_venda_tarefa','pos_venda_jornada','pos_venda_jornada_etapa',
        'pos_venda_jornada_inscricao','pos_venda_campanha','pos_venda_campanha_publico','pos_venda_historico'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', tabela);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', tabela);
        IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname=current_schema() AND tablename=tabela AND policyname='tenant_isolation') THEN
            EXECUTE format('CREATE POLICY tenant_isolation ON %I USING(id_clinica=nullif(current_setting(''app.tenant_id'',true),'''')::integer) WITH CHECK(id_clinica=nullif(current_setting(''app.tenant_id'',true),'''')::integer)', tabela);
        END IF;
    END LOOP;
END $rls$;

INSERT INTO auth_schema_version(version) VALUES (16) ON CONFLICT DO NOTHING;
COMMIT;
