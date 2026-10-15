"""Executa a migração 023 em schema isolado de um PostgreSQL real."""

import os
from pathlib import Path
import unittest
import uuid

import psycopg

ROOT = Path(__file__).resolve().parents[2]
DATABASE_URL = os.getenv("TEST_DATABASE_URL", "postgresql://clinica:clinica@localhost:5432/clinica")


class MensageriaMigrationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.conn = psycopg.connect(DATABASE_URL, autocommit=True)
        cls.schema = "mensageria_test_" + uuid.uuid4().hex
        try:
            with cls.conn.cursor() as cur:
                cur.execute(f"CREATE SCHEMA {cls.schema}")
                cur.execute(f"SET search_path TO {cls.schema}")
                cur.execute("""
                CREATE TABLE clinica(id_clinica integer PRIMARY KEY);
                CREATE TABLE tenant_membro(id_clinica integer NOT NULL,id_usuario integer NOT NULL,
                    PRIMARY KEY(id_clinica,id_usuario));
                CREATE TABLE mensagem(id_mensagem bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    id_clinica integer NOT NULL, UNIQUE(id_clinica,id_mensagem));
                CREATE TABLE auth_schema_version(version integer PRIMARY KEY);
                INSERT INTO clinica VALUES (1),(2);
                INSERT INTO tenant_membro VALUES (1,10),(2,20);
                """)
                migration = ROOT / "database" / "migrations" / "023_mensageria_confiavel.sql"
                cur.execute(migration.read_text(encoding="utf-8"))
        except Exception:
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

    def test_schema_has_rls_and_operational_index(self):
        with self.conn.cursor() as cur:
            cur.execute("SELECT EXISTS(SELECT 1 FROM auth_schema_version WHERE version=23)")
            self.assertTrue(cur.fetchone()[0])
            cur.execute("SELECT relrowsecurity,relforcerowsecurity FROM pg_class WHERE oid='mensageria_outbox'::regclass")
            self.assertEqual((True, True), cur.fetchone())
            cur.execute("SELECT count(*) FROM pg_indexes WHERE tablename='mensageria_outbox' AND indexname='mensageria_outbox_aquisicao_idx'")
            self.assertEqual(1, cur.fetchone()[0])

    def test_idempotency_key_is_unique_per_tenant(self):
        insert = """
            INSERT INTO mensageria_outbox(idempotency_key,canal,provedor,destino,tipo,payload,origem)
            VALUES ('request-1','whatsapp','meta','5585999999999','texto','{"texto":"Oi"}','teste')
            ON CONFLICT(id_clinica,idempotency_key) DO NOTHING
        """
        with self.conn.cursor() as cur:
            cur.execute(insert); cur.execute(insert)
            cur.execute("SELECT count(*) FROM mensageria_outbox WHERE idempotency_key='request-1'")
            self.assertEqual(1, cur.fetchone()[0])

    def test_event_history_is_immutable(self):
        with self.conn.cursor() as cur:
            cur.execute("""
                INSERT INTO mensageria_outbox(idempotency_key,canal,provedor,destino,tipo,payload,origem)
                VALUES ('request-event','whatsapp','meta','5585888888888','texto','{}','teste') RETURNING id_outbox
            """)
            outbox = cur.fetchone()[0]
            cur.execute("INSERT INTO mensageria_evento(id_outbox,tipo) VALUES (%s,'enfileirada') RETURNING id_evento", (outbox,))
            event = cur.fetchone()[0]
            with self.assertRaises(psycopg.errors.RaiseException):
                cur.execute("DELETE FROM mensageria_evento WHERE id_evento=%s", (event,))


if __name__ == "__main__":
    unittest.main()
