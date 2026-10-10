-- Issue #16: papéis cumulativos, permissões por ação, vínculo do médico e auditoria.
-- Aplicar depois de 012_tenant_identity_isolation.sql e 013_single_tenant_account.sql.
BEGIN;

DO $migration$
BEGIN
 IF EXISTS(SELECT 1 FROM auth_schema_version WHERE version=15) THEN RETURN; END IF;

 ALTER TABLE tenant_membro DROP CONSTRAINT IF EXISTS tenant_membro_perfil_check;
 ALTER TABLE tenant_membro ADD CONSTRAINT tenant_membro_perfil_check
   CHECK(perfil IN ('recepcionista','medico','financeiro','administrador'));
 ALTER TABLE tenant_membro ADD COLUMN IF NOT EXISTS id_medico INTEGER;

 ALTER TABLE tenant_convite DROP CONSTRAINT IF EXISTS tenant_convite_perfil_check;
 ALTER TABLE tenant_convite ADD CONSTRAINT tenant_convite_perfil_check
   CHECK(perfil IN ('recepcionista','medico','financeiro','administrador'));

 CREATE TABLE tenant_membro_papel (
   id_clinica INTEGER NOT NULL,
   id_usuario INTEGER NOT NULL,
   papel VARCHAR(30) NOT NULL CHECK(papel IN ('recepcionista','medico','financeiro','administrador')),
   PRIMARY KEY(id_clinica,id_usuario,papel),
   FOREIGN KEY(id_clinica,id_usuario) REFERENCES tenant_membro(id_clinica,id_usuario) ON DELETE CASCADE
 );
 INSERT INTO tenant_membro_papel(id_clinica,id_usuario,papel)
 SELECT id_clinica,id_usuario,perfil FROM tenant_membro ON CONFLICT DO NOTHING;

 CREATE TABLE tenant_membro_permissao (
   id_clinica INTEGER NOT NULL,
   id_usuario INTEGER NOT NULL,
   permissao VARCHAR(100) NOT NULL,
   permitido BOOLEAN NOT NULL,
   PRIMARY KEY(id_clinica,id_usuario,permissao),
   FOREIGN KEY(id_clinica,id_usuario) REFERENCES tenant_membro(id_clinica,id_usuario) ON DELETE CASCADE
 );

 CREATE TABLE auth_audit_log (
   id BIGSERIAL PRIMARY KEY,
   id_clinica INTEGER NOT NULL REFERENCES clinica(id_clinica),
   id_usuario INTEGER REFERENCES usuario(id),
   acao VARCHAR(100) NOT NULL,
   recurso VARCHAR(255) NOT NULL,
   metodo VARCHAR(10) NOT NULL,
   resultado INTEGER NOT NULL,
   endereco_ip VARCHAR(64),
   criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
 );
 CREATE INDEX auth_audit_log_tenant_data_idx ON auth_audit_log(id_clinica,criado_em DESC);

 -- O índice composto é criado pela migração 012 e permite validar que o médico pertence ao tenant.
 IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conname='tenant_membro_medico_fk') THEN
   ALTER TABLE tenant_membro ADD CONSTRAINT tenant_membro_medico_fk
     FOREIGN KEY(id_clinica,id_medico) REFERENCES medico(id_clinica,id_medico);
 END IF;

 INSERT INTO auth_schema_version(version) VALUES (15);
END $migration$;

COMMIT;
