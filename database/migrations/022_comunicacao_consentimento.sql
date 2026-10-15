-- Issue #23: política central e auditável de consentimento e modelos.
-- Aplicar depois de 021_integracao_api_clinicas.sql.
BEGIN;

CREATE TABLE comunicacao_consentimento_evento (
    id_consentimento BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_clinica INTEGER NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer
        REFERENCES clinica(id_clinica),
    id_paciente INTEGER,
    id_paciente_externo BIGINT,
    telefone VARCHAR(20),
    canal VARCHAR(20) NOT NULL CHECK(canal IN ('whatsapp','email','sms','telefone','todos')),
    finalidade VARCHAR(30) NOT NULL CHECK(finalidade IN ('atendimento','pos_venda','campanha','transacional','todos')),
    decisao VARCHAR(12) NOT NULL CHECK(decisao IN ('concedido','revogado')),
    origem VARCHAR(40) NOT NULL CHECK(origem IN (
        'paciente','formulario','importacao','importacao_sem_evidencia','usuario','canal','sistema'
    )),
    texto_aceito TEXT,
    evidencia_referencia VARCHAR(300),
    ocorrido_em TIMESTAMPTZ NOT NULL,
    id_usuario INTEGER,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(id_clinica,id_consentimento),
    CHECK((id_paciente IS NOT NULL)::integer + (id_paciente_externo IS NOT NULL)::integer
        + (telefone IS NOT NULL)::integer = 1),
    CHECK(decisao='revogado' OR nullif(trim(texto_aceito),'') IS NOT NULL),
    FOREIGN KEY(id_clinica,id_paciente) REFERENCES paciente(id_clinica,id_paciente),
    FOREIGN KEY(id_clinica,id_paciente_externo)
        REFERENCES integracao_paciente_externo(id_clinica,id_paciente_externo) ON DELETE CASCADE,
    FOREIGN KEY(id_clinica,id_usuario) REFERENCES tenant_membro(id_clinica,id_usuario)
);
CREATE INDEX comunicacao_consentimento_atual_idx ON comunicacao_consentimento_evento(
    id_clinica,id_paciente,canal,finalidade,ocorrido_em DESC,id_consentimento DESC
);
CREATE INDEX comunicacao_consentimento_externo_idx ON comunicacao_consentimento_evento(
    id_clinica,id_paciente_externo,canal,finalidade,ocorrido_em DESC,id_consentimento DESC
);
CREATE INDEX comunicacao_consentimento_telefone_idx ON comunicacao_consentimento_evento(
    id_clinica,telefone,canal,finalidade,ocorrido_em DESC,id_consentimento DESC
);

CREATE TABLE comunicacao_modelo (
    id_modelo BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_clinica INTEGER NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer
        REFERENCES clinica(id_clinica),
    codigo VARCHAR(100) NOT NULL,
    versao INTEGER NOT NULL CHECK(versao > 0),
    nome VARCHAR(160) NOT NULL,
    canal VARCHAR(20) NOT NULL CHECK(canal IN ('whatsapp','email','sms')),
    finalidade VARCHAR(30) NOT NULL CHECK(finalidade IN ('atendimento','pos_venda','campanha','transacional')),
    categoria VARCHAR(40) NOT NULL,
    idioma VARCHAR(15) NOT NULL,
    conteudo TEXT NOT NULL,
    id_usuario INTEGER NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(id_clinica,id_modelo),
    UNIQUE(id_clinica,codigo,versao),
    FOREIGN KEY(id_clinica,id_usuario) REFERENCES tenant_membro(id_clinica,id_usuario)
);
CREATE INDEX comunicacao_modelo_catalogo_idx
    ON comunicacao_modelo(id_clinica,codigo,versao DESC);

CREATE TABLE comunicacao_modelo_status_evento (
    id_status BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_clinica INTEGER NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer
        REFERENCES clinica(id_clinica),
    id_modelo BIGINT NOT NULL,
    status VARCHAR(12) NOT NULL CHECK(status IN ('rascunho','submetido','aprovado','rejeitado','inativo')),
    provedor VARCHAR(80),
    provedor_modelo_id VARCHAR(160),
    motivo_rejeicao VARCHAR(500),
    id_usuario INTEGER NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(id_clinica,id_status),
    CHECK(status NOT IN ('aprovado','rejeitado') OR nullif(trim(provedor),'') IS NOT NULL),
    CHECK(status<>'aprovado' OR nullif(trim(provedor_modelo_id),'') IS NOT NULL),
    CHECK(status<>'rejeitado' OR nullif(trim(motivo_rejeicao),'') IS NOT NULL),
    FOREIGN KEY(id_clinica,id_modelo) REFERENCES comunicacao_modelo(id_clinica,id_modelo),
    FOREIGN KEY(id_clinica,id_usuario) REFERENCES tenant_membro(id_clinica,id_usuario)
);
CREATE INDEX comunicacao_modelo_status_atual_idx
    ON comunicacao_modelo_status_evento(id_clinica,id_modelo,id_status DESC);

CREATE TABLE comunicacao_tentativa (
    id_tentativa BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_clinica INTEGER NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer
        REFERENCES clinica(id_clinica),
    id_paciente INTEGER,
    telefone VARCHAR(20),
    canal VARCHAR(20) NOT NULL CHECK(canal IN ('whatsapp','email','sms','telefone')),
    finalidade VARCHAR(30) NOT NULL CHECK(finalidade IN ('atendimento','pos_venda','campanha','transacional')),
    essencial BOOLEAN NOT NULL DEFAULT false,
    id_consentimento BIGINT,
    id_modelo BIGINT,
    modelo_versao INTEGER,
    resultado VARCHAR(10) NOT NULL CHECK(resultado IN ('permitida','bloqueada')),
    motivo VARCHAR(80) NOT NULL,
    origem VARCHAR(80) NOT NULL,
    id_usuario INTEGER,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(id_clinica,id_tentativa),
    FOREIGN KEY(id_clinica,id_paciente) REFERENCES paciente(id_clinica,id_paciente),
    FOREIGN KEY(id_clinica,id_consentimento)
        REFERENCES comunicacao_consentimento_evento(id_clinica,id_consentimento),
    FOREIGN KEY(id_clinica,id_modelo) REFERENCES comunicacao_modelo(id_clinica,id_modelo),
    FOREIGN KEY(id_clinica,id_usuario) REFERENCES tenant_membro(id_clinica,id_usuario)
);
CREATE INDEX comunicacao_tentativa_paciente_idx
    ON comunicacao_tentativa(id_clinica,id_paciente,criado_em DESC);

ALTER TABLE mensagem
    ADD COLUMN IF NOT EXISTS id_tentativa_comunicacao BIGINT,
    ADD COLUMN IF NOT EXISTS finalidade_comunicacao VARCHAR(30),
    ADD COLUMN IF NOT EXISTS id_consentimento BIGINT,
    ADD COLUMN IF NOT EXISTS id_modelo_comunicacao BIGINT,
    ADD COLUMN IF NOT EXISTS modelo_versao INTEGER;
ALTER TABLE mensagem
    ADD CONSTRAINT mensagem_tentativa_comunicacao_fk FOREIGN KEY(id_clinica,id_tentativa_comunicacao)
        REFERENCES comunicacao_tentativa(id_clinica,id_tentativa),
    ADD CONSTRAINT mensagem_consentimento_fk FOREIGN KEY(id_clinica,id_consentimento)
        REFERENCES comunicacao_consentimento_evento(id_clinica,id_consentimento),
    ADD CONSTRAINT mensagem_modelo_comunicacao_fk FOREIGN KEY(id_clinica,id_modelo_comunicacao)
        REFERENCES comunicacao_modelo(id_clinica,id_modelo);

ALTER TABLE envio_retorno
    ADD COLUMN IF NOT EXISTS id_tentativa_comunicacao BIGINT,
    ADD COLUMN IF NOT EXISTS id_consentimento BIGINT,
    ADD COLUMN IF NOT EXISTS id_modelo_comunicacao BIGINT,
    ADD COLUMN IF NOT EXISTS modelo_versao INTEGER;
ALTER TABLE envio_retorno
    ADD CONSTRAINT envio_retorno_tentativa_comunicacao_fk FOREIGN KEY(id_clinica,id_tentativa_comunicacao)
        REFERENCES comunicacao_tentativa(id_clinica,id_tentativa),
    ADD CONSTRAINT envio_retorno_consentimento_fk FOREIGN KEY(id_clinica,id_consentimento)
        REFERENCES comunicacao_consentimento_evento(id_clinica,id_consentimento),
    ADD CONSTRAINT envio_retorno_modelo_comunicacao_fk FOREIGN KEY(id_clinica,id_modelo_comunicacao)
        REFERENCES comunicacao_modelo(id_clinica,id_modelo);

ALTER TABLE pos_venda_campanha
    ADD COLUMN IF NOT EXISTS id_modelo_comunicacao BIGINT,
    ADD COLUMN IF NOT EXISTS finalidade VARCHAR(30) NOT NULL DEFAULT 'campanha';
ALTER TABLE pos_venda_campanha
    ADD CONSTRAINT pos_venda_campanha_finalidade_check CHECK(finalidade IN ('pos_venda','campanha')),
    ADD CONSTRAINT pos_venda_campanha_modelo_fk FOREIGN KEY(id_clinica,id_modelo_comunicacao)
        REFERENCES comunicacao_modelo(id_clinica,id_modelo);
ALTER TABLE pos_venda_campanha_publico ADD COLUMN IF NOT EXISTS motivo_exclusao VARCHAR(80);

CREATE OR REPLACE FUNCTION comunicacao_bloquear_mutacao() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Histórico de comunicação é imutável';
END;
$$;
CREATE TRIGGER trg_comunicacao_consentimento_immutable
    BEFORE UPDATE OR DELETE ON comunicacao_consentimento_evento
    FOR EACH ROW EXECUTE FUNCTION comunicacao_bloquear_mutacao();
CREATE TRIGGER trg_comunicacao_modelo_immutable
    BEFORE UPDATE OR DELETE ON comunicacao_modelo
    FOR EACH ROW EXECUTE FUNCTION comunicacao_bloquear_mutacao();
CREATE TRIGGER trg_comunicacao_modelo_status_immutable
    BEFORE UPDATE OR DELETE ON comunicacao_modelo_status_evento
    FOR EACH ROW EXECUTE FUNCTION comunicacao_bloquear_mutacao();
CREATE TRIGGER trg_comunicacao_tentativa_immutable
    BEFORE UPDATE OR DELETE ON comunicacao_tentativa
    FOR EACH ROW EXECUTE FUNCTION comunicacao_bloquear_mutacao();

ALTER TABLE comunicacao_consentimento_evento ENABLE ROW LEVEL SECURITY;
ALTER TABLE comunicacao_consentimento_evento FORCE ROW LEVEL SECURITY;
ALTER TABLE comunicacao_modelo ENABLE ROW LEVEL SECURITY;
ALTER TABLE comunicacao_modelo FORCE ROW LEVEL SECURITY;
ALTER TABLE comunicacao_modelo_status_evento ENABLE ROW LEVEL SECURITY;
ALTER TABLE comunicacao_modelo_status_evento FORCE ROW LEVEL SECURITY;
ALTER TABLE comunicacao_tentativa ENABLE ROW LEVEL SECURITY;
ALTER TABLE comunicacao_tentativa FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON comunicacao_consentimento_evento
    USING(id_clinica=nullif(current_setting('app.tenant_id',true),'')::integer)
    WITH CHECK(id_clinica=nullif(current_setting('app.tenant_id',true),'')::integer);
CREATE POLICY tenant_isolation ON comunicacao_modelo
    USING(id_clinica=nullif(current_setting('app.tenant_id',true),'')::integer)
    WITH CHECK(id_clinica=nullif(current_setting('app.tenant_id',true),'')::integer);
CREATE POLICY tenant_isolation ON comunicacao_modelo_status_evento
    USING(id_clinica=nullif(current_setting('app.tenant_id',true),'')::integer)
    WITH CHECK(id_clinica=nullif(current_setting('app.tenant_id',true),'')::integer);
CREATE POLICY tenant_isolation ON comunicacao_tentativa
    USING(id_clinica=nullif(current_setting('app.tenant_id',true),'')::integer)
    WITH CHECK(id_clinica=nullif(current_setting('app.tenant_id',true),'')::integer);

CREATE OR REPLACE FUNCTION comunicacao_avaliar(
    p_id_paciente INTEGER,
    p_telefone TEXT,
    p_canal TEXT,
    p_finalidade TEXT,
    p_essencial BOOLEAN,
    p_id_modelo BIGINT DEFAULT NULL
) RETURNS TABLE(
    permitido BOOLEAN,
    motivo TEXT,
    id_consentimento BIGINT,
    id_modelo BIGINT,
    modelo_versao INTEGER
) LANGUAGE plpgsql STABLE AS $$
DECLARE
    consentimento comunicacao_consentimento_evento%ROWTYPE;
    modelo comunicacao_modelo%ROWTYPE;
    modelo_status comunicacao_modelo_status_evento%ROWTYPE;
    telefone_normalizado TEXT := nullif(regexp_replace(coalesce(p_telefone,''),'\D','','g'),'');
BEGIN
    IF p_canal NOT IN ('whatsapp','email','sms','telefone')
       OR p_finalidade NOT IN ('atendimento','pos_venda','campanha','transacional') THEN
        RETURN QUERY SELECT false,'politica_invalida',NULL::bigint,NULL::bigint,NULL::integer;
        RETURN;
    END IF;

    IF p_id_paciente IS NOT NULL AND EXISTS (
        SELECT 1 FROM paciente_retorno_status prs
        WHERE prs.id_paciente=p_id_paciente AND prs.status='nao_contatar'
    ) THEN
        RETURN QUERY SELECT false,'nao_contatar',NULL::bigint,NULL::bigint,NULL::integer;
        RETURN;
    END IF;

    IF p_id_modelo IS NOT NULL THEN
        SELECT * INTO modelo FROM comunicacao_modelo m WHERE m.id_modelo=p_id_modelo;
        IF NOT FOUND THEN
            RETURN QUERY SELECT false,'modelo_nao_encontrado',NULL::bigint,NULL::bigint,NULL::integer;
            RETURN;
        END IF;
        SELECT * INTO modelo_status FROM comunicacao_modelo_status_evento s
        WHERE s.id_modelo=modelo.id_modelo ORDER BY s.id_status DESC LIMIT 1;
        IF NOT FOUND OR modelo_status.status<>'aprovado' THEN
            RETURN QUERY SELECT false,'modelo_nao_aprovado',NULL::bigint,p_id_modelo,NULL::integer;
            RETURN;
        END IF;
        IF modelo.canal<>p_canal OR modelo.finalidade<>p_finalidade THEN
            RETURN QUERY SELECT false,'modelo_incompativel',NULL::bigint,modelo.id_modelo,modelo.versao;
            RETURN;
        END IF;
    ELSIF NOT p_essencial AND p_canal IN ('whatsapp','email','sms') THEN
        RETURN QUERY SELECT false,'modelo_obrigatorio',NULL::bigint,NULL::bigint,NULL::integer;
        RETURN;
    END IF;

    IF coalesce(p_essencial,false) THEN
        RETURN QUERY SELECT true,'comunicacao_essencial',NULL::bigint,
            modelo.id_modelo,modelo.versao;
        RETURN;
    END IF;
    IF p_id_paciente IS NULL AND telefone_normalizado IS NULL THEN
        RETURN QUERY SELECT false,'paciente_nao_identificado',NULL::bigint,
            modelo.id_modelo,modelo.versao;
        RETURN;
    END IF;

    SELECT * INTO consentimento FROM comunicacao_consentimento_evento c
    WHERE ((p_id_paciente IS NOT NULL AND c.id_paciente=p_id_paciente)
        OR (c.telefone IS NOT NULL AND c.telefone=telefone_normalizado))
      AND c.canal IN (p_canal,'todos')
      AND c.finalidade IN (p_finalidade,'todos')
    ORDER BY c.ocorrido_em DESC,c.id_consentimento DESC LIMIT 1;
    IF NOT FOUND THEN
        RETURN QUERY SELECT false,'consentimento_ausente',NULL::bigint,
            modelo.id_modelo,modelo.versao;
    ELSIF consentimento.decisao<>'concedido' THEN
        RETURN QUERY SELECT false,'consentimento_revogado',consentimento.id_consentimento,
            modelo.id_modelo,modelo.versao;
    ELSE
        RETURN QUERY SELECT true,'consentimento_valido',consentimento.id_consentimento,
            modelo.id_modelo,modelo.versao;
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION comunicacao_registrar_tentativa(
    p_id_paciente INTEGER,
    p_telefone TEXT,
    p_canal TEXT,
    p_finalidade TEXT,
    p_essencial BOOLEAN,
    p_id_modelo BIGINT,
    p_origem TEXT,
    p_id_usuario INTEGER DEFAULT NULL
) RETURNS TABLE(
    id_tentativa BIGINT,
    permitido BOOLEAN,
    motivo TEXT,
    id_consentimento BIGINT,
    id_modelo BIGINT,
    modelo_versao INTEGER
) LANGUAGE plpgsql VOLATILE AS $$
DECLARE avaliacao RECORD;
DECLARE tentativa BIGINT;
BEGIN
    SELECT * INTO avaliacao FROM comunicacao_avaliar(
        p_id_paciente,p_telefone,p_canal,p_finalidade,p_essencial,p_id_modelo
    );
    INSERT INTO comunicacao_tentativa(
        id_paciente,telefone,canal,finalidade,essencial,id_consentimento,id_modelo,
        modelo_versao,resultado,motivo,origem,id_usuario
    ) VALUES (
        p_id_paciente,nullif(regexp_replace(coalesce(p_telefone,''),'\D','','g'),''),p_canal,p_finalidade,
        coalesce(p_essencial,false),avaliacao.id_consentimento,avaliacao.id_modelo,
        avaliacao.modelo_versao,CASE WHEN avaliacao.permitido THEN 'permitida' ELSE 'bloqueada' END,
        avaliacao.motivo,p_origem,p_id_usuario
    ) RETURNING comunicacao_tentativa.id_tentativa INTO tentativa;
    RETURN QUERY SELECT tentativa,avaliacao.permitido,avaliacao.motivo,
        avaliacao.id_consentimento,avaliacao.id_modelo,avaliacao.modelo_versao;
END;
$$;

-- Os textos legados entram como rascunhos. Um responsável ainda precisa
-- submetê-los e registrar a aprovação real do provedor antes do uso.
INSERT INTO comunicacao_modelo(
    id_clinica,codigo,versao,nome,canal,finalidade,categoria,idioma,conteudo,id_usuario
)
SELECT legacy.id_clinica,'retorno_' || legacy.grupo::text,1,
       'Retorno: ' || replace(legacy.grupo::text,'_',' '),'whatsapp','pos_venda','utilidade','pt_BR',
       legacy.texto,member.id_usuario
FROM mensagem_modelo_retorno legacy
JOIN LATERAL (
    SELECT tm.id_usuario FROM tenant_membro tm
    WHERE tm.id_clinica=legacy.id_clinica AND tm.ativo
    ORDER BY CASE WHEN EXISTS (
        SELECT 1 FROM tenant_membro_papel papel
        WHERE papel.id_clinica=tm.id_clinica AND papel.id_usuario=tm.id_usuario
          AND papel.papel='administrador'
    ) THEN 0 ELSE 1 END,tm.id_usuario LIMIT 1
) member ON true
ON CONFLICT(id_clinica,codigo,versao) DO NOTHING;

INSERT INTO comunicacao_modelo_status_evento(id_clinica,id_modelo,status,id_usuario)
SELECT m.id_clinica,m.id_modelo,'rascunho',m.id_usuario FROM comunicacao_modelo m
WHERE NOT EXISTS (SELECT 1 FROM comunicacao_modelo_status_evento s WHERE s.id_modelo=m.id_modelo);

INSERT INTO auth_schema_version(version) VALUES (22) ON CONFLICT DO NOTHING;
COMMIT;
