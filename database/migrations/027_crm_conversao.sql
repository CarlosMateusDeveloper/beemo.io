-- Issue #26: CRM do primeiro contato ao agendamento real.
-- Aplicar depois de 026_onboarding_clinicas.sql.
BEGIN;

CREATE TABLE crm_interessado (
    id_interessado BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_clinica INTEGER NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer REFERENCES clinica(id_clinica),
    nome VARCHAR(160) NOT NULL,
    telefone VARCHAR(20),
    email VARCHAR(180),
    canal_preferido VARCHAR(20) NOT NULL DEFAULT 'whatsapp'
        CHECK(canal_preferido IN ('whatsapp','telefone','email','presencial')),
    contato_permitido BOOLEAN NOT NULL DEFAULT true,
    finalidade VARCHAR(20) NOT NULL DEFAULT 'atendimento' CHECK(finalidade='atendimento'),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(id_clinica,id_interessado),
    CHECK(telefone IS NOT NULL OR email IS NOT NULL)
);

CREATE TABLE crm_oportunidade (
    id_oportunidade BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_clinica INTEGER NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer REFERENCES clinica(id_clinica),
    id_interessado BIGINT NOT NULL,
    canal_origem VARCHAR(20) NOT NULL CHECK(canal_origem IN ('whatsapp','telefone','site','indicacao','instagram','presencial','outro')),
    origem VARCHAR(120) NOT NULL,
    servico_interesse VARCHAR(180) NOT NULL,
    id_responsavel INTEGER,
    etapa VARCHAR(30) NOT NULL DEFAULT 'novo'
        CHECK(etapa IN ('novo','em_contato','qualificado','aguardando_agendamento','agendado','perdido')),
    proxima_acao TIMESTAMPTZ,
    orcamento NUMERIC(12,2) CHECK(orcamento IS NULL OR orcamento >= 0),
    motivo_perda VARCHAR(300),
    idempotency_key VARCHAR(180),
    conversa_externa_id VARCHAR(180),
    primeiro_contato_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    primeira_resposta_automatica_em TIMESTAMPTZ,
    primeira_resposta_humana_em TIMESTAMPTZ,
    id_paciente INTEGER,
    id_consulta INTEGER,
    criado_por INTEGER,
    atualizado_por INTEGER,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    versao INTEGER NOT NULL DEFAULT 0,
    UNIQUE(id_clinica,id_oportunidade),
    FOREIGN KEY(id_clinica,id_interessado) REFERENCES crm_interessado(id_clinica,id_interessado),
    FOREIGN KEY(id_clinica,id_responsavel) REFERENCES tenant_membro(id_clinica,id_usuario),
    FOREIGN KEY(id_clinica,criado_por) REFERENCES tenant_membro(id_clinica,id_usuario),
    FOREIGN KEY(id_clinica,atualizado_por) REFERENCES tenant_membro(id_clinica,id_usuario),
    FOREIGN KEY(id_clinica,id_paciente) REFERENCES paciente(id_clinica,id_paciente),
    FOREIGN KEY(id_clinica,id_consulta) REFERENCES consulta(id_clinica,id_consulta),
    CHECK((etapa='perdido' AND nullif(btrim(motivo_perda),'') IS NOT NULL) OR (etapa<>'perdido' AND motivo_perda IS NULL)),
    CHECK((etapa='agendado' AND id_paciente IS NOT NULL AND id_consulta IS NOT NULL) OR etapa<>'agendado')
);
CREATE UNIQUE INDEX crm_oportunidade_idempotencia_uk ON crm_oportunidade(id_clinica,idempotency_key)
    WHERE idempotency_key IS NOT NULL;
CREATE UNIQUE INDEX crm_oportunidade_conversa_aberta_uk ON crm_oportunidade(id_clinica,conversa_externa_id)
    WHERE conversa_externa_id IS NOT NULL AND etapa NOT IN ('agendado','perdido');
CREATE INDEX crm_oportunidade_fila_idx ON crm_oportunidade(id_clinica,etapa,proxima_acao,id_responsavel);
CREATE INDEX crm_interessado_telefone_idx ON crm_interessado(id_clinica,telefone);
CREATE INDEX crm_interessado_email_idx ON crm_interessado(id_clinica,email);

CREATE TABLE crm_oportunidade_evento (
    id_evento BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_clinica INTEGER NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer REFERENCES clinica(id_clinica),
    id_oportunidade BIGINT NOT NULL,
    tipo VARCHAR(30) NOT NULL CHECK(tipo IN ('criada','atualizada','etapa','mensagem_entrada','resposta_automatica','resposta_humana','transferencia','conversao','cancelamento','reagendamento')),
    etapa_anterior VARCHAR(30),
    etapa_nova VARCHAR(30),
    id_usuario INTEGER,
    automatico BOOLEAN NOT NULL DEFAULT false,
    id_mensagem BIGINT,
    dados JSONB NOT NULL DEFAULT '{}'::jsonb,
    ocorrido_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(id_clinica,id_evento),
    FOREIGN KEY(id_clinica,id_oportunidade) REFERENCES crm_oportunidade(id_clinica,id_oportunidade) ON DELETE CASCADE,
    FOREIGN KEY(id_clinica,id_usuario) REFERENCES tenant_membro(id_clinica,id_usuario),
    FOREIGN KEY(id_clinica,id_mensagem) REFERENCES mensagem(id_clinica,id_mensagem)
);
CREATE INDEX crm_evento_oportunidade_idx ON crm_oportunidade_evento(id_clinica,id_oportunidade,ocorrido_em,id_evento);
CREATE UNIQUE INDEX crm_evento_mensagem_tipo_uk ON crm_oportunidade_evento(id_clinica,id_mensagem,tipo)
    WHERE id_mensagem IS NOT NULL;

CREATE TABLE crm_identidade_revisao (
    id_revisao BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_clinica INTEGER NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer REFERENCES clinica(id_clinica),
    id_oportunidade BIGINT NOT NULL,
    motivo VARCHAR(40) NOT NULL CHECK(motivo IN ('telefone_compartilhado','email_duplicado','cpf_existente','identidade_ambigua')),
    candidatos JSONB NOT NULL DEFAULT '[]'::jsonb,
    status VARCHAR(12) NOT NULL DEFAULT 'pendente' CHECK(status IN ('pendente','resolvida','ignorada')),
    resolvido_por INTEGER,
    resolvido_em TIMESTAMPTZ,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(id_clinica,id_revisao),
    FOREIGN KEY(id_clinica,id_oportunidade) REFERENCES crm_oportunidade(id_clinica,id_oportunidade) ON DELETE CASCADE,
    FOREIGN KEY(id_clinica,resolvido_por) REFERENCES tenant_membro(id_clinica,id_usuario)
);
CREATE UNIQUE INDEX crm_revisao_pendente_uk ON crm_identidade_revisao(id_clinica,id_oportunidade,motivo)
    WHERE status='pendente';

ALTER TABLE mensagem ADD COLUMN id_crm_oportunidade BIGINT;
ALTER TABLE mensagem ADD CONSTRAINT mensagem_crm_oportunidade_fk
    FOREIGN KEY(id_clinica,id_crm_oportunidade) REFERENCES crm_oportunidade(id_clinica,id_oportunidade);
CREATE INDEX mensagem_crm_oportunidade_idx ON mensagem(id_clinica,id_crm_oportunidade,criado_em);

CREATE OR REPLACE FUNCTION crm_versionar_oportunidade() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    NEW.versao := OLD.versao + 1;
    NEW.atualizado_em := now();
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_crm_oportunidade_versionar BEFORE UPDATE ON crm_oportunidade
    FOR EACH ROW EXECUTE FUNCTION crm_versionar_oportunidade();

CREATE OR REPLACE FUNCTION crm_registrar_agendamento() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE oportunidade BIGINT;
BEGIN
    SELECT id_oportunidade INTO oportunidade FROM crm_oportunidade
      WHERE id_consulta=NEW.id_consulta LIMIT 1;
    IF oportunidade IS NULL THEN RETURN NEW; END IF;
    IF OLD.id_agenda IS DISTINCT FROM NEW.id_agenda THEN
        INSERT INTO crm_oportunidade_evento(id_oportunidade,tipo,automatico,dados)
        VALUES(oportunidade,'reagendamento',true,jsonb_build_object('agendaAnterior',OLD.id_agenda,'agendaNova',NEW.id_agenda));
    END IF;
    IF OLD.status_consulta IS DISTINCT FROM NEW.status_consulta AND NEW.status_consulta='Cancelada' THEN
        INSERT INTO crm_oportunidade_evento(id_oportunidade,tipo,automatico,dados)
        VALUES(oportunidade,'cancelamento',true,jsonb_build_object('consultaId',NEW.id_consulta));
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_crm_consulta_rastrear AFTER UPDATE OF id_agenda,status_consulta ON consulta
    FOR EACH ROW EXECUTE FUNCTION crm_registrar_agendamento();

CREATE OR REPLACE FUNCTION crm_bloquear_evento_mutacao() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Histórico do CRM é imutável'; END;
$$;
CREATE TRIGGER trg_crm_evento_imutavel BEFORE UPDATE OR DELETE ON crm_oportunidade_evento
    FOR EACH ROW EXECUTE FUNCTION crm_bloquear_evento_mutacao();

DO $rls$
DECLARE tabela TEXT;
BEGIN
    FOREACH tabela IN ARRAY ARRAY['crm_interessado','crm_oportunidade','crm_oportunidade_evento','crm_identidade_revisao'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',tabela);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY',tabela);
        EXECUTE format('CREATE POLICY tenant_isolation ON %I USING(id_clinica=nullif(current_setting(''app.tenant_id'',true),'''')::integer) WITH CHECK(id_clinica=nullif(current_setting(''app.tenant_id'',true),'''')::integer)',tabela);
    END LOOP;
END $rls$;

INSERT INTO auth_schema_version(version) VALUES(27);
COMMIT;
