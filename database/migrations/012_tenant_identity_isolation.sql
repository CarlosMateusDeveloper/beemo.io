-- Execute com o dono das tabelas, antes de iniciar os servicos atualizados.
-- Preserva os dados existentes na clinica 1. Nenhum novo login herda esse vinculo.
BEGIN;
CREATE TABLE IF NOT EXISTS auth_schema_version (version INTEGER PRIMARY KEY, aplicado_em TIMESTAMPTZ NOT NULL DEFAULT now());
DO $migration$
DECLARE t record; k record; cols text; refs text; tenant_att smallint; tables text[]; ix text;
BEGIN
 IF EXISTS(SELECT 1 FROM auth_schema_version WHERE version=12) THEN RETURN; END IF;
 IF NOT EXISTS(SELECT 1 FROM clinica WHERE id_clinica=1) THEN RAISE EXCEPTION 'Clinica legada 1 ausente: defina o destino dos dados antes da migracao'; END IF;
 CREATE TABLE tenant_membro (
   id_clinica INTEGER NOT NULL REFERENCES clinica(id_clinica),
   id_usuario INTEGER NOT NULL REFERENCES usuario(id),
   perfil VARCHAR(30) NOT NULL CHECK(perfil IN ('administrador','medico')),
   ativo BOOLEAN NOT NULL DEFAULT true,
   PRIMARY KEY(id_clinica,id_usuario)
 );
 INSERT INTO tenant_membro(id_clinica,id_usuario,perfil) SELECT 1,id,perfil::text FROM usuario WHERE perfil IS NOT NULL;
 CREATE UNIQUE INDEX tenant_membro_usuario_ativo_uk ON tenant_membro(id_usuario) WHERE ativo;
 CREATE TABLE auth_email_identity (email VARCHAR(100) PRIMARY KEY, id_usuario INTEGER NOT NULL REFERENCES usuario(id) ON DELETE CASCADE);
 INSERT INTO auth_email_identity SELECT lower(email),id FROM usuario;
 -- E-mail de exibicao nao e identidade: provedores distintos podem informar o mesmo endereco.
 FOR k IN SELECT conname FROM pg_constraint WHERE conrelid='usuario'::regclass AND contype='u' LOOP
   EXECUTE format('ALTER TABLE usuario DROP CONSTRAINT %I',k.conname);
 END LOOP;
 ALTER TABLE usuario ALTER COLUMN email DROP NOT NULL;
 ALTER TABLE usuario ALTER COLUMN perfil DROP NOT NULL;
 ALTER TABLE usuario ALTER COLUMN perfil DROP DEFAULT;
 ALTER TABLE auth_session ADD COLUMN id_clinica INTEGER REFERENCES clinica(id_clinica);
 ALTER TABLE auth_magic_token ALTER COLUMN id_usuario DROP NOT NULL;
 CREATE TABLE tenant_convite (
   hash VARCHAR(64) PRIMARY KEY,
   id_clinica INTEGER NOT NULL REFERENCES clinica(id_clinica),
   email VARCHAR(100) NOT NULL,
   perfil VARCHAR(30) NOT NULL CHECK(perfil IN ('administrador','medico')),
   expira_em TIMESTAMPTZ NOT NULL,
   usado_em TIMESTAMPTZ,
   criado_por INTEGER NOT NULL REFERENCES usuario(id)
 );
 CREATE INDEX ON tenant_convite(id_clinica,email);
 SELECT array_agg(tablename) INTO tables FROM pg_tables
 WHERE schemaname='public' AND tablename NOT LIKE 'auth_%' AND tablename NOT LIKE 'tenant_%'
 AND tablename NOT IN ('usuario','clinica');
 -- O contexto e obrigatorio: sem contexto a RLS nao encontra linha alguma.
 FOR t IN SELECT unnest(tables) AS name LOOP
   EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS id_clinica INTEGER',t.name);
   EXECUTE format('UPDATE %I SET id_clinica=1 WHERE id_clinica IS NULL',t.name);
   EXECUTE format('ALTER TABLE %I ALTER COLUMN id_clinica SET NOT NULL',t.name);
   EXECUTE format('ALTER TABLE %I ALTER COLUMN id_clinica SET DEFAULT nullif(current_setting(''app.tenant_id'',true),'''')::integer',t.name);
   EXECUTE format('ALTER TABLE %I ADD CONSTRAINT tenant_owner_fk FOREIGN KEY(id_clinica) REFERENCES clinica(id_clinica)',t.name);
   EXECUTE format('CREATE INDEX %I ON %I(id_clinica)', 'tenant_scope_'||t.name,t.name);
 END LOOP;
 -- CPF, CRM e outras chaves de negocio pertencem ao tenant, nao ao sistema inteiro.
 FOR k IN SELECT c.*,cl.relname FROM pg_constraint c JOIN pg_class cl ON cl.oid=c.conrelid
   WHERE cl.relname=ANY(tables) AND c.contype='u' LOOP
   SELECT attnum INTO tenant_att FROM pg_attribute WHERE attrelid=k.conrelid AND attname='id_clinica';
   IF tenant_att=ANY(k.conkey) THEN CONTINUE; END IF;
   SELECT string_agg(quote_ident(a.attname),',' ORDER BY v.ord) INTO cols
     FROM unnest(k.conkey) WITH ORDINALITY v(num,ord) JOIN pg_attribute a ON a.attrelid=k.conrelid AND a.attnum=v.num;
   EXECUTE format('ALTER TABLE %I DROP CONSTRAINT %I',k.relname,k.conname);
   EXECUTE format('ALTER TABLE %I ADD CONSTRAINT %I UNIQUE(id_clinica,%s)',k.relname,k.conname,cols);
 END LOOP;
 -- FKs compostas impedem associar uma linha do tenant A a um paciente/consulta do B.
 FOR k IN SELECT c.*,cl.relname,pa.relname AS parent FROM pg_constraint c
   JOIN pg_class cl ON cl.oid=c.conrelid JOIN pg_class pa ON pa.oid=c.confrelid
   WHERE cl.relname=ANY(tables) AND c.contype='f' AND (pa.relname=ANY(tables) OR pa.relname='usuario') LOOP
   SELECT attnum INTO tenant_att FROM pg_attribute WHERE attrelid=k.conrelid AND attname='id_clinica';
   IF tenant_att=ANY(k.conkey) THEN CONTINUE; END IF;
   SELECT string_agg(quote_ident(a.attname),',' ORDER BY v.ord) INTO cols
     FROM unnest(k.conkey) WITH ORDINALITY v(num,ord) JOIN pg_attribute a ON a.attrelid=k.conrelid AND a.attnum=v.num;
   SELECT string_agg(quote_ident(a.attname),',' ORDER BY v.ord) INTO refs
     FROM unnest(k.confkey) WITH ORDINALITY v(num,ord) JOIN pg_attribute a ON a.attrelid=k.confrelid AND a.attnum=v.num;
   IF k.parent='usuario' THEN
     EXECUTE format('ALTER TABLE %I ADD CONSTRAINT %I FOREIGN KEY(id_clinica,%s) REFERENCES tenant_membro(id_clinica,id_usuario)',k.relname,'tenant_fk_'||k.oid,cols);
   ELSE
     ix:='tenant_ref_'||md5(k.parent||refs);
     EXECUTE format('CREATE UNIQUE INDEX IF NOT EXISTS %I ON %I(id_clinica,%s)',ix,k.parent,refs);
     EXECUTE format('ALTER TABLE %I ADD CONSTRAINT %I FOREIGN KEY(id_clinica,%s) REFERENCES %I(id_clinica,%s)',k.relname,'tenant_fk_'||k.oid,cols,k.parent,refs);
   END IF;
 END LOOP;
 FOR t IN SELECT unnest(tables) AS name LOOP
   EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',t.name);
   EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY',t.name);
   EXECUTE format('CREATE POLICY tenant_isolation ON %I USING(id_clinica=nullif(current_setting(''app.tenant_id'',true),'''')::integer) WITH CHECK(id_clinica=nullif(current_setting(''app.tenant_id'',true),'''')::integer)',t.name);
 END LOOP;
 INSERT INTO auth_schema_version(version) VALUES(12);
END $migration$;
COMMIT;
