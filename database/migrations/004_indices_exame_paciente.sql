-- Migração pra bancos já criados a partir de uma versão anterior de
-- schema_clinica.sql. Roda uma vez:
--   psql -U clinica -d clinica -f database/migrations/004_indices_exame_paciente.sql
--
-- Índices pra listagem paginada/filtrável de GET /api/exames (issue #13) e
-- busca por telefone em GET /api/pacientes (issue #11) — cpf já é UNIQUE
-- (índice implícito), nome usa ILIKE e não se beneficia de um btree simples.

CREATE INDEX IF NOT EXISTS idx_exame_paciente ON exame(id_paciente);
CREATE INDEX IF NOT EXISTS idx_exame_status ON exame(status);
CREATE INDEX IF NOT EXISTS idx_paciente_ddd_numero ON paciente(ddd, numero);
