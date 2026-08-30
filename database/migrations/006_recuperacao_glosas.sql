-- Migração pra bancos já criados a partir de uma versão anterior de
-- schema_clinica.sql (incluindo quem já rodou 005_convenios_auditoria_lotes_glosas.sql).
-- Roda uma vez:
--   psql -U clinica -d clinica -f database/migrations/006_recuperacao_glosas.sql
--
-- Aditivo sobre o schema da Fase 14 (glosa/glosa_historico) pra fechar o
-- fluxo de recuperação de glosas (docs/specs/recuperacao-glosas.md) — não
-- remodela nada que já existia.

ALTER TYPE status_glosa ADD VALUE IF NOT EXISTS 'negada';
ALTER TYPE status_glosa ADD VALUE IF NOT EXISTS 'recuperada_parcialmente';

ALTER TABLE glosa ADD COLUMN data_glosa DATE NOT NULL DEFAULT CURRENT_DATE;
ALTER TABLE glosa ADD COLUMN valor_faturado NUMERIC(10, 2) NULL;
ALTER TABLE glosa ADD COLUMN codigo_motivo VARCHAR(50) NULL;
ALTER TABLE glosa ADD COLUMN origem VARCHAR(10) NOT NULL DEFAULT 'manual'
    CHECK (origem IN ('importada', 'manual'));
ALTER TABLE glosa ADD COLUMN recorribilidade VARCHAR(20) NULL
    CHECK (recorribilidade IN ('recorrivel', 'nao_recorrivel', 'necessita_analise'));
ALTER TABLE glosa ADD COLUMN categoria_motivo VARCHAR(20) NULL
    CHECK (categoria_motivo IN (
        'autorizacao', 'documentacao', 'codigo_procedimento', 'elegibilidade',
        'cobertura', 'cobranca', 'prazo', 'duplicidade', 'outros'
    ));

CREATE TYPE status_recurso_glosa AS ENUM (
    'rascunho', 'em_preparacao', 'enviado', 'aguardando_retorno', 'em_analise_convenio',
    'recuperado', 'recuperado_parcialmente', 'negado', 'prazo_expirado'
);
CREATE TYPE canal_envio_recurso AS ENUM ('manual', 'portal_convenio', 'email');

CREATE TABLE recurso_glosa (
    id_recurso INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_glosa INT NOT NULL REFERENCES glosa(id_glosa),
    status status_recurso_glosa NOT NULL DEFAULT 'rascunho',
    justificativa TEXT NULL,
    prazo_limite DATE NULL,
    id_usuario_responsavel INT NULL REFERENCES usuario(id),
    canal_envio canal_envio_recurso NULL,
    protocolo VARCHAR(100) NULL,
    enviado_em TIMESTAMPTZ NULL,
    id_usuario_envio INT NULL REFERENCES usuario(id),
    respondido_em TIMESTAMPTZ NULL,
    valor_recuperado NUMERIC(10, 2) NULL,
    valor_nao_recuperado NUMERIC(10, 2) NULL,
    motivo_negativa TEXT NULL,
    documento_resposta_url TEXT NULL,
    evidencias_conferidas BOOLEAN NOT NULL DEFAULT FALSE,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE recurso_glosa_documento (
    id_recurso_glosa_documento INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_recurso INT NOT NULL REFERENCES recurso_glosa(id_recurso),
    tipo VARCHAR(30) NOT NULL
        CHECK (tipo IN ('prontuario', 'guia', 'solicitacao_medica', 'autorizacao', 'laudo', 'comprovante', 'outro')),
    id_documento_anexo INT NULL REFERENCES documento_anexo(id_documento),
    descricao VARCHAR(255) NULL
);

CREATE INDEX idx_recurso_glosa_glosa ON recurso_glosa(id_glosa);
CREATE INDEX idx_recurso_glosa_status ON recurso_glosa(status);
CREATE INDEX idx_recurso_glosa_prazo ON recurso_glosa(prazo_limite);
CREATE INDEX idx_recurso_glosa_documento_recurso ON recurso_glosa_documento(id_recurso);
