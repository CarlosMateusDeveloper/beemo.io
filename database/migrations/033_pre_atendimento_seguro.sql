-- Issue #28: pré-atendimento móvel com acesso temporário e revisão da equipe.
BEGIN;

DO $migration$
DECLARE table_name text;
BEGIN
  IF EXISTS(SELECT 1 FROM auth_schema_version WHERE version=33) THEN RETURN; END IF;

  -- Tabelas auth_* guardam somente credenciais opacas e o escopo mínimo necessário
  -- para descobrir o tenant antes de habilitar a RLS. Nenhum dado do paciente fica aqui.
  CREATE TABLE auth_pre_atendimento_acesso (
    token_hash CHAR(64) PRIMARY KEY,
    id_clinica INTEGER NOT NULL REFERENCES clinica(id_clinica),
    id_consulta INTEGER NOT NULL,
    id_pre_atendimento BIGINT,
    escopo VARCHAR(40)[] NOT NULL DEFAULT ARRAY['cadastro','formulario','documentos','orientacoes'],
    expira_em TIMESTAMPTZ NOT NULL,
    revogado_em TIMESTAMPTZ,
    tentativas SMALLINT NOT NULL DEFAULT 0 CHECK(tentativas BETWEEN 0 AND 10),
    max_tentativas SMALLINT NOT NULL DEFAULT 5 CHECK(max_tentativas BETWEEN 1 AND 10),
    criado_por INTEGER NOT NULL REFERENCES usuario(id),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    ultimo_acesso_em TIMESTAMPTZ
  );
  CREATE INDEX auth_pre_atendimento_consulta_idx
    ON auth_pre_atendimento_acesso(id_clinica,id_consulta,criado_em DESC);

  CREATE TABLE auth_pre_atendimento_sessao (
    sessao_hash CHAR(64) PRIMARY KEY,
    token_hash CHAR(64) NOT NULL REFERENCES auth_pre_atendimento_acesso(token_hash) ON DELETE CASCADE,
    expira_em TIMESTAMPTZ NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    ultimo_acesso_em TIMESTAMPTZ NOT NULL DEFAULT now()
  );
  CREATE INDEX auth_pre_atendimento_sessao_expira_idx ON auth_pre_atendimento_sessao(expira_em);

  CREATE TABLE pre_atendimento_formulario_versao (
    id_formulario BIGSERIAL PRIMARY KEY,
    id_clinica INTEGER NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer REFERENCES clinica(id_clinica),
    codigo VARCHAR(60) NOT NULL,
    versao INTEGER NOT NULL CHECK(versao > 0),
    titulo VARCHAR(120) NOT NULL,
    campos JSONB NOT NULL CHECK(jsonb_typeof(campos)='array'),
    orientacoes JSONB NOT NULL DEFAULT '[]'::jsonb CHECK(jsonb_typeof(orientacoes)='array'),
    status VARCHAR(20) NOT NULL DEFAULT 'aprovado' CHECK(status IN ('rascunho','aprovado','inativo')),
    criado_por INTEGER NOT NULL REFERENCES usuario(id),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(id_clinica,codigo,versao),
    UNIQUE(id_clinica,id_formulario)
  );

  CREATE TABLE pre_atendimento (
    id_pre_atendimento BIGSERIAL PRIMARY KEY,
    id_clinica INTEGER NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer REFERENCES clinica(id_clinica),
    id_consulta INTEGER NOT NULL,
    id_paciente INTEGER NOT NULL,
    id_formulario BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'rascunho' CHECK(status IN ('rascunho','enviado','pendente','aprovado')),
    dados_propostos JSONB NOT NULL DEFAULT '{}'::jsonb CHECK(jsonb_typeof(dados_propostos)='object'),
    respostas JSONB NOT NULL DEFAULT '{}'::jsonb CHECK(jsonb_typeof(respostas)='object'),
    campos_pendentes JSONB NOT NULL DEFAULT '[]'::jsonb CHECK(jsonb_typeof(campos_pendentes)='array'),
    versao INTEGER NOT NULL DEFAULT 1,
    enviado_em TIMESTAMPTZ,
    revisado_em TIMESTAMPTZ,
    revisado_por INTEGER REFERENCES usuario(id),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(id_clinica,id_consulta),
    UNIQUE(id_clinica,id_pre_atendimento),
    FOREIGN KEY(id_clinica,id_consulta) REFERENCES consulta(id_clinica,id_consulta),
    FOREIGN KEY(id_clinica,id_paciente) REFERENCES paciente(id_clinica,id_paciente),
    FOREIGN KEY(id_clinica,id_formulario) REFERENCES pre_atendimento_formulario_versao(id_clinica,id_formulario)
  );
  CREATE INDEX pre_atendimento_fila_idx ON pre_atendimento(id_clinica,status,atualizado_em DESC);

  CREATE TABLE pre_atendimento_anexo (
    id_anexo BIGSERIAL PRIMARY KEY,
    id_clinica INTEGER NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer REFERENCES clinica(id_clinica),
    id_pre_atendimento BIGINT NOT NULL,
    nome_original VARCHAR(180) NOT NULL,
    tipo_mime VARCHAR(60) NOT NULL CHECK(tipo_mime IN ('application/pdf','image/jpeg','image/png')),
    tamanho BIGINT NOT NULL CHECK(tamanho BETWEEN 1 AND 8388608),
    sha256 CHAR(64) NOT NULL,
    conteudo BYTEA NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(id_clinica,id_pre_atendimento,sha256),
    FOREIGN KEY(id_clinica,id_pre_atendimento) REFERENCES pre_atendimento(id_clinica,id_pre_atendimento) ON DELETE CASCADE
  );

  CREATE TABLE pre_atendimento_evento (
    id_evento BIGSERIAL PRIMARY KEY,
    id_clinica INTEGER NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer REFERENCES clinica(id_clinica),
    id_pre_atendimento BIGINT NOT NULL,
    tipo VARCHAR(40) NOT NULL,
    id_usuario INTEGER REFERENCES usuario(id),
    detalhes JSONB NOT NULL DEFAULT '{}'::jsonb,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY(id_clinica,id_pre_atendimento) REFERENCES pre_atendimento(id_clinica,id_pre_atendimento) ON DELETE CASCADE
  );
  CREATE INDEX pre_atendimento_evento_historico_idx
    ON pre_atendimento_evento(id_clinica,id_pre_atendimento,criado_em DESC);

  ALTER TABLE auth_pre_atendimento_acesso ADD CONSTRAINT auth_pre_atendimento_consulta_fk
    FOREIGN KEY(id_clinica,id_consulta) REFERENCES consulta(id_clinica,id_consulta) ON DELETE CASCADE;
  ALTER TABLE auth_pre_atendimento_acesso ADD CONSTRAINT auth_pre_atendimento_registro_fk
    FOREIGN KEY(id_clinica,id_pre_atendimento) REFERENCES pre_atendimento(id_clinica,id_pre_atendimento) ON DELETE CASCADE;

  FOREACH table_name IN ARRAY ARRAY['pre_atendimento_formulario_versao','pre_atendimento','pre_atendimento_anexo','pre_atendimento_evento'] LOOP
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',table_name);
    EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY',table_name);
    EXECUTE format('CREATE POLICY tenant_isolation ON %I USING(id_clinica=nullif(current_setting(''app.tenant_id'',true),'''')::integer) WITH CHECK(id_clinica=nullif(current_setting(''app.tenant_id'',true),'''')::integer)',table_name);
  END LOOP;

  INSERT INTO auth_schema_version(version) VALUES(33);
END $migration$;

COMMIT;
