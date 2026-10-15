"""Executa a migração 024 em um schema PostgreSQL isolado."""

import os
from pathlib import Path
import unittest
import uuid

import psycopg

ROOT = Path(__file__).resolve().parents[2]
DATABASE_URL = os.getenv("TEST_DATABASE_URL", "postgresql://clinica:clinica@localhost:5432/clinica")


class WhatsAppMigrationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.conn = psycopg.connect(DATABASE_URL, autocommit=True)
        cls.schema = "whatsapp_test_" + uuid.uuid4().hex
        try:
            with cls.conn.cursor() as cur:
                cur.execute(f"CREATE SCHEMA {cls.schema}")
                cur.execute(f"SET search_path TO {cls.schema}")
                cur.execute("""
                    CREATE TABLE clinica(id_clinica integer PRIMARY KEY);
                    CREATE TABLE usuario(id integer PRIMARY KEY);
                    CREATE TABLE tenant_membro(id_clinica integer,id_usuario integer,ativo boolean default true,
                      PRIMARY KEY(id_clinica,id_usuario));
                    CREATE TABLE paciente(id_paciente integer PRIMARY KEY,id_clinica integer NOT NULL,
                      UNIQUE(id_clinica,id_paciente));
                    CREATE TABLE pos_venda_caso(id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                      id_clinica integer NOT NULL, UNIQUE(id_clinica,id));
                    CREATE TABLE pos_venda_jornada_inscricao(id_inscricao bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                      id_clinica integer NOT NULL, UNIQUE(id_clinica,id_inscricao));
                    CREATE TABLE pos_venda_campanha(id_campanha bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                      id_clinica integer NOT NULL, UNIQUE(id_clinica,id_campanha));
                    CREATE TABLE pos_venda_tarefa(id_tarefa bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                      id_clinica integer NOT NULL,origem varchar(160) NOT NULL);
                    CREATE TABLE mensageria_outbox(id_outbox bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                      id_clinica integer NOT NULL,id_campanha bigint,payload jsonb NOT NULL,tipo varchar(20) NOT NULL,
                      destino varchar(160) NOT NULL,criado_em timestamptz NOT NULL DEFAULT now(), UNIQUE(id_clinica,id_outbox));
                    CREATE TABLE mensagem(id_mensagem bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                      id_clinica integer NOT NULL,status_entrega varchar(16) NOT NULL DEFAULT 'enviada',
                      id_mensagem_provedor varchar(200),
                      criado_em timestamptz NOT NULL DEFAULT now(),
                      CONSTRAINT mensagem_status_entrega_check CHECK(status_entrega IN ('pendente','enviada','falha','recebida')),
                      UNIQUE(id_clinica,id_mensagem));
                    CREATE TABLE auth_schema_version(version integer PRIMARY KEY);
                    INSERT INTO clinica VALUES (1),(2);
                    INSERT INTO usuario VALUES (10),(20);
                    INSERT INTO tenant_membro(id_clinica,id_usuario) VALUES (1,10),(2,20);
                """)
                migration = ROOT / "database" / "migrations" / "024_whatsapp_bidirecional.sql"
                cur.execute(migration.read_text(encoding="utf-8"))
        except Exception:
            cls.conn.rollback()
            with cls.conn.cursor() as cur:
                cur.execute("SET search_path TO public")
                cur.execute(f"DROP SCHEMA IF EXISTS {cls.schema} CASCADE")
            cls.conn.close()
            raise

    @classmethod
    def tearDownClass(cls):
        with cls.conn.cursor() as cur:
            cur.execute("SET search_path TO public")
            cur.execute(f"DROP SCHEMA {cls.schema} CASCADE")
        cls.conn.close()

    def setUp(self):
        with self.conn.cursor() as cur:
            cur.execute(f"SET search_path TO {self.schema}")
            cur.execute("SET app.tenant_id TO '1'")

    def test_schema_enforces_tenant_and_delivery_state(self):
        with self.conn.cursor() as cur:
            cur.execute("SELECT EXISTS(SELECT 1 FROM auth_schema_version WHERE version=24)")
            self.assertTrue(cur.fetchone()[0])
            cur.execute("SELECT relrowsecurity,relforcerowsecurity FROM pg_class WHERE oid='whatsapp_integracao'::regclass")
            self.assertEqual((True, True), cur.fetchone())
            cur.execute("INSERT INTO mensagem(id_clinica,status_entrega) VALUES (1,'entregue'),(1,'lida'),(1,'cancelada')")
            with self.assertRaises(psycopg.errors.CheckViolation):
                cur.execute("INSERT INTO mensagem(id_clinica,status_entrega) VALUES (1,'desconhecida')")

    def test_provider_status_is_idempotent(self):
        with self.conn.cursor() as cur:
            sql = """INSERT INTO whatsapp_status_evento(id_mensagem_provedor,status,ocorrido_em)
                     VALUES ('wamid.1','entregue','2026-10-12T12:00:00Z')
                     ON CONFLICT(id_clinica,id_mensagem_provedor,status,ocorrido_em) DO NOTHING"""
            cur.execute(sql); cur.execute(sql)
            cur.execute("SELECT count(*) FROM whatsapp_status_evento WHERE id_mensagem_provedor='wamid.1'")
            self.assertEqual(1, cur.fetchone()[0])


if __name__ == "__main__":
    unittest.main()
