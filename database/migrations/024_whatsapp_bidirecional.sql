-- Issue #22: WhatsApp oficial, bidirecional e correlacionado ao pos-venda.
-- Aplicar depois de 023_mensageria_confiavel.sql.
BEGIN;

CREATE TABLE whatsapp_integracao (
    id_clinica INTEGER PRIMARY KEY REFERENCES clinica(id_clinica),
    provedor VARCHAR(30) NOT NULL DEFAULT 'meta' CHECK(provedor='meta'),
    webhook_key VARCHAR(64) NOT NULL UNIQUE,
    phone_number_id VARCHAR(80) NOT NULL,
    business_account_id VARCHAR(80),
    numero_exibicao VARCHAR(30),
    access_token_criptografado TEXT NOT NULL,
    app_secret_criptografado TEXT NOT NULL,
    verify_token_hash CHAR(64) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT true,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_por INTEGER,
    FOREIGN KEY(id_clinica,atualizado_por) REFERENCES tenant_membro(id_clinica,id_usuario)
);

ALTER TABLE mensageria_outbox
    ADD COLUMN id_pos_venda_caso BIGINT,
    ADD COLUMN id_jornada_inscricao BIGINT;
ALTER TABLE mensageria_outbox ADD CONSTRAINT mensageria_outbox_caso_fk
    FOREIGN KEY(id_clinica,id_pos_venda_caso) REFERENCES pos_venda_caso(id_clinica,id);
ALTER TABLE mensageria_outbox ADD CONSTRAINT mensageria_outbox_jornada_fk
    FOREIGN KEY(id_clinica,id_jornada_inscricao)
    REFERENCES pos_venda_jornada_inscricao(id_clinica,id_inscricao);

ALTER TABLE mensagem
    ADD COLUMN id_pos_venda_caso BIGINT,
    ADD COLUMN id_campanha BIGINT,
    ADD COLUMN id_jornada_inscricao BIGINT;
ALTER TABLE mensagem ADD CONSTRAINT mensagem_caso_fk
    FOREIGN KEY(id_clinica,id_pos_venda_caso) REFERENCES pos_venda_caso(id_clinica,id);
ALTER TABLE mensagem ADD CONSTRAINT mensagem_campanha_fk
    FOREIGN KEY(id_clinica,id_campanha) REFERENCES pos_venda_campanha(id_clinica,id_campanha);
ALTER TABLE mensagem ADD CONSTRAINT mensagem_jornada_fk
    FOREIGN KEY(id_clinica,id_jornada_inscricao)
    REFERENCES pos_venda_jornada_inscricao(id_clinica,id_inscricao);
ALTER TABLE mensagem DROP CONSTRAINT mensagem_status_entrega_check;
ALTER TABLE mensagem ADD CONSTRAINT mensagem_status_entrega_check
    CHECK(status_entrega IN ('pendente','enviada','entregue','lida','falha','cancelada','recebida'));

CREATE TABLE whatsapp_status_evento (
    id_evento BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_clinica INTEGER NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer
        REFERENCES clinica(id_clinica),
    id_mensagem_provedor VARCHAR(200) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK(status IN ('enviada','entregue','lida','falha')),
    ocorrido_em TIMESTAMPTZ NOT NULL,
    erro_codigo VARCHAR(80),
    erro_titulo VARCHAR(300),
    erro_detalhe VARCHAR(800),
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    recebido_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(id_clinica,id_evento),
    UNIQUE(id_clinica,id_mensagem_provedor,status,ocorrido_em)
);
CREATE INDEX whatsapp_status_mensagem_idx
    ON whatsapp_status_evento(id_clinica,id_mensagem_provedor,ocorrido_em);

CREATE TABLE whatsapp_associacao_pendente (
    id_associacao BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_clinica INTEGER NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer
        REFERENCES clinica(id_clinica),
    id_mensagem BIGINT NOT NULL,
    telefone VARCHAR(20) NOT NULL,
    casos_candidatos JSONB NOT NULL,
    status VARCHAR(12) NOT NULL DEFAULT 'pendente' CHECK(status IN ('pendente','resolvida','ignorada')),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolvido_em TIMESTAMPTZ,
    UNIQUE(id_clinica,id_associacao),
    UNIQUE(id_clinica,id_mensagem),
    FOREIGN KEY(id_clinica,id_mensagem) REFERENCES mensagem(id_clinica,id_mensagem)
);

ALTER TABLE pos_venda_tarefa ADD COLUMN id_caso BIGINT;
ALTER TABLE pos_venda_tarefa ADD CONSTRAINT pos_venda_tarefa_caso_fk
    FOREIGN KEY(id_clinica,id_caso) REFERENCES pos_venda_caso(id_clinica,id) ON DELETE CASCADE;
CREATE UNIQUE INDEX pos_venda_tarefa_whatsapp_origem_uk
    ON pos_venda_tarefa(id_clinica,origem) WHERE origem LIKE 'whatsapp_resposta:%';

CREATE INDEX mensagem_caso_idx ON mensagem(id_clinica,id_pos_venda_caso,criado_em DESC);
CREATE UNIQUE INDEX mensagem_provider_unique_idx ON mensagem(id_clinica,id_mensagem_provedor)
    WHERE id_mensagem_provedor IS NOT NULL;
CREATE INDEX outbox_caso_idx ON mensageria_outbox(id_clinica,id_pos_venda_caso,criado_em DESC);

ALTER TABLE whatsapp_integracao ENABLE ROW LEVEL SECURITY;
ALTER TABLE whatsapp_integracao FORCE ROW LEVEL SECURITY;
ALTER TABLE whatsapp_status_evento ENABLE ROW LEVEL SECURITY;
ALTER TABLE whatsapp_status_evento FORCE ROW LEVEL SECURITY;
ALTER TABLE whatsapp_associacao_pendente ENABLE ROW LEVEL SECURITY;
ALTER TABLE whatsapp_associacao_pendente FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON whatsapp_integracao
    USING(id_clinica=nullif(current_setting('app.tenant_id',true),'')::integer)
    WITH CHECK(id_clinica=nullif(current_setting('app.tenant_id',true),'')::integer);
CREATE POLICY tenant_isolation ON whatsapp_status_evento
    USING(id_clinica=nullif(current_setting('app.tenant_id',true),'')::integer)
    WITH CHECK(id_clinica=nullif(current_setting('app.tenant_id',true),'')::integer);
CREATE POLICY tenant_isolation ON whatsapp_associacao_pendente
    USING(id_clinica=nullif(current_setting('app.tenant_id',true),'')::integer)
    WITH CHECK(id_clinica=nullif(current_setting('app.tenant_id',true),'')::integer);

INSERT INTO auth_schema_version(version) VALUES (24) ON CONFLICT DO NOTHING;
COMMIT;
