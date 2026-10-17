"""Teste transacional da migração 027; não altera os dados reais da clínica."""

import pathlib
import uuid
import psycopg
from psycopg import sql

root = pathlib.Path(__file__).resolve().parents[1]
migration = (root / "migrations" / "027_crm_conversao.sql").read_text(encoding="utf-8")
migration = migration.replace("\nBEGIN;\n", "\n").replace("\nCOMMIT;", "")

conn = psycopg.connect(host="localhost", port=5432, dbname="clinica", user="clinica", password="clinica", connect_timeout=5)
try:
    with conn.cursor() as cur:
        schema = "test_crm_" + uuid.uuid4().hex
        cur.execute(sql.SQL("CREATE SCHEMA {}").format(sql.Identifier(schema)))
        cur.execute(sql.SQL("SET LOCAL search_path TO {}").format(sql.Identifier(schema)))
        cur.execute("""
            CREATE TYPE situacao_agenda AS ENUM ('Livre','Ocupado','Bloqueado');
            CREATE TYPE status_consulta_enum AS ENUM ('Agendada','Confirmada','Em Espera','Em Atendimento','Realizada','Cancelada','Faltou');
            CREATE TABLE auth_schema_version(version integer primary key);
            CREATE TABLE clinica(id_clinica integer primary key);
            CREATE TABLE usuario(id integer primary key,nome text);
            CREATE TABLE tenant_membro(id_clinica integer,id_usuario integer,ativo boolean default true,primary key(id_clinica,id_usuario));
            CREATE TABLE paciente(id_paciente integer generated always as identity primary key,id_clinica integer not null,
              nome text,cpf text,data_nascimento date,ddd text,numero text,email text,unique(id_clinica,id_paciente));
            CREATE TABLE agenda(id_agenda integer generated always as identity primary key,id_clinica integer not null,
              id_medico integer,situacao situacao_agenda,data_slot date,hora_slot time,unique(id_clinica,id_agenda));
            CREATE TABLE consulta(id_consulta integer generated always as identity primary key,id_clinica integer not null,
              id_paciente integer,id_agenda integer,status_consulta status_consulta_enum,unique(id_clinica,id_consulta));
            CREATE TABLE mensagem(id_mensagem bigint generated always as identity primary key,id_clinica integer not null,
              direcao text,remetente text,conteudo text,status_entrega text,criado_em timestamptz default now(),unique(id_clinica,id_mensagem));
            INSERT INTO clinica VALUES (1),(2); INSERT INTO usuario VALUES (10,'A'),(20,'B');
            INSERT INTO tenant_membro VALUES (1,10,true),(2,20,true);
        """)
        cur.execute(migration)
        cur.execute("SELECT set_config('app.tenant_id','1',true)")
        interested = cur.execute("INSERT INTO crm_interessado(nome,telefone) VALUES ('Ana','5585999999999') RETURNING id_interessado").fetchone()[0]
        second = cur.execute("INSERT INTO crm_interessado(nome,telefone) VALUES ('Bruno','5585999999999') RETURNING id_interessado").fetchone()[0]
        assert interested != second, "telefone compartilhado não pode fundir interessados"
        opportunity = cur.execute("""
          INSERT INTO crm_oportunidade(id_interessado,canal_origem,origem,servico_interesse,idempotency_key,criado_por,atualizado_por)
          VALUES (%s,'whatsapp','WhatsApp','Dermatologia','wamid.1',10,10) RETURNING id_oportunidade
        """, (interested,)).fetchone()[0]
        cur.execute("UPDATE crm_oportunidade SET etapa='em_contato' WHERE id_oportunidade=%s", (opportunity,))
        assert cur.execute("SELECT versao FROM crm_oportunidade WHERE id_oportunidade=%s", (opportunity,)).fetchone() == (1,)
        cur.execute("SAVEPOINT invalid_loss")
        try:
            cur.execute("UPDATE crm_oportunidade SET etapa='perdido' WHERE id_oportunidade=%s", (opportunity,))
            raise AssertionError("perda sem motivo deveria falhar")
        except psycopg.errors.CheckViolation:
            cur.execute("ROLLBACK TO SAVEPOINT invalid_loss")
        patient = cur.execute("""
          INSERT INTO paciente(id_clinica,nome,cpf,data_nascimento,ddd,numero)
          VALUES (1,'Ana','00000000001','1990-01-01','85','999999999') RETURNING id_paciente
        """).fetchone()[0]
        agenda = cur.execute("""
          INSERT INTO agenda(id_clinica,id_medico,situacao,data_slot,hora_slot)
          VALUES (1,1,'Ocupado',current_date+1,'09:00') RETURNING id_agenda
        """).fetchone()[0]
        consultation = cur.execute("""
          INSERT INTO consulta(id_clinica,id_paciente,id_agenda,status_consulta)
          VALUES (1,%s,%s,'Agendada') RETURNING id_consulta
        """, (patient, agenda)).fetchone()[0]
        cur.execute("""
          UPDATE crm_oportunidade SET etapa='agendado',id_paciente=%s,id_consulta=%s
          WHERE id_oportunidade=%s
        """, (patient, consultation, opportunity))
        cur.execute("UPDATE consulta SET status_consulta='Cancelada' WHERE id_consulta=%s", (consultation,))
        assert cur.execute("""
          SELECT count(*) FROM crm_oportunidade_evento
          WHERE id_oportunidade=%s AND tipo='cancelamento'
        """, (opportunity,)).fetchone()[0] == 1
        assert cur.execute("SELECT count(*) FROM crm_oportunidade").fetchone()[0] == 1
        cur.execute("SELECT set_config('app.tenant_id','2',true)")
        assert cur.execute("SELECT count(*) FROM crm_oportunidade").fetchone()[0] == 0
        assert cur.execute("SELECT 1 FROM auth_schema_version WHERE version=27").fetchone() == (1,)
    print("OK: CRM preserva identidade, versão, perda obrigatória, idempotência e RLS.")
finally:
    conn.rollback()
    conn.close()
