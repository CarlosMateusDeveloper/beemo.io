-- Issue #24: outbox, idempotencia, retentativas, limites e observabilidade.
-- Aplicar depois de 022_comunicacao_consentimento.sql.
BEGIN;

CREATE TABLE mensageria_outbox (
    id_outbox BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_clinica INTEGER NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer
        REFERENCES clinica(id_clinica),
    idempotency_key VARCHAR(160) NOT NULL,
    canal VARCHAR(20) NOT NULL CHECK(canal IN ('whatsapp','email','sms')),
    provedor VARCHAR(40) NOT NULL,
    destino VARCHAR(160) NOT NULL,
    tipo VARCHAR(20) NOT NULL CHECK(tipo IN ('texto','template')),
    payload JSONB NOT NULL,
    origem VARCHAR(80) NOT NULL,
    id_campanha BIGINT,
    status VARCHAR(16) NOT NULL DEFAULT 'pendente'
        CHECK(status IN ('pendente','processando','retentativa','enviada','morta','cancelada')),
    tentativas INTEGER NOT NULL DEFAULT 0 CHECK(tentativas >= 0),
    max_tentativas INTEGER NOT NULL DEFAULT 6 CHECK(max_tentativas BETWEEN 1 AND 20),
    disponivel_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    bloqueado_em TIMESTAMPTZ,
    bloqueado_por VARCHAR(120),
    id_mensagem_provedor VARCHAR(200),
    erro_categoria VARCHAR(20),
    erro_codigo VARCHAR(80),
    erro_mensagem VARCHAR(800),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    enviado_em TIMESTAMPTZ,
    UNIQUE(id_clinica,id_outbox),
    UNIQUE(id_clinica,idempotency_key)
);
CREATE INDEX mensageria_outbox_aquisicao_idx
    ON mensageria_outbox(disponivel_em,id_outbox)
    WHERE status IN ('pendente','retentativa');
CREATE INDEX mensageria_outbox_operacao_idx
    ON mensageria_outbox(id_clinica,status,atualizado_em DESC);

CREATE TABLE mensageria_evento (
    id_evento BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_clinica INTEGER NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer
        REFERENCES clinica(id_clinica),
    id_outbox BIGINT NOT NULL,
    tipo VARCHAR(30) NOT NULL CHECK(tipo IN (
        'enfileirada','adquirida','enviada','retentativa','morta','reprocessada',
        'cancelada','limitada','circuito_aberto'
    )),
    detalhes JSONB NOT NULL DEFAULT '{}'::jsonb,
    id_usuario INTEGER,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(id_clinica,id_evento),
    FOREIGN KEY(id_clinica,id_outbox) REFERENCES mensageria_outbox(id_clinica,id_outbox),
    FOREIGN KEY(id_clinica,id_usuario) REFERENCES tenant_membro(id_clinica,id_usuario)
);
CREATE INDEX mensageria_evento_metricas_idx
    ON mensageria_evento(id_clinica,tipo,criado_em DESC);

CREATE TABLE mensageria_limite (
    id_limite BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_clinica INTEGER NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer
        REFERENCES clinica(id_clinica),
    escopo VARCHAR(20) NOT NULL CHECK(escopo IN ('clinica','numero','campanha','provedor')),
    chave VARCHAR(160) NOT NULL DEFAULT '*',
    janela_segundos INTEGER NOT NULL CHECK(janela_segundos BETWEEN 1 AND 86400),
    max_envios INTEGER NOT NULL CHECK(max_envios > 0),
    ativo BOOLEAN NOT NULL DEFAULT true,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(id_clinica,id_limite),
    UNIQUE(id_clinica,escopo,chave)
);

CREATE TABLE mensageria_provedor_estado (
    id_clinica INTEGER NOT NULL REFERENCES clinica(id_clinica),
    provedor VARCHAR(40) NOT NULL,
    estado VARCHAR(12) NOT NULL DEFAULT 'fechado' CHECK(estado IN ('fechado','aberto','meio_aberto')),
    falhas_consecutivas INTEGER NOT NULL DEFAULT 0 CHECK(falhas_consecutivas >= 0),
    aberto_ate TIMESTAMPTZ,
    ultimo_erro VARCHAR(800),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY(id_clinica,provedor)
);

CREATE TABLE mensageria_evento_recebido (
    id_recebido BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_clinica INTEGER NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer
        REFERENCES clinica(id_clinica),
    provedor VARCHAR(40) NOT NULL,
    id_externo VARCHAR(200) NOT NULL,
    status VARCHAR(12) NOT NULL DEFAULT 'processando' CHECK(status IN ('processando','processado','erro')),
    erro VARCHAR(800),
    recebido_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    processado_em TIMESTAMPTZ,
    UNIQUE(id_clinica,id_recebido),
    UNIQUE(id_clinica,provedor,id_externo)
);

ALTER TABLE mensagem
    ADD COLUMN IF NOT EXISTS id_outbox BIGINT,
    ADD COLUMN IF NOT EXISTS id_mensagem_provedor VARCHAR(200),
    ADD COLUMN IF NOT EXISTS status_entrega VARCHAR(16) NOT NULL DEFAULT 'enviada';
ALTER TABLE mensagem ADD CONSTRAINT mensagem_status_entrega_check
    CHECK(status_entrega IN ('pendente','enviada','falha','recebida'));
ALTER TABLE mensagem ADD CONSTRAINT mensagem_outbox_fk
    FOREIGN KEY(id_clinica,id_outbox) REFERENCES mensageria_outbox(id_clinica,id_outbox);
CREATE UNIQUE INDEX mensagem_outbox_unique_idx ON mensagem(id_clinica,id_outbox) WHERE id_outbox IS NOT NULL;

CREATE OR REPLACE FUNCTION mensageria_bloquear_evento_mutacao() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Historico de mensageria e imutavel';
END;
$$;
CREATE TRIGGER trg_mensageria_evento_immutable BEFORE UPDATE OR DELETE ON mensageria_evento
    FOR EACH ROW EXECUTE FUNCTION mensageria_bloquear_evento_mutacao();

-- Sem RLS ainda, para criar os padrões de todos os tenants existentes.
INSERT INTO mensageria_limite(id_clinica,escopo,chave,janela_segundos,max_envios)
SELECT id_clinica,'clinica','*',60,120 FROM clinica ON CONFLICT DO NOTHING;
INSERT INTO mensageria_limite(id_clinica,escopo,chave,janela_segundos,max_envios)
SELECT id_clinica,'numero','*',86400,20 FROM clinica ON CONFLICT DO NOTHING;
INSERT INTO mensageria_limite(id_clinica,escopo,chave,janela_segundos,max_envios)
SELECT id_clinica,'provedor','meta',60,80 FROM clinica ON CONFLICT DO NOTHING;

ALTER TABLE mensageria_outbox ENABLE ROW LEVEL SECURITY;
ALTER TABLE mensageria_outbox FORCE ROW LEVEL SECURITY;
ALTER TABLE mensageria_evento ENABLE ROW LEVEL SECURITY;
ALTER TABLE mensageria_evento FORCE ROW LEVEL SECURITY;
ALTER TABLE mensageria_limite ENABLE ROW LEVEL SECURITY;
ALTER TABLE mensageria_limite FORCE ROW LEVEL SECURITY;
ALTER TABLE mensageria_provedor_estado ENABLE ROW LEVEL SECURITY;
ALTER TABLE mensageria_provedor_estado FORCE ROW LEVEL SECURITY;
ALTER TABLE mensageria_evento_recebido ENABLE ROW LEVEL SECURITY;
ALTER TABLE mensageria_evento_recebido FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON mensageria_outbox
    USING(id_clinica=nullif(current_setting('app.tenant_id',true),'')::integer)
    WITH CHECK(id_clinica=nullif(current_setting('app.tenant_id',true),'')::integer);
CREATE POLICY tenant_isolation ON mensageria_evento
    USING(id_clinica=nullif(current_setting('app.tenant_id',true),'')::integer)
    WITH CHECK(id_clinica=nullif(current_setting('app.tenant_id',true),'')::integer);
CREATE POLICY tenant_isolation ON mensageria_limite
    USING(id_clinica=nullif(current_setting('app.tenant_id',true),'')::integer)
    WITH CHECK(id_clinica=nullif(current_setting('app.tenant_id',true),'')::integer);
CREATE POLICY tenant_isolation ON mensageria_provedor_estado
    USING(id_clinica=nullif(current_setting('app.tenant_id',true),'')::integer)
    WITH CHECK(id_clinica=nullif(current_setting('app.tenant_id',true),'')::integer);
CREATE POLICY tenant_isolation ON mensageria_evento_recebido
    USING(id_clinica=nullif(current_setting('app.tenant_id',true),'')::integer)
    WITH CHECK(id_clinica=nullif(current_setting('app.tenant_id',true),'')::integer);

INSERT INTO auth_schema_version(version) VALUES (23) ON CONFLICT DO NOTHING;
COMMIT;
