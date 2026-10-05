-- O tenant e resolvido pela conta; nao existe selecao de clinica na interface.
BEGIN;

DO $migration$
BEGIN
 IF EXISTS(SELECT 1 FROM auth_schema_version WHERE version=13) THEN RETURN; END IF;

 IF EXISTS(
   SELECT id_usuario
   FROM tenant_membro
   WHERE ativo
   GROUP BY id_usuario
   HAVING count(*) > 1
 ) THEN
   RAISE EXCEPTION 'Existem contas vinculadas a mais de uma clinica ativa. Revise os vinculos antes de aplicar a migracao 013.';
 END IF;

 CREATE UNIQUE INDEX IF NOT EXISTS tenant_membro_usuario_ativo_uk
   ON tenant_membro(id_usuario)
   WHERE ativo;

 INSERT INTO auth_schema_version(version) VALUES (13);
END $migration$;

COMMIT;
