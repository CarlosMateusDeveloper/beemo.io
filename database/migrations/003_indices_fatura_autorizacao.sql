-- Migração pra bancos já criados a partir de uma versão anterior de
-- schema_clinica.sql. Roda uma vez:
--   psql -U clinica -d clinica -f database/migrations/003_indices_fatura_autorizacao.sql
--
-- Índices pra listagem paginada/filtrável de GET /api/faturas e
-- GET /api/autorizacoes-convenio (issues #14 e #15).

CREATE INDEX IF NOT EXISTS idx_fatura_status ON fatura(status);
CREATE INDEX IF NOT EXISTS idx_fatura_vencimento ON fatura(vencimento);
CREATE INDEX IF NOT EXISTS idx_autorizacao_convenio_status ON autorizacao_convenio(status);
