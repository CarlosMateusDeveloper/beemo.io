-- Pós-venda por atendimento. Execute após 007_retorno_pacientes.sql.
BEGIN;

CREATE TABLE IF NOT EXISTS pos_venda_caso (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_consulta_origem INT NOT NULL UNIQUE REFERENCES consulta(id_consulta),
    id_consulta_reagendada INT REFERENCES consulta(id_consulta),
    data_falta TIMESTAMP NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'pendente'
        CHECK (status IN ('pendente','em_contato','sem_resposta','reagendado','nova_falta',
                          'recuperado','nao_deseja','nao_contatar','registro_corrigido')),
    id_responsavel INT REFERENCES usuario(id),
    proxima_acao TIMESTAMPTZ,
    motivo_falta VARCHAR(500),
    recorrente BOOLEAN NOT NULL DEFAULT FALSE,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    versao INT NOT NULL DEFAULT 0
);

-- Uma consulta só pertence a um caso, inclusive após várias novas faltas.
CREATE TABLE IF NOT EXISTS pos_venda_consulta (
    id_consulta INT PRIMARY KEY REFERENCES consulta(id_consulta),
    id_caso BIGINT NOT NULL REFERENCES pos_venda_caso(id),
    vinculado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS pos_venda_evento (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_caso BIGINT NOT NULL REFERENCES pos_venda_caso(id),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    id_usuario INT REFERENCES usuario(id),
    tipo VARCHAR(40) NOT NULL,
    canal VARCHAR(30),
    descricao VARCHAR(2000) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_pos_venda_fila ON pos_venda_caso(status, proxima_acao);
CREATE INDEX IF NOT EXISTS idx_pos_venda_eventos ON pos_venda_evento(id_caso, id);

CREATE OR REPLACE FUNCTION pos_venda_versionar() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    NEW.versao := OLD.versao + 1;
    NEW.atualizado_em := now();
    RETURN NEW;
END;
$$;
DROP TRIGGER IF EXISTS trg_pos_venda_versao ON pos_venda_caso;
CREATE TRIGGER trg_pos_venda_versao BEFORE UPDATE ON pos_venda_caso
FOR EACH ROW EXECUTE FUNCTION pos_venda_versionar();

-- O banco recebe alterações tanto da Agenda (Go) quanto do backend Java.
-- Assim o acompanhamento não depende de manter a tela aberta.
CREATE OR REPLACE FUNCTION pos_venda_acompanhar_consulta() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    caso pos_venda_caso%ROWTYPE;
    novo_status TEXT;
    novo_id BIGINT;
BEGIN
    IF TG_OP = 'UPDATE' THEN
        IF NEW.id_paciente IS DISTINCT FROM OLD.id_paciente AND EXISTS (
            SELECT 1 FROM pos_venda_consulta WHERE id_consulta = NEW.id_consulta
        ) THEN
            RAISE EXCEPTION 'Consulta vinculada ao pós-venda: não é possível trocar o paciente';
        END IF;
        IF NEW.status_consulta IS NOT DISTINCT FROM OLD.status_consulta THEN RETURN NEW; END IF;
    END IF;

    SELECT c.* INTO caso FROM pos_venda_caso c
    JOIN pos_venda_consulta v ON v.id_caso = c.id
    WHERE v.id_consulta = NEW.id_consulta FOR UPDATE OF c;

    IF NOT FOUND THEN
        IF NEW.status_consulta = 'Faltou' THEN
            INSERT INTO pos_venda_caso(id_consulta_origem, data_falta, status)
            SELECT NEW.id_consulta, a.data_slot + a.hora_slot,
                CASE WHEN EXISTS (SELECT 1 FROM paciente_retorno_status
                    WHERE id_paciente = NEW.id_paciente AND status = 'nao_contatar')
                    THEN 'nao_contatar' ELSE 'pendente' END
            FROM agenda a WHERE a.id_agenda = NEW.id_agenda
            RETURNING id INTO novo_id;
            INSERT INTO pos_venda_consulta(id_consulta, id_caso) VALUES (NEW.id_consulta, novo_id);
            INSERT INTO pos_venda_evento(id_caso,tipo,descricao)
            VALUES (novo_id,'falta','Falta registrada na agenda. Caso criado para acompanhamento.');
        END IF;
        RETURN NEW;
    END IF;

    -- Registra inclusive novas faltas de tentativas antigas, sem duplicar casos.
    INSERT INTO pos_venda_evento(id_caso,tipo,descricao)
    VALUES (caso.id,'agenda','Consulta #' || NEW.id_consulta || ': ' || NEW.status_consulta::text);

    IF caso.status IN ('nao_deseja','nao_contatar') THEN RETURN NEW; END IF;
    IF caso.id_consulta_reagendada = NEW.id_consulta THEN
        novo_status := CASE NEW.status_consulta::text
            WHEN 'Realizada' THEN 'recuperado'
            WHEN 'Faltou' THEN 'nova_falta'
            WHEN 'Cancelada' THEN 'pendente'
            ELSE 'reagendado' END;
        UPDATE pos_venda_caso SET status = novo_status,
            proxima_acao = CASE WHEN novo_status IN ('nova_falta','pendente') THEN now() ELSE NULL END
        WHERE id = caso.id;
    ELSIF caso.id_consulta_origem = NEW.id_consulta AND caso.id_consulta_reagendada IS NULL THEN
        UPDATE pos_venda_caso SET
            status = CASE WHEN NEW.status_consulta = 'Faltou' THEN 'pendente' ELSE 'registro_corrigido' END,
            proxima_acao = NULL
        WHERE id = caso.id;
    END IF;
    RETURN NEW;
END;
$$;
DROP TRIGGER IF EXISTS trg_pos_venda_consulta ON consulta;
CREATE TRIGGER trg_pos_venda_consulta AFTER INSERT OR UPDATE ON consulta
FOR EACH ROW EXECUTE FUNCTION pos_venda_acompanhar_consulta();

CREATE OR REPLACE FUNCTION pos_venda_respeitar_contato() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.status = 'nao_contatar' THEN
        WITH encerrados AS (
            UPDATE pos_venda_caso pv SET status = 'nao_contatar', proxima_acao = NULL
            FROM consulta c WHERE c.id_consulta = pv.id_consulta_origem AND c.id_paciente = NEW.id_paciente
                AND pv.status NOT IN ('recuperado','nao_deseja','nao_contatar','registro_corrigido')
            RETURNING pv.id
        )
        INSERT INTO pos_venda_evento(id_caso,tipo,descricao)
        SELECT id,'nao_contatar','Acompanhamento encerrado: paciente pediu para não receber contatos.' FROM encerrados;
    END IF;
    RETURN NEW;
END;
$$;
DROP TRIGGER IF EXISTS trg_pos_venda_preferencia ON paciente_retorno_status;
CREATE TRIGGER trg_pos_venda_preferencia AFTER INSERT OR UPDATE ON paciente_retorno_status
FOR EACH ROW EXECUTE FUNCTION pos_venda_respeitar_contato();

-- Inclui faltas anteriores à instalação, de forma idempotente.
WITH novos AS (
    INSERT INTO pos_venda_caso(id_consulta_origem, data_falta, status)
    SELECT c.id_consulta, a.data_slot + a.hora_slot,
        CASE WHEN prs.status = 'nao_contatar' THEN 'nao_contatar' ELSE 'pendente' END
    FROM consulta c JOIN agenda a ON a.id_agenda = c.id_agenda
    LEFT JOIN paciente_retorno_status prs ON prs.id_paciente = c.id_paciente
    WHERE c.status_consulta = 'Faltou'
      AND NOT EXISTS (SELECT 1 FROM pos_venda_consulta v WHERE v.id_consulta = c.id_consulta)
    ON CONFLICT (id_consulta_origem) DO NOTHING
    RETURNING id, id_consulta_origem
), vinculos AS (
    INSERT INTO pos_venda_consulta(id_consulta,id_caso)
    SELECT id_consulta_origem,id FROM novos RETURNING id_caso
)
INSERT INTO pos_venda_evento(id_caso,tipo,descricao)
SELECT id_caso,'falta','Falta anterior incluída no acompanhamento.' FROM vinculos;
COMMIT;
