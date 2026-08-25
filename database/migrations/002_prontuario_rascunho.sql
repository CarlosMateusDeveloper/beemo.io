-- Migração pra bancos já criados a partir de uma versão anterior de
-- schema_clinica.sql. Roda uma vez:
--   psql -U clinica -d clinica -f database/migrations/002_prontuario_rascunho.sql
--
-- Suporte a rascunho em /prontuario: um atendimento em andamento pode ser
-- salvo sem descricao/diagnostico/prescricao preenchidos. assinado_em NULL
-- já era o sinal de "não finalizado" — isso só destrava salvar antes de
-- preencher tudo. Finalizar continua exigindo os três campos, mas essa
-- validação passa a ser da aplicação, não do banco.

ALTER TABLE prontuario ALTER COLUMN descricao DROP NOT NULL;
ALTER TABLE prontuario ALTER COLUMN diagnostico DROP NOT NULL;
ALTER TABLE prontuario ALTER COLUMN prescricao DROP NOT NULL;
