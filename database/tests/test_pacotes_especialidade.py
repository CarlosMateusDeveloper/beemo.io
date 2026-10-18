"""Valida a migração 028 em PostgreSQL real dentro de uma transação."""

import os
import pathlib
import uuid

import psycopg
from psycopg import sql


root = pathlib.Path(__file__).resolve().parents[1]
migration = (root / "migrations" / "028_pacotes_especialidade.sql").read_text(encoding="utf-8")
migration = migration.replace("\nBEGIN;\n", "\n").replace("\nCOMMIT;", "")

conn = psycopg.connect(
    host="localhost",
    port=int(os.getenv("CLINISIS_TEST_DB_PORT", "55440")),
    dbname="postgres",
    user="postgres",
    connect_timeout=5,
)
try:
    with conn.cursor() as cursor:
        schema = "test_specialty_" + uuid.uuid4().hex
        cursor.execute(sql.SQL("CREATE SCHEMA {}").format(sql.Identifier(schema)))
        cursor.execute(sql.SQL("SET LOCAL search_path TO {}").format(sql.Identifier(schema)))
        cursor.execute("""
            CREATE TABLE auth_schema_version(version INTEGER PRIMARY KEY);
            CREATE TABLE clinica(id_clinica INTEGER PRIMARY KEY);
            CREATE TABLE usuario(id INTEGER PRIMARY KEY,nome TEXT NOT NULL);
            CREATE TABLE tenant_membro(
              id_clinica INTEGER NOT NULL,id_usuario INTEGER NOT NULL,ativo BOOLEAN NOT NULL DEFAULT true,
              PRIMARY KEY(id_clinica,id_usuario)
            );
            INSERT INTO clinica VALUES (1),(2);
            INSERT INTO usuario VALUES (10,'Médica A'),(20,'Médica B');
            INSERT INTO tenant_membro VALUES (1,10,true),(2,20,true);
        """)
        cursor.execute(migration)
        cursor.execute("SELECT set_config('app.tenant_id','1',true)")
        package_id = cursor.execute("""
            INSERT INTO especialidade_pacote(codigo,nome,especialidade,descricao)
            VALUES ('odonto','Odontologia','Odontologia','Teste') RETURNING id_pacote
        """).fetchone()[0]
        version_id = cursor.execute("""
            INSERT INTO especialidade_pacote_versao(id_pacote,versao,status,componentes,id_revisor,
              registro_revisor,revisado_em,publicado_em)
            VALUES (%s,1,'PUBLICADA','[{"chave":"retorno"}]',10,'CRO 123',now(),now()) RETURNING id_versao
        """, (package_id,)).fetchone()[0]
        application_id = cursor.execute("""
            INSERT INTO especialidade_pacote_aplicacao(id_pacote,id_versao,componentes_selecionados,
              politica_comunicacao_aprovada,id_aplicado_por)
            VALUES (%s,%s,'["retorno"]',true,10) RETURNING id_aplicacao
        """, (package_id,version_id)).fetchone()[0]
        cursor.execute("""
            INSERT INTO especialidade_componente_aplicado(id_aplicacao,chave,tipo,configuracao_base,
              configuracao_local,versao_origem,personalizado)
            VALUES (%s,'retorno','MODELO_ATENDIMENTO','{"duracaoMinutos":30}',
              '{"duracaoMinutos":45}',1,true)
        """, (application_id,))
        saved = cursor.execute("""
            SELECT configuracao_base->>'duracaoMinutos',configuracao_local->>'duracaoMinutos',personalizado
            FROM especialidade_componente_aplicado WHERE id_aplicacao=%s
        """, (application_id,)).fetchone()
        assert saved == ("30", "45", True), saved
        assert cursor.execute("SELECT 1 FROM auth_schema_version WHERE version=28").fetchone() == (1,)
    print("OK: versão, aplicação parcial, personalização e esquema da migração 028.")
finally:
    conn.rollback()
    conn.close()
