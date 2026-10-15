"""Teste transacional da migração da fila diária; não altera dados da clínica.
Executar: chatbot/.venv/Scripts/python.exe database/tests/test_pos_venda_fila_diaria.py
"""
import pathlib
import uuid

import psycopg
from psycopg import sql


migration = (pathlib.Path(__file__).parents[1] / 'migrations' / '019_pos_venda_fila_diaria.sql').read_text(encoding='utf-8')
migration = migration.replace('\nBEGIN;\n', '\n').replace('\nCOMMIT;', '')
conn = psycopg.connect(host='localhost', port=5432, dbname='clinica', user='clinica', password='clinica', connect_timeout=5)
try:
    with conn.cursor() as cursor:
        schema = 'test_fila_diaria_' + uuid.uuid4().hex
        cursor.execute(sql.SQL('CREATE SCHEMA {}').format(sql.Identifier(schema)))
        cursor.execute(sql.SQL('SET LOCAL search_path TO {}').format(sql.Identifier(schema)))
        cursor.execute('''
            CREATE TABLE auth_schema_version(version INTEGER PRIMARY KEY);
            INSERT INTO auth_schema_version VALUES (18);
            CREATE TABLE clinica(id_clinica INT PRIMARY KEY);
            INSERT INTO clinica VALUES (1),(2);
            CREATE TABLE pos_venda_caso(
              id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
              id_clinica INT NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer,
              status TEXT NOT NULL,
              id_responsavel INT,
              proxima_acao TIMESTAMPTZ,
              UNIQUE(id_clinica,id)
            );
            CREATE TABLE pos_venda_evento(
              id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
              id_clinica INT NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer,
              id_caso BIGINT NOT NULL,
              tipo VARCHAR(40) NOT NULL,
              descricao TEXT,
              criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
            );
        ''')
        cursor.execute(migration)
        cursor.execute("SELECT set_config('app.tenant_id','1',true)")
        case_id = cursor.execute("INSERT INTO pos_venda_caso(status) VALUES ('pendente') RETURNING id").fetchone()[0]
        cursor.execute("INSERT INTO pos_venda_alerta(id_caso,tipo,mensagem) VALUES (%s,'sem_responsavel','Organizar')", (case_id,))
        cursor.execute('SAVEPOINT duplicate_guard')
        try:
            cursor.execute("INSERT INTO pos_venda_alerta(id_caso,tipo,mensagem) VALUES (%s,'sem_responsavel','Duplicado')", (case_id,))
            raise AssertionError('Alerta aberto repetido deveria ser rejeitado')
        except psycopg.errors.UniqueViolation:
            cursor.execute('ROLLBACK TO SAVEPOINT duplicate_guard')
        cursor.execute("UPDATE pos_venda_alerta SET status='resolvido',resolvido_em=now() WHERE id_caso=%s", (case_id,))
        cursor.execute("INSERT INTO pos_venda_alerta(id_caso,tipo,mensagem) VALUES (%s,'sem_responsavel','Reaberto')", (case_id,))
        assert cursor.execute("SELECT count(*) FROM pos_venda_alerta WHERE status='aberto'").fetchone()[0] == 1
        cursor.execute("SELECT set_config('app.tenant_id','2',true)")
        assert cursor.execute('SELECT count(*) FROM pos_venda_alerta').fetchone()[0] == 0
        print('OK: alertas persistem, não duplicam, podem reabrir e respeitam o tenant.')
finally:
    conn.rollback()
    conn.close()
