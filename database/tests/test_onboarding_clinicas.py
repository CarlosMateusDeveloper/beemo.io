"""Teste transacional da migração 026; não altera os dados reais da clínica."""

import pathlib
import uuid

import psycopg
from psycopg import sql


root = pathlib.Path(__file__).resolve().parents[1]
migration = (root / "migrations" / "026_onboarding_clinicas.sql").read_text(encoding="utf-8")
migration = migration.replace("\nBEGIN;\n", "\n").replace("\nCOMMIT;", "")

conn = psycopg.connect(
    host="localhost", port=5432, dbname="clinica", user="clinica", password="clinica", connect_timeout=5
)
try:
    with conn.cursor() as cursor:
        schema = "test_onboarding_" + uuid.uuid4().hex
        cursor.execute(sql.SQL("CREATE SCHEMA {}").format(sql.Identifier(schema)))
        cursor.execute(sql.SQL("SET LOCAL search_path TO {}").format(sql.Identifier(schema)))
        cursor.execute("""
            CREATE TABLE auth_schema_version(version INTEGER PRIMARY KEY);
            INSERT INTO auth_schema_version VALUES (25);
            CREATE TABLE clinica(id_clinica INTEGER PRIMARY KEY, nome TEXT);
            CREATE TABLE usuario(id INTEGER PRIMARY KEY, nome TEXT);
            CREATE TABLE tenant_membro(
              id_clinica INTEGER NOT NULL,
              id_usuario INTEGER NOT NULL,
              ativo BOOLEAN NOT NULL DEFAULT true,
              PRIMARY KEY(id_clinica,id_usuario)
            );
            CREATE TABLE integracao_importacao(
              id_importacao BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
              id_clinica INTEGER NOT NULL
                DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer,
              nome_arquivo TEXT NOT NULL,
              arquivo_hash TEXT NOT NULL,
              status TEXT NOT NULL DEFAULT 'CONCLUIDA',
              UNIQUE(id_clinica,id_importacao)
            );
            INSERT INTO clinica VALUES (1,'Clínica A'),(2,'Clínica B');
            INSERT INTO usuario VALUES (10,'Admin A'),(20,'Admin B');
            INSERT INTO tenant_membro VALUES (1,10,true),(2,20,true);
        """)
        cursor.execute(migration)

        cursor.execute("SELECT set_config('app.tenant_id','1',true)")
        cursor.execute("""
            INSERT INTO onboarding_clinica(
              id_responsavel,fuso_horario,agenda_inicio,agenda_fim,dias_atendimento,
              canais,politica_consentimento_confirmada,primeiro_fluxo
            ) VALUES (10,'America/Fortaleza','08:00','18:00','[1,2,3,4,5]',
              '["whatsapp","email"]',true,'faltas')
        """)
        cursor.execute("""
            INSERT INTO onboarding_evento(id_usuario,tipo,detalhes)
            VALUES (10,'CONFIGURACAO_ATUALIZADA','{"origem":"teste"}')
        """)
        cursor.execute("UPDATE onboarding_clinica SET status='CONCLUIDO'")
        assert cursor.execute("SELECT versao,status FROM onboarding_clinica").fetchone() == (1, "CONCLUIDO")

        import_id = cursor.execute("""
            INSERT INTO integracao_importacao(nome_arquivo,arquivo_hash)
            VALUES ('pacientes.csv',repeat('a',64)) RETURNING id_importacao
        """).fetchone()[0]
        cursor.execute("""
            UPDATE integracao_importacao
            SET rollback_status='CORRECAO_ASSISTIDA',
                rollback_relatorio='{"preservados":1,"removidos":2}',
                desfeita_em=now(),desfeita_por=10,
                arquivo_conteudo_criptografado=decode(repeat('ab',32),'hex'),
                arquivo_nonce=decode(repeat('cd',12),'hex'),
                arquivo_tamanho=128,arquivo_chave_versao=1
            WHERE id_importacao=%s
        """, (import_id,))
        rollback = cursor.execute("""
            SELECT rollback_status,rollback_relatorio->>'preservados',desfeita_por,
                   octet_length(arquivo_nonce),arquivo_chave_versao
            FROM integracao_importacao WHERE id_importacao=%s
        """, (import_id,)).fetchone()
        assert rollback == ("CORRECAO_ASSISTIDA", "1", 10, 12, 1), rollback

        cursor.execute("SELECT set_config('app.tenant_id','2',true)")
        assert cursor.execute("SELECT count(*) FROM onboarding_clinica").fetchone()[0] == 0
        assert cursor.execute("SELECT count(*) FROM onboarding_evento").fetchone()[0] == 0

        cursor.execute("SELECT set_config('app.tenant_id','1',true)")
        assert cursor.execute("SELECT count(*) FROM onboarding_clinica").fetchone()[0] == 1
        assert cursor.execute("SELECT count(*) FROM onboarding_evento").fetchone()[0] == 1
        assert cursor.execute("SELECT 1 FROM auth_schema_version WHERE version=26").fetchone() == (1,)
    print("OK: onboarding, auditoria, versionamento, rollback e RLS por tenant.")
finally:
    conn.rollback()
    conn.close()
