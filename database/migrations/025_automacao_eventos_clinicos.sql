-- Issue #20: automacao idempotente baseada em eventos da agenda e do prontuario.
-- Aplicar depois de 024_whatsapp_bidirecional.sql.
BEGIN;

ALTER TABLE pos_venda_jornada_inscricao
    DROP CONSTRAINT IF EXISTS pos_venda_jornada_inscricao_status_check;
ALTER TABLE pos_venda_jornada_inscricao
    ADD CONSTRAINT pos_venda_jornada_inscricao_status_check
    CHECK(status IN ('ativa','pausada','concluida','cancelada'));

CREATE TABLE pos_venda_evento_clinico (
    id_evento BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_clinica INTEGER NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer
        REFERENCES clinica(id_clinica),
    identificador VARCHAR(240) NOT NULL,
    tipo VARCHAR(60) NOT NULL CHECK(tipo IN (
        'CONSULTA_CRIADA','CONSULTA_CONFIRMADA','CONSULTA_CANCELADA','CONSULTA_FALTOU','CONSULTA_REALIZADA',
        'PRONTUARIO_PROCEDIMENTO_CONCLUIDO','PRONTUARIO_RETORNO_INDICADO'
    )),
    versao SMALLINT NOT NULL DEFAULT 1 CHECK(versao=1),
    ocorrido_em TIMESTAMPTZ NOT NULL,
    origem_tipo VARCHAR(30) NOT NULL CHECK(origem_tipo IN ('consulta','prontuario')),
    origem_id VARCHAR(120) NOT NULL,
    origem_versao BIGINT NOT NULL CHECK(origem_versao>0),
    id_paciente INTEGER,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(15) NOT NULL DEFAULT 'pendente'
        CHECK(status IN ('pendente','processando','processado','ignorado','erro')),
    tentativas INTEGER NOT NULL DEFAULT 0,
    erro VARCHAR(1000),
    recebido_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    processado_em TIMESTAMPTZ,
    UNIQUE(id_clinica,id_evento),
    UNIQUE(id_clinica,identificador),
    UNIQUE(id_clinica,origem_tipo,origem_id,tipo,origem_versao),
    FOREIGN KEY(id_clinica,id_paciente) REFERENCES paciente(id_clinica,id_paciente)
);

CREATE TABLE pos_venda_automacao_regra (
    id_regra BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_clinica INTEGER NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer
        REFERENCES clinica(id_clinica),
    nome VARCHAR(160) NOT NULL,
    evento_tipo VARCHAR(60) NOT NULL CHECK(evento_tipo IN (
        'CONSULTA_CRIADA','CONSULTA_CONFIRMADA','CONSULTA_CANCELADA','CONSULTA_FALTOU','CONSULTA_REALIZADA',
        'PRONTUARIO_PROCEDIMENTO_CONCLUIDO','PRONTUARIO_RETORNO_INDICADO'
    )),
    acao VARCHAR(30) NOT NULL CHECK(acao IN (
        'criar_oportunidade','atualizar_oportunidade','criar_tarefa','inscrever_jornada',
        'avancar_jornada','pausar_jornada','concluir_jornada'
    )),
    id_jornada BIGINT,
    id_responsavel INTEGER,
    configuracao JSONB NOT NULL DEFAULT '{}'::jsonb,
    prioridade SMALLINT NOT NULL DEFAULT 100 CHECK(prioridade BETWEEN 0 AND 1000),
    ativa BOOLEAN NOT NULL DEFAULT true,
    criado_por INTEGER NOT NULL,
    atualizado_por INTEGER NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    versao INTEGER NOT NULL DEFAULT 0,
    UNIQUE(id_clinica,id_regra),
    UNIQUE(id_clinica,nome),
    CHECK((acao='inscrever_jornada' AND id_jornada IS NOT NULL) OR acao<>'inscrever_jornada'),
    FOREIGN KEY(id_clinica,id_jornada) REFERENCES pos_venda_jornada(id_clinica,id_jornada),
    FOREIGN KEY(id_clinica,id_responsavel) REFERENCES tenant_membro(id_clinica,id_usuario),
    FOREIGN KEY(id_clinica,criado_por) REFERENCES tenant_membro(id_clinica,id_usuario),
    FOREIGN KEY(id_clinica,atualizado_por) REFERENCES tenant_membro(id_clinica,id_usuario)
);

CREATE TABLE pos_venda_automacao_execucao (
    id_execucao BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_clinica INTEGER NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer
        REFERENCES clinica(id_clinica),
    id_evento BIGINT NOT NULL,
    id_regra BIGINT NOT NULL,
    status VARCHAR(15) NOT NULL CHECK(status IN ('processando','executada','bloqueada','ignorada','erro')),
    tentativas INTEGER NOT NULL DEFAULT 1,
    explicacao VARCHAR(1000),
    id_caso BIGINT,
    id_tarefa BIGINT,
    id_inscricao BIGINT,
    erro VARCHAR(1000),
    iniciado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    concluido_em TIMESTAMPTZ,
    UNIQUE(id_clinica,id_execucao),
    UNIQUE(id_clinica,id_evento,id_regra),
    FOREIGN KEY(id_clinica,id_evento) REFERENCES pos_venda_evento_clinico(id_clinica,id_evento) ON DELETE CASCADE,
    FOREIGN KEY(id_clinica,id_regra) REFERENCES pos_venda_automacao_regra(id_clinica,id_regra),
    FOREIGN KEY(id_clinica,id_caso) REFERENCES pos_venda_caso(id_clinica,id),
    FOREIGN KEY(id_clinica,id_tarefa) REFERENCES pos_venda_tarefa(id_clinica,id_tarefa),
    FOREIGN KEY(id_clinica,id_inscricao) REFERENCES pos_venda_jornada_inscricao(id_clinica,id_inscricao)
);

CREATE INDEX pos_venda_evento_clinico_fila_idx
    ON pos_venda_evento_clinico(id_clinica,status,id_evento);
CREATE INDEX pos_venda_evento_clinico_origem_idx
    ON pos_venda_evento_clinico(id_clinica,origem_tipo,origem_id,origem_versao DESC);
CREATE INDEX pos_venda_automacao_regra_evento_idx
    ON pos_venda_automacao_regra(id_clinica,evento_tipo,ativa,prioridade,id_regra);
CREATE INDEX pos_venda_automacao_execucao_evento_idx
    ON pos_venda_automacao_execucao(id_clinica,id_evento,id_execucao);
CREATE UNIQUE INDEX pos_venda_tarefa_automacao_origem_uk
    ON pos_venda_tarefa(id_clinica,origem) WHERE origem LIKE 'automacao:%';

CREATE OR REPLACE FUNCTION pos_venda_automacao_regra_versionar() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    NEW.versao := OLD.versao + 1;
    NEW.atualizado_em := now();
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_pos_venda_automacao_regra_versionar
BEFORE UPDATE ON pos_venda_automacao_regra
FOR EACH ROW EXECUTE FUNCTION pos_venda_automacao_regra_versionar();

CREATE OR REPLACE FUNCTION pos_venda_processar_evento(p_id_evento BIGINT) RETURNS VOID LANGUAGE plpgsql AS $$
DECLARE
    evento pos_venda_evento_clinico%ROWTYPE;
    regra pos_venda_automacao_regra%ROWTYPE;
    execucao_id BIGINT;
    caso_id BIGINT;
    tarefa_id BIGINT;
    inscricao_id BIGINT;
    ator_id INTEGER;
    jornada_criador INTEGER;
    paciente_nome TEXT;
    prioridade_tarefa TEXT;
    titulo_tarefa TEXT;
    descricao_tarefa TEXT;
    bloqueio TEXT;
    afetados INTEGER;
    houve_erro BOOLEAN := false;
BEGIN
    SELECT * INTO evento FROM pos_venda_evento_clinico WHERE id_evento=p_id_evento FOR UPDATE;
    IF NOT FOUND OR evento.status='processado' THEN RETURN; END IF;

    UPDATE pos_venda_evento_clinico
       SET status='processando',tentativas=tentativas+1,erro=NULL
     WHERE id_evento=evento.id_evento;

    IF EXISTS (
        SELECT 1 FROM pos_venda_evento_clinico posterior
        WHERE posterior.origem_tipo=evento.origem_tipo AND posterior.origem_id=evento.origem_id
          AND posterior.origem_versao>evento.origem_versao
          AND posterior.status IN ('processado','processando')
    ) THEN
        UPDATE pos_venda_evento_clinico SET status='ignorado',processado_em=now(),
            erro='Evento anterior a uma versão já processada.' WHERE id_evento=evento.id_evento;
        RETURN;
    END IF;

    SELECT p.nome INTO paciente_nome FROM paciente p WHERE p.id_paciente=evento.id_paciente;
    FOR regra IN
        SELECT * FROM pos_venda_automacao_regra
        WHERE ativa AND evento_tipo=evento.tipo ORDER BY prioridade,id_regra
    LOOP
        INSERT INTO pos_venda_automacao_execucao(id_evento,id_regra,status)
        VALUES (evento.id_evento,regra.id_regra,'processando')
        ON CONFLICT(id_clinica,id_evento,id_regra) DO UPDATE
          SET status='processando',tentativas=pos_venda_automacao_execucao.tentativas+1,
              erro=NULL,iniciado_em=now(),concluido_em=NULL
          WHERE pos_venda_automacao_execucao.status='erro'
        RETURNING id_execucao INTO execucao_id;
        IF execucao_id IS NULL THEN CONTINUE; END IF;

        BEGIN
            bloqueio := NULL;
            caso_id := NULL; tarefa_id := NULL; inscricao_id := NULL;
            ator_id := coalesce(regra.id_responsavel,(
                SELECT tm.id_usuario FROM tenant_membro tm WHERE tm.ativo
                ORDER BY CASE tm.perfil WHEN 'administrador' THEN 0 ELSE 1 END,tm.id_usuario LIMIT 1
            ));
            IF evento.id_paciente IS NOT NULL AND EXISTS (
                SELECT 1 FROM paciente_retorno_status prs
                WHERE prs.id_paciente=evento.id_paciente AND prs.status='nao_contatar'
            ) AND regra.acao IN ('criar_tarefa','inscrever_jornada') THEN
                bloqueio := 'Paciente marcado como não contatar; ação incompatível não executada.';
            END IF;

            IF bloqueio IS NOT NULL THEN
                UPDATE pos_venda_automacao_execucao SET status='bloqueada',explicacao=bloqueio,
                    concluido_em=now() WHERE id_execucao=execucao_id;

            ELSIF regra.acao='criar_oportunidade' THEN
                IF evento.origem_tipo<>'consulta' OR evento.tipo<>'CONSULTA_FALTOU' THEN
                    UPDATE pos_venda_automacao_execucao SET status='ignorada',
                        explicacao='A ação exige um evento de falta da agenda.',concluido_em=now()
                    WHERE id_execucao=execucao_id;
                ELSE
                    SELECT v.id_caso INTO caso_id FROM pos_venda_consulta v
                    WHERE v.id_consulta=evento.origem_id::integer;
                    IF caso_id IS NULL THEN
                        INSERT INTO pos_venda_caso(id_consulta_origem,data_falta,status,origem)
                        SELECT c.id_consulta,a.data_slot+a.hora_slot,
                            CASE WHEN EXISTS(SELECT 1 FROM paciente_retorno_status prs
                              WHERE prs.id_paciente=c.id_paciente AND prs.status='nao_contatar')
                              THEN 'nao_contatar' ELSE 'pendente' END,'agenda_interna'
                        FROM consulta c JOIN agenda a ON a.id_agenda=c.id_agenda
                        WHERE c.id_consulta=evento.origem_id::integer
                        ON CONFLICT(id_clinica,id_consulta_origem) DO NOTHING RETURNING id INTO caso_id;
                        IF caso_id IS NULL THEN
                            SELECT id INTO caso_id FROM pos_venda_caso
                            WHERE id_consulta_origem=evento.origem_id::integer;
                        END IF;
                        INSERT INTO pos_venda_consulta(id_consulta,id_caso)
                        VALUES (evento.origem_id::integer,caso_id) ON CONFLICT DO NOTHING;
                    ELSE
                        UPDATE pos_venda_caso SET
                            status=CASE WHEN status IN ('nao_deseja','nao_contatar') THEN status
                              WHEN id_consulta_reagendada=evento.origem_id::integer THEN 'nova_falta'
                              ELSE 'pendente' END,
                            recorrente=recorrente OR id_consulta_reagendada=evento.origem_id::integer,
                            proxima_acao=CASE WHEN status IN ('nao_deseja','nao_contatar') THEN NULL ELSE now() END
                        WHERE id=caso_id;
                    END IF;
                    INSERT INTO pos_venda_evento(id_caso,tipo,descricao)
                    SELECT caso_id,'automacao','Regra "' || regra.nome || '" executada pelo evento ' || evento.identificador || '.'
                    WHERE NOT EXISTS(SELECT 1 FROM pos_venda_evento
                      WHERE id_caso=caso_id AND descricao LIKE '%' || evento.identificador || '%');
                    UPDATE pos_venda_automacao_execucao SET status='executada',id_caso=caso_id,
                        explicacao='A oportunidade foi criada ou atualizada pela regra e pelo evento informados.',
                        concluido_em=now() WHERE id_execucao=execucao_id;
                END IF;

            ELSIF regra.acao='atualizar_oportunidade' THEN
                IF evento.origem_tipo<>'consulta' THEN
                    UPDATE pos_venda_automacao_execucao SET status='ignorada',
                        explicacao='Nenhuma consulta foi informada para atualizar a oportunidade.',concluido_em=now()
                    WHERE id_execucao=execucao_id;
                ELSE
                    SELECT v.id_caso INTO caso_id FROM pos_venda_consulta v
                    WHERE v.id_consulta=evento.origem_id::integer;
                    IF caso_id IS NULL THEN
                        UPDATE pos_venda_automacao_execucao SET status='ignorada',
                            explicacao='A consulta ainda não pertence a uma oportunidade.',concluido_em=now()
                        WHERE id_execucao=execucao_id;
                    ELSE
                        UPDATE pos_venda_caso SET
                            status=CASE
                              WHEN status IN ('nao_deseja','nao_contatar') THEN status
                              WHEN id_consulta_reagendada=evento.origem_id::integer THEN CASE
                                WHEN evento.tipo='CONSULTA_REALIZADA' THEN 'recuperado'
                                WHEN evento.tipo='CONSULTA_FALTOU' THEN 'nova_falta'
                                WHEN evento.tipo='CONSULTA_CANCELADA' THEN 'pendente'
                                ELSE 'reagendado' END
                              WHEN id_consulta_origem=evento.origem_id::integer AND id_consulta_reagendada IS NULL
                                THEN CASE WHEN evento.tipo='CONSULTA_FALTOU' THEN 'pendente' ELSE 'registro_corrigido' END
                              ELSE status END,
                            recorrente=recorrente OR (
                              evento.tipo='CONSULTA_FALTOU' AND id_consulta_reagendada=evento.origem_id::integer
                            ),
                            proxima_acao=CASE
                              WHEN id_consulta_reagendada=evento.origem_id::integer
                                AND evento.tipo IN ('CONSULTA_FALTOU','CONSULTA_CANCELADA')
                                AND status NOT IN ('nao_deseja','nao_contatar') THEN now()
                              WHEN id_consulta_origem=evento.origem_id::integer AND id_consulta_reagendada IS NULL THEN NULL
                              WHEN evento.tipo IN ('CONSULTA_REALIZADA','CONSULTA_CONFIRMADA','CONSULTA_CRIADA') THEN NULL
                              ELSE proxima_acao END
                        WHERE id=caso_id;
                        INSERT INTO pos_venda_evento(id_caso,tipo,descricao)
                        VALUES (caso_id,'automacao','Regra "' || regra.nome || '" aplicou ' || evento.tipo ||
                            ' a partir do evento ' || evento.identificador || '.');
                        UPDATE pos_venda_automacao_execucao SET status='executada',id_caso=caso_id,
                            explicacao='O estado da oportunidade foi atualizado conforme o evento da agenda.',
                            concluido_em=now() WHERE id_execucao=execucao_id;
                    END IF;
                END IF;

            ELSIF regra.acao='criar_tarefa' THEN
                IF ator_id IS NULL THEN RAISE EXCEPTION 'Nenhum responsável ativo está disponível para a tarefa'; END IF;
                prioridade_tarefa := coalesce(nullif(regra.configuracao->>'prioridade',''),'alta');
                IF prioridade_tarefa NOT IN ('alta','media','baixa') THEN prioridade_tarefa := 'alta'; END IF;
                titulo_tarefa := coalesce(nullif(regra.configuracao->>'titulo',''),'Contatar paciente após falta');
                descricao_tarefa := coalesce(nullif(regra.configuracao->>'descricao',''),
                    'Tarefa criada automaticamente pela regra "' || regra.nome || '" e evento ' || evento.identificador || '.');
                INSERT INTO pos_venda_tarefa(id_paciente,paciente_referencia,titulo,descricao,id_responsavel,
                    prioridade,prazo,origem,criado_por,atualizado_por)
                VALUES (evento.id_paciente,paciente_nome,titulo_tarefa,descricao_tarefa,ator_id,prioridade_tarefa,
                    now()+make_interval(hours=>coalesce((regra.configuracao->>'prazoHoras')::integer,0)),
                    'automacao:' || evento.id_evento || ':' || regra.id_regra,ator_id,ator_id)
                ON CONFLICT(id_clinica,origem) WHERE origem LIKE 'automacao:%' DO NOTHING
                RETURNING id_tarefa INTO tarefa_id;
                IF tarefa_id IS NULL THEN SELECT id_tarefa INTO tarefa_id FROM pos_venda_tarefa
                    WHERE origem='automacao:' || evento.id_evento || ':' || regra.id_regra; END IF;
                UPDATE pos_venda_automacao_execucao SET status='executada',id_tarefa=tarefa_id,
                    explicacao='A tarefa foi criada para o responsável configurado pela regra.',
                    concluido_em=now() WHERE id_execucao=execucao_id;

            ELSIF regra.acao='inscrever_jornada' THEN
                SELECT criado_por INTO jornada_criador FROM pos_venda_jornada
                WHERE id_jornada=regra.id_jornada AND status='ativa';
                IF jornada_criador IS NULL THEN
                    UPDATE pos_venda_automacao_execucao SET status='bloqueada',
                        explicacao='A jornada configurada não está ativa.',concluido_em=now()
                    WHERE id_execucao=execucao_id;
                ELSIF evento.id_paciente IS NULL THEN
                    UPDATE pos_venda_automacao_execucao SET status='bloqueada',
                        explicacao='O evento não possui paciente para inscrição.',concluido_em=now()
                    WHERE id_execucao=execucao_id;
                ELSIF EXISTS (
                    SELECT 1 FROM (SELECT DISTINCT canal FROM pos_venda_jornada_etapa
                      WHERE id_jornada=regra.id_jornada AND canal IN ('whatsapp','email')) canal
                    WHERE coalesce((SELECT c.decisao FROM comunicacao_consentimento_evento c
                      WHERE c.id_paciente=evento.id_paciente AND c.canal IN (canal.canal,'todos')
                        AND c.finalidade IN ('pos_venda','todos')
                      ORDER BY c.ocorrido_em DESC,c.id_consentimento DESC LIMIT 1),'revogado')<>'concedido'
                ) THEN
                    UPDATE pos_venda_automacao_execucao SET status='bloqueada',
                        explicacao='Consentimento ausente ou revogado para um canal da jornada.',concluido_em=now()
                    WHERE id_execucao=execucao_id;
                ELSE
                    INSERT INTO pos_venda_jornada_inscricao(id_jornada,id_paciente,criado_por)
                    VALUES (regra.id_jornada,evento.id_paciente,coalesce(regra.id_responsavel,jornada_criador))
                    ON CONFLICT(id_clinica,id_jornada,id_paciente) DO NOTHING
                    RETURNING id_inscricao INTO inscricao_id;
                    IF inscricao_id IS NULL THEN SELECT id_inscricao INTO inscricao_id
                        FROM pos_venda_jornada_inscricao
                        WHERE id_jornada=regra.id_jornada AND id_paciente=evento.id_paciente; END IF;
                    INSERT INTO pos_venda_historico(recurso,recurso_id,acao,id_usuario,dados)
                    VALUES ('inscricao',inscricao_id,'inscrita_por_evento',coalesce(regra.id_responsavel,jornada_criador),
                        jsonb_build_object('eventoId',evento.id_evento,'regraId',regra.id_regra,'evento',evento.tipo));
                    UPDATE pos_venda_automacao_execucao SET status='executada',id_inscricao=inscricao_id,
                        explicacao='O paciente foi inscrito na jornada ativa pela regra configurada.',
                        concluido_em=now() WHERE id_execucao=execucao_id;
                END IF;

            ELSIF regra.acao='avancar_jornada' THEN
                UPDATE pos_venda_jornada_inscricao i SET
                    etapa_atual=coalesce((SELECT min(e.ordem) FROM pos_venda_jornada_etapa e
                      WHERE e.id_jornada=i.id_jornada AND e.ordem>i.etapa_atual),i.etapa_atual),
                    status=CASE WHEN NOT EXISTS(SELECT 1 FROM pos_venda_jornada_etapa e
                      WHERE e.id_jornada=i.id_jornada AND e.ordem>i.etapa_atual) THEN 'concluida' ELSE 'ativa' END,
                    concluido_em=CASE WHEN NOT EXISTS(SELECT 1 FROM pos_venda_jornada_etapa e
                      WHERE e.id_jornada=i.id_jornada AND e.ordem>i.etapa_atual) THEN now() ELSE NULL END,
                    atualizado_em=now()
                WHERE i.id_paciente=evento.id_paciente AND i.status='ativa'
                  AND (regra.id_jornada IS NULL OR i.id_jornada=regra.id_jornada);
                GET DIAGNOSTICS afetados = ROW_COUNT;
                UPDATE pos_venda_automacao_execucao SET status=CASE WHEN afetados=0 THEN 'ignorada' ELSE 'executada' END,
                    explicacao=CASE WHEN afetados=0 THEN 'Nenhuma inscrição ativa correspondia à regra.'
                      ELSE 'A jornada avançou para a próxima etapa; a última etapa conclui a inscrição.' END,
                    concluido_em=now() WHERE id_execucao=execucao_id;

            ELSIF regra.acao IN ('pausar_jornada','concluir_jornada') THEN
                UPDATE pos_venda_jornada_inscricao SET
                    status=CASE WHEN regra.acao='concluir_jornada' THEN 'concluida' ELSE 'pausada' END,
                    concluido_em=CASE WHEN regra.acao='concluir_jornada' THEN now() ELSE concluido_em END,
                    atualizado_em=now()
                WHERE id_paciente=evento.id_paciente AND status='ativa'
                  AND (regra.id_jornada IS NULL OR id_jornada=regra.id_jornada);
                GET DIAGNOSTICS afetados = ROW_COUNT;
                UPDATE pos_venda_automacao_execucao SET status=CASE WHEN afetados=0 THEN 'ignorada' ELSE 'executada' END,
                    explicacao=CASE WHEN afetados=0 THEN 'Nenhuma inscrição ativa correspondia à regra.'
                      ELSE 'A inscrição foi encerrada conforme a regra de saída.' END,
                    concluido_em=now() WHERE id_execucao=execucao_id;
            END IF;
        EXCEPTION WHEN OTHERS THEN
            houve_erro := true;
            UPDATE pos_venda_automacao_execucao SET status='erro',erro=left(SQLERRM,1000),
                concluido_em=now() WHERE id_execucao=execucao_id;
        END;
    END LOOP;
    UPDATE pos_venda_evento_clinico SET status=CASE WHEN houve_erro THEN 'erro' ELSE 'processado' END,
        erro=CASE WHEN houve_erro THEN 'Uma ou mais regras falharam; consulte as execuções.' ELSE NULL END,
        processado_em=now() WHERE id_evento=evento.id_evento;
END;
$$;

CREATE OR REPLACE FUNCTION pos_venda_consumir_evento() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    PERFORM pos_venda_processar_evento(NEW.id_evento);
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_pos_venda_consumir_evento
AFTER INSERT ON pos_venda_evento_clinico
FOR EACH ROW EXECUTE FUNCTION pos_venda_consumir_evento();

-- Regras padrão preservam o comportamento de faltas e tornam sua origem auditável/configurável.
INSERT INTO pos_venda_automacao_regra(
    id_clinica,nome,evento_tipo,acao,configuracao,prioridade,criado_por,atualizado_por
)
SELECT tm.id_clinica,'Criar oportunidade após falta','CONSULTA_FALTOU','criar_oportunidade','{}',10,tm.id_usuario,tm.id_usuario
FROM (SELECT DISTINCT ON(id_clinica) id_clinica,id_usuario FROM tenant_membro WHERE ativo
      ORDER BY id_clinica,CASE perfil WHEN 'administrador' THEN 0 ELSE 1 END,id_usuario) tm
ON CONFLICT(id_clinica,nome) DO NOTHING;
INSERT INTO pos_venda_automacao_regra(
    id_clinica,nome,evento_tipo,acao,configuracao,prioridade,criado_por,atualizado_por
)
SELECT tm.id_clinica,'Criar tarefa após falta','CONSULTA_FALTOU','criar_tarefa',
  '{"prioridade":"alta","titulo":"Contatar paciente após falta","prazoHoras":0}',20,tm.id_usuario,tm.id_usuario
FROM (SELECT DISTINCT ON(id_clinica) id_clinica,id_usuario FROM tenant_membro WHERE ativo
      ORDER BY id_clinica,CASE perfil WHEN 'administrador' THEN 0 ELSE 1 END,id_usuario) tm
ON CONFLICT(id_clinica,nome) DO NOTHING;
INSERT INTO pos_venda_automacao_regra(
    id_clinica,nome,evento_tipo,acao,configuracao,prioridade,criado_por,atualizado_por
)
SELECT tm.id_clinica,'Atualizar oportunidade: ' || tipo,tipo,'atualizar_oportunidade','{}',30,tm.id_usuario,tm.id_usuario
FROM (SELECT DISTINCT ON(id_clinica) id_clinica,id_usuario FROM tenant_membro WHERE ativo
      ORDER BY id_clinica,CASE perfil WHEN 'administrador' THEN 0 ELSE 1 END,id_usuario) tm
CROSS JOIN unnest(ARRAY['CONSULTA_CRIADA','CONSULTA_CONFIRMADA','CONSULTA_CANCELADA','CONSULTA_REALIZADA']) tipo
ON CONFLICT(id_clinica,nome) DO NOTHING;

CREATE OR REPLACE FUNCTION pos_venda_publicar_consulta() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    evento_tipo TEXT;
    paciente_id INTEGER;
    revisao BIGINT;
BEGIN
    IF TG_OP='UPDATE' THEN
        IF NEW.id_paciente IS DISTINCT FROM OLD.id_paciente AND EXISTS(
            SELECT 1 FROM pos_venda_consulta WHERE id_consulta=NEW.id_consulta
        ) THEN RAISE EXCEPTION 'Consulta vinculada ao pós-venda: não é possível trocar o paciente'; END IF;
        IF NEW.status_consulta IS NOT DISTINCT FROM OLD.status_consulta THEN RETURN NEW; END IF;
    END IF;
    evento_tipo := CASE NEW.status_consulta::text
        WHEN 'Agendada' THEN 'CONSULTA_CRIADA'
        WHEN 'Confirmada' THEN 'CONSULTA_CONFIRMADA'
        WHEN 'Cancelada' THEN 'CONSULTA_CANCELADA'
        WHEN 'Faltou' THEN 'CONSULTA_FALTOU'
        WHEN 'Realizada' THEN 'CONSULTA_REALIZADA'
        ELSE NULL END;
    IF evento_tipo IS NULL THEN RETURN NEW; END IF;
    SELECT coalesce(max(origem_versao),0)+1 INTO revisao FROM pos_venda_evento_clinico
      WHERE origem_tipo='consulta' AND origem_id=NEW.id_consulta::text;
    INSERT INTO pos_venda_evento_clinico(
      identificador,tipo,ocorrido_em,origem_tipo,origem_id,origem_versao,id_paciente,payload
    ) VALUES (
      'consulta:' || NEW.id_consulta || ':' || revisao || ':' || evento_tipo,evento_tipo,now(),
      'consulta',NEW.id_consulta::text,revisao,NEW.id_paciente,
      jsonb_build_object('consultaId',NEW.id_consulta,'agendaId',NEW.id_agenda,
        'status',NEW.status_consulta::text,'operacao',TG_OP)
    ) ON CONFLICT(id_clinica,identificador) DO NOTHING;
    RETURN NEW;
END;
$$;
DROP TRIGGER IF EXISTS trg_pos_venda_consulta ON consulta;
CREATE TRIGGER trg_pos_venda_consulta
AFTER INSERT OR UPDATE ON consulta
FOR EACH ROW EXECUTE FUNCTION pos_venda_publicar_consulta();

CREATE OR REPLACE FUNCTION pos_venda_publicar_prontuario() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    paciente_id INTEGER;
BEGIN
    IF NEW.assinado_em IS NULL OR (TG_OP='UPDATE' AND OLD.assinado_em IS NOT NULL) THEN RETURN NEW; END IF;
    SELECT c.id_paciente INTO paciente_id FROM consulta c WHERE c.id_consulta=NEW.id_consulta;
    INSERT INTO pos_venda_evento_clinico(
      identificador,tipo,ocorrido_em,origem_tipo,origem_id,origem_versao,id_paciente,payload
    ) VALUES (
      'prontuario:' || NEW.id_prontuario || ':concluido','PRONTUARIO_PROCEDIMENTO_CONCLUIDO',
      NEW.assinado_em,'prontuario',NEW.id_prontuario::text,1,paciente_id,
      jsonb_build_object('prontuarioId',NEW.id_prontuario,'consultaId',NEW.id_consulta,
        'assinadoEm',NEW.assinado_em)
    ) ON CONFLICT(id_clinica,identificador) DO NOTHING;
    IF NEW.retorno_sugerido_em IS NOT NULL THEN
      INSERT INTO pos_venda_evento_clinico(
        identificador,tipo,ocorrido_em,origem_tipo,origem_id,origem_versao,id_paciente,payload
      ) VALUES (
        'prontuario:' || NEW.id_prontuario || ':retorno','PRONTUARIO_RETORNO_INDICADO',
        NEW.assinado_em,'prontuario',NEW.id_prontuario::text,1,paciente_id,
        jsonb_build_object('prontuarioId',NEW.id_prontuario,'consultaId',NEW.id_consulta,
          'retornoSugeridoEm',NEW.retorno_sugerido_em)
      ) ON CONFLICT(id_clinica,identificador) DO NOTHING;
    END IF;
    RETURN NEW;
END;
$$;
DROP TRIGGER IF EXISTS trg_pos_venda_evento_prontuario ON prontuario;
CREATE TRIGGER trg_pos_venda_evento_prontuario
AFTER INSERT OR UPDATE ON prontuario
FOR EACH ROW EXECUTE FUNCTION pos_venda_publicar_prontuario();

ALTER TABLE pos_venda_evento_clinico ENABLE ROW LEVEL SECURITY;
ALTER TABLE pos_venda_evento_clinico FORCE ROW LEVEL SECURITY;
ALTER TABLE pos_venda_automacao_regra ENABLE ROW LEVEL SECURITY;
ALTER TABLE pos_venda_automacao_regra FORCE ROW LEVEL SECURITY;
ALTER TABLE pos_venda_automacao_execucao ENABLE ROW LEVEL SECURITY;
ALTER TABLE pos_venda_automacao_execucao FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON pos_venda_evento_clinico
    USING(id_clinica=nullif(current_setting('app.tenant_id',true),'')::integer)
    WITH CHECK(id_clinica=nullif(current_setting('app.tenant_id',true),'')::integer);
CREATE POLICY tenant_isolation ON pos_venda_automacao_regra
    USING(id_clinica=nullif(current_setting('app.tenant_id',true),'')::integer)
    WITH CHECK(id_clinica=nullif(current_setting('app.tenant_id',true),'')::integer);
CREATE POLICY tenant_isolation ON pos_venda_automacao_execucao
    USING(id_clinica=nullif(current_setting('app.tenant_id',true),'')::integer)
    WITH CHECK(id_clinica=nullif(current_setting('app.tenant_id',true),'')::integer);

INSERT INTO auth_schema_version(version) VALUES (25) ON CONFLICT DO NOTHING;
COMMIT;
