-- Migração pra bancos já criados a partir de uma versão anterior de
-- schema_clinica.sql (que já tem esse init script incluído). Roda uma vez:
--   psql -U clinica -d clinica -f database/migrations/001_medico_status_e_metricas.sql
--
-- Fecha o gap de schema da issue #17 (/medicos): status rico do médico,
-- repasse% pra receita líquida, e horário real de início/cancelamento da
-- consulta pra pontualidade e horas perdidas.

ALTER TABLE medico ADD COLUMN status VARCHAR(10)
    CHECK (status IN ('ativo', 'ferias', 'afastado', 'desligado'));
UPDATE medico SET status = CASE WHEN ativo THEN 'ativo' ELSE 'desligado' END;
ALTER TABLE medico ALTER COLUMN status SET NOT NULL;
ALTER TABLE medico ALTER COLUMN status SET DEFAULT 'ativo';
ALTER TABLE medico DROP COLUMN ativo;

ALTER TABLE medico ADD COLUMN repasse_percentual NUMERIC(5, 2) NULL
    CHECK (repasse_percentual IS NULL OR (repasse_percentual BETWEEN 0 AND 100));

ALTER TABLE consulta ADD COLUMN iniciado_em TIMESTAMPTZ NULL;
ALTER TABLE consulta ADD COLUMN cancelado_em TIMESTAMPTZ NULL;
