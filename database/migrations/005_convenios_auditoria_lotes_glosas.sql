-- Migração pra bancos já criados a partir de uma versão anterior de
-- schema_clinica.sql. Roda uma vez:
--   psql -U clinica -d clinica -f database/migrations/005_convenios_auditoria_lotes_glosas.sql
--
-- Schema completo pras 4 abas da spec de /convenios (Glosas, Auditoria,
-- Convênios, Lotes) — implementação desta fase é só a aba Convênios
-- (cadastro/config); Auditoria/Lotes/Glosas ganham tabela agora pra não
-- remodelar depois, motor de regras e telas entram em fase futura.

-- =====================================================================
-- FASE 14 — Convênios: auditoria, lotes e glosas
-- =====================================================================

-- --- Convênios (config) — implementado nesta fase ---

ALTER TABLE convenio ADD COLUMN contato VARCHAR(150) NULL;
ALTER TABLE convenio ADD COLUMN observacoes TEXT NULL;
-- Tocado pelo service sempre que o convenio OU qualquer plano/procedimento/
-- regra/documento obrigatorio dele muda — alimenta a coluna "Última
-- atualização" da listagem da aba Convênios.
ALTER TABLE convenio ADD COLUMN atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE TABLE convenio_plano (
    id_plano INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_convenio INT NOT NULL REFERENCES convenio(id_convenio),
    nome VARCHAR(100) NOT NULL,
    codigo VARCHAR(50) NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE convenio_procedimento (
    id_convenio_procedimento INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_convenio INT NOT NULL REFERENCES convenio(id_convenio),
    -- NULL = vale pra todos os planos do convenio; preenchido = preco/regra
    -- especifica de um plano.
    id_plano INT NULL REFERENCES convenio_plano(id_plano),
    codigo VARCHAR(30) NOT NULL,
    descricao VARCHAR(200) NOT NULL,
    valor_negociado NUMERIC(10, 2) NULL,
    cobertura BOOLEAN NOT NULL DEFAULT TRUE,
    exige_autorizacao BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TYPE tipo_regra_auditoria AS ENUM (
    'autorizacao_obrigatoria', 'documento_obrigatorio', 'codigo_incompativel',
    'procedimento_nao_coberto', 'paciente_inelegivel', 'quantidade_acima_permitido',
    'prazo_faturamento_excedido', 'profissional_nao_habilitado', 'divergencia_atendimento_faturamento'
);
CREATE TYPE severidade_regra AS ENUM ('critica', 'alta', 'media', 'baixa');

-- parametros guarda config especifica de cada regra (ex: {"quantidadeMaxima": 3})
-- pra permitir novas variacoes sem alterar o schema, como a spec pede
-- ("permitir adicionar novas regras posteriormente sem alterar a estrutura
-- principal do modulo").
CREATE TABLE regra_auditoria (
    id_regra INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_convenio INT NOT NULL REFERENCES convenio(id_convenio),
    -- NULL = regra geral do convenio; preenchido = so vale pra esse procedimento.
    id_convenio_procedimento INT NULL REFERENCES convenio_procedimento(id_convenio_procedimento),
    tipo tipo_regra_auditoria NOT NULL,
    severidade severidade_regra NOT NULL,
    descricao VARCHAR(255) NOT NULL,
    parametros JSONB NOT NULL DEFAULT '{}'::jsonb,
    ativo BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE documento_obrigatorio_convenio (
    id_documento_obrigatorio INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_convenio INT NOT NULL REFERENCES convenio(id_convenio),
    id_convenio_procedimento INT NULL REFERENCES convenio_procedimento(id_convenio_procedimento),
    nome_documento VARCHAR(150) NOT NULL,
    obrigatorio BOOLEAN NOT NULL DEFAULT TRUE
);

-- --- Auditoria, Lotes, Glosas — schema pronto agora (pra nao remodelar
-- depois), motor de regras e telas entram numa fase futura ---

CREATE TYPE status_auditoria_atendimento AS ENUM ('bloqueado', 'atencao', 'aprovado');

CREATE TABLE auditoria_atendimento (
    id_auditoria_atendimento INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_consulta INT NOT NULL UNIQUE REFERENCES consulta(id_consulta),
    status status_auditoria_atendimento NOT NULL,
    valor_em_risco NUMERIC(10, 2) NOT NULL DEFAULT 0,
    avaliado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TYPE status_auditoria_item AS ENUM ('ok', 'falha');

CREATE TABLE auditoria_item (
    id_auditoria_item INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_auditoria_atendimento INT NOT NULL REFERENCES auditoria_atendimento(id_auditoria_atendimento),
    id_regra INT NULL REFERENCES regra_auditoria(id_regra),
    status status_auditoria_item NOT NULL,
    descricao VARCHAR(255) NOT NULL,
    severidade severidade_regra NULL,
    acao_recomendada VARCHAR(255) NULL
);

CREATE TYPE status_lote_faturamento AS ENUM (
    'rascunho', 'pronto_envio', 'enviado', 'processando', 'pago_parcial', 'pago', 'com_glosas'
);

CREATE TABLE lote_faturamento (
    id_lote INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_convenio INT NOT NULL REFERENCES convenio(id_convenio),
    codigo VARCHAR(30) NOT NULL UNIQUE,
    status status_lote_faturamento NOT NULL DEFAULT 'rascunho',
    data_envio DATE NULL,
    valor_total NUMERIC(12, 2) NOT NULL DEFAULT 0,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Liga ao `fatura` ja existente em vez de duplicar valor/status.
CREATE TABLE lote_item (
    id_lote_item INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_lote INT NOT NULL REFERENCES lote_faturamento(id_lote),
    id_fatura INT NOT NULL UNIQUE REFERENCES fatura(id_fatura)
);

CREATE TYPE status_glosa AS ENUM (
    'nova', 'em_analise', 'recurso_preparacao', 'recurso_enviado', 'recuperada', 'confirmada'
);

CREATE TABLE glosa (
    id_glosa INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_fatura INT NOT NULL REFERENCES fatura(id_fatura),
    id_convenio INT NOT NULL REFERENCES convenio(id_convenio),
    motivo VARCHAR(255) NOT NULL,
    valor NUMERIC(10, 2) NOT NULL,
    prazo_recurso DATE NULL,
    status status_glosa NOT NULL DEFAULT 'nova',
    id_usuario_responsavel INT NULL REFERENCES usuario(id),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE glosa_historico (
    id_glosa_historico INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_glosa INT NOT NULL REFERENCES glosa(id_glosa),
    evento VARCHAR(255) NOT NULL,
    id_usuario INT NULL REFERENCES usuario(id),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Documentos de glosa reaproveitam `documento_anexo` (ja existe, generico
-- por id_consulta) — nao criamos tabela nova so pra isso.

CREATE INDEX idx_convenio_procedimento_convenio ON convenio_procedimento(id_convenio);
CREATE INDEX idx_regra_auditoria_convenio ON regra_auditoria(id_convenio);
CREATE INDEX idx_glosa_status ON glosa(status);
CREATE INDEX idx_glosa_prazo_recurso ON glosa(prazo_recurso);
CREATE INDEX idx_lote_faturamento_status ON lote_faturamento(status);
