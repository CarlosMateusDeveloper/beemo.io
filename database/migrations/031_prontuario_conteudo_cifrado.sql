-- Issue #67: conteúdo clínico livre cifrado pela aplicação.
BEGIN;
CREATE TABLE IF NOT EXISTS prontuario_conteudo_cifrado (
  id_prontuario INTEGER NOT NULL,
  id_clinica INTEGER NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer REFERENCES clinica(id_clinica),
  versao_chave INTEGER NOT NULL CHECK(versao_chave > 0),
  nonce BYTEA NOT NULL CHECK(octet_length(nonce)=12),
  conteudo BYTEA NOT NULL,
  criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
  atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY(id_clinica,id_prontuario),
  FOREIGN KEY(id_clinica,id_prontuario) REFERENCES prontuario(id_clinica,id_prontuario) ON DELETE CASCADE
);
ALTER TABLE prontuario_conteudo_cifrado ENABLE ROW LEVEL SECURITY;
ALTER TABLE prontuario_conteudo_cifrado FORCE ROW LEVEL SECURITY;
DO $rls$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_policies WHERE schemaname=current_schema() AND tablename='prontuario_conteudo_cifrado' AND policyname='tenant_isolation') THEN
  CREATE POLICY tenant_isolation ON prontuario_conteudo_cifrado USING(id_clinica=nullif(current_setting('app.tenant_id',true),'')::integer) WITH CHECK(id_clinica=nullif(current_setting('app.tenant_id',true),'')::integer);
 END IF;
END $rls$;
INSERT INTO auth_schema_version(version) VALUES(31) ON CONFLICT DO NOTHING;
COMMIT;
