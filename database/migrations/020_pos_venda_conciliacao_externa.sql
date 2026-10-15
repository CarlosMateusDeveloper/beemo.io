-- Issue #39: conciliação de reagendamentos externos até o comparecimento.
-- Aplicar depois de 019_pos_venda_fila_diaria.sql.
BEGIN;

ALTER TABLE pos_venda_caso
    ADD COLUMN IF NOT EXISTS id_atendimento_externo_reagendado BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid='pos_venda_caso'::regclass
          AND conname='pos_venda_caso_reagendamento_externo_fk'
    ) THEN
        ALTER TABLE pos_venda_caso ADD CONSTRAINT pos_venda_caso_reagendamento_externo_fk
            FOREIGN KEY(id_clinica,id_atendimento_externo_reagendado)
            REFERENCES integracao_atendimento_externo(id_clinica,id_atendimento_externo);
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid='pos_venda_caso'::regclass
          AND conname='pos_venda_caso_reagendamento_unico_check'
    ) THEN
        ALTER TABLE pos_venda_caso ADD CONSTRAINT pos_venda_caso_reagendamento_unico_check CHECK (
            num_nonnulls(id_consulta_reagendada,id_atendimento_externo_reagendado) <= 1
        );
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS pos_venda_atendimento_externo (
    id_atendimento_externo BIGINT PRIMARY KEY,
    id_clinica INTEGER NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer
        REFERENCES clinica(id_clinica),
    id_caso BIGINT NOT NULL,
    vinculado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(id_clinica,id_atendimento_externo),
    FOREIGN KEY(id_clinica,id_atendimento_externo)
        REFERENCES integracao_atendimento_externo(id_clinica,id_atendimento_externo),
    FOREIGN KEY(id_clinica,id_caso)
        REFERENCES pos_venda_caso(id_clinica,id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS pos_venda_atendimento_externo_caso_idx
    ON pos_venda_atendimento_externo(id_clinica,id_caso,vinculado_em DESC);

ALTER TABLE pos_venda_atendimento_externo ENABLE ROW LEVEL SECURITY;
ALTER TABLE pos_venda_atendimento_externo FORCE ROW LEVEL SECURITY;
DO $rls$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies
        WHERE schemaname=current_schema()
          AND tablename='pos_venda_atendimento_externo'
          AND policyname='tenant_isolation'
    ) THEN
        CREATE POLICY tenant_isolation ON pos_venda_atendimento_externo
            USING(id_clinica=nullif(current_setting('app.tenant_id',true),'')::integer)
            WITH CHECK(id_clinica=nullif(current_setting('app.tenant_id',true),'')::integer);
    END IF;
END $rls$;

ALTER TABLE pos_venda_alerta DROP CONSTRAINT IF EXISTS pos_venda_alerta_tipo_check;
ALTER TABLE pos_venda_alerta ADD CONSTRAINT pos_venda_alerta_tipo_check CHECK (tipo IN (
    'sem_responsavel','sem_proxima_acao','prazo_vencido','resposta_pendente',
    'conferir_presenca','revisar_recorrencia'
));

CREATE OR REPLACE FUNCTION pos_venda_validar_vinculo_externo() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    origem integracao_atendimento_externo%ROWTYPE;
    reagendamento integracao_atendimento_externo%ROWTYPE;
    caso pos_venda_caso%ROWTYPE;
BEGIN
    SELECT * INTO caso FROM pos_venda_caso WHERE id=NEW.id_caso;
    IF NOT FOUND OR caso.id_atendimento_externo_origem IS NULL THEN
        RAISE EXCEPTION 'Caso não pertence a uma fonte externa';
    END IF;
    SELECT * INTO origem FROM integracao_atendimento_externo
        WHERE id_atendimento_externo=caso.id_atendimento_externo_origem;
    SELECT * INTO reagendamento FROM integracao_atendimento_externo
        WHERE id_atendimento_externo=NEW.id_atendimento_externo;
    IF NOT FOUND THEN RAISE EXCEPTION 'Atendimento externo não encontrado'; END IF;
    IF reagendamento.id_atendimento_externo=origem.id_atendimento_externo
       OR reagendamento.id_fonte<>origem.id_fonte
       OR reagendamento.id_paciente_externo<>origem.id_paciente_externo
       OR reagendamento.tipo<>origem.tipo
       OR reagendamento.especialidade<>origem.especialidade
       OR reagendamento.agendado_em<=origem.agendado_em
       OR reagendamento.agendado_em<now()
       OR reagendamento.situacao_normalizada NOT IN ('AGENDADO','CONFIRMADO','DESCONHECIDO') THEN
        RAISE EXCEPTION 'Atendimento externo não é elegível para este caso';
    END IF;
    RETURN NEW;
END;
$$;
DROP TRIGGER IF EXISTS trg_pos_venda_validar_vinculo_externo ON pos_venda_atendimento_externo;
CREATE TRIGGER trg_pos_venda_validar_vinculo_externo
BEFORE INSERT ON pos_venda_atendimento_externo
FOR EACH ROW EXECUTE FUNCTION pos_venda_validar_vinculo_externo();

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
        -- Vínculos anteriores permanecem auditáveis, mas não sobrescrevem o
        -- desfecho da tentativa que está atualmente em acompanhamento.
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
                'importacao_csv'
            ) RETURNING id INTO novo_id;
            INSERT INTO pos_venda_evento(id_caso,tipo,descricao)
            VALUES (novo_id,'falta','Falta importada da fonte externa.');
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

INSERT INTO auth_schema_version(version) VALUES (20) ON CONFLICT DO NOTHING;
COMMIT;
