"""Teste transacional da estrutura da API de integração; não altera dados da clínica."""
import hashlib
import pathlib
import uuid

import psycopg
from psycopg import sql


root = pathlib.Path(__file__).resolve().parents[1]


def migration(name):
    text = (root / "migrations" / name).read_text(encoding="utf-8")
    return text.replace("\nBEGIN;\n", "\n").replace("\nCOMMIT;", "")


conn = psycopg.connect(
    host="localhost", port=5432, dbname="clinica", user="clinica", password="clinica", connect_timeout=5
)
try:
    with conn.cursor() as cursor:
        schema = "test_integracao_api_" + uuid.uuid4().hex
        cursor.execute(sql.SQL("CREATE SCHEMA {}").format(sql.Identifier(schema)))
        cursor.execute(sql.SQL("SET LOCAL search_path TO {}").format(sql.Identifier(schema)))
        cursor.execute("""
            CREATE TABLE auth_schema_version(version INTEGER PRIMARY KEY);
            INSERT INTO auth_schema_version VALUES (17);
            CREATE TABLE clinica(id_clinica INT PRIMARY KEY,nome TEXT);
            CREATE TABLE usuario(id INT PRIMARY KEY,nome TEXT);
            CREATE TABLE tenant_membro(id_clinica INT,id_usuario INT,ativo BOOLEAN DEFAULT true,
              PRIMARY KEY(id_clinica,id_usuario));
            INSERT INTO clinica VALUES (1,'Clínica A'),(2,'Clínica B');
            INSERT INTO usuario VALUES (1,'Administrador A'),(2,'Administrador B');
            INSERT INTO tenant_membro VALUES (1,1,true),(2,2,true);
            CREATE TABLE pos_venda_caso(
              id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
              id_clinica INT NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer,
              id_consulta_origem INT UNIQUE,id_consulta_reagendada INT,data_falta TIMESTAMP NOT NULL,
              status VARCHAR(30) NOT NULL DEFAULT 'pendente',origem_legada TEXT,id_responsavel INT,
              proxima_acao TIMESTAMPTZ,motivo_falta TEXT,recorrente BOOLEAN NOT NULL DEFAULT false,
              versao INT NOT NULL DEFAULT 0,criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
              atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now(),UNIQUE(id_clinica,id)
            );
            CREATE TABLE pos_venda_evento(
              id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
              id_clinica INT NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer,
              id_caso BIGINT NOT NULL,id_usuario INT,tipo VARCHAR(40) NOT NULL,canal VARCHAR(20),
              descricao TEXT NOT NULL,criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
            );
            ALTER TABLE pos_venda_caso ENABLE ROW LEVEL SECURITY;
            ALTER TABLE pos_venda_caso FORCE ROW LEVEL SECURITY;
            CREATE POLICY tenant_isolation ON pos_venda_caso
              USING(id_clinica=nullif(current_setting('app.tenant_id',true),'')::integer)
              WITH CHECK(id_clinica=nullif(current_setting('app.tenant_id',true),'')::integer);
            ALTER TABLE pos_venda_evento ENABLE ROW LEVEL SECURITY;
            ALTER TABLE pos_venda_evento FORCE ROW LEVEL SECURITY;
            CREATE POLICY tenant_isolation ON pos_venda_evento
              USING(id_clinica=nullif(current_setting('app.tenant_id',true),'')::integer)
              WITH CHECK(id_clinica=nullif(current_setting('app.tenant_id',true),'')::integer);
        """)
        for name in (
            "018_pos_venda_importacao_csv.sql",
            "019_pos_venda_fila_diaria.sql",
            "020_pos_venda_conciliacao_externa.sql",
            "021_integracao_api_clinicas.sql",
        ):
            cursor.execute(migration(name))

        cursor.execute("SELECT set_config('app.tenant_id','1',true)")
        source = cursor.execute("""
            INSERT INTO integracao_fonte_dados(codigo,nome,fuso_horario,adaptador)
            VALUES ('piloto-api','Sistema piloto','America/Fortaleza','clinisis-standard-v1')
            RETURNING id_fonte
        """).fetchone()[0]
        cursor.execute("""
            INSERT INTO integracao_situacao_mapeamento(
              id_fonte,situacao_chave,situacao_origem,situacao_normalizada
            ) VALUES
              (%s,'faltou','Faltou','FALTOU'),
              (%s,'agendado','Agendado','AGENDADO'),
              (%s,'realizado','Realizado','REALIZADO')
        """, (source, source, source))

        raw_key = "cls.identificador.segredo-com-alta-entropia"
        credential_hash = hashlib.sha256(raw_key.encode()).hexdigest()
        credential = cursor.execute("""
            INSERT INTO auth_integracao_credencial(
              identificador,segredo_hash,id_clinica,id_fonte,nome,criado_por
            ) VALUES ('identificador',%s,1,%s,'Conector piloto',1) RETURNING id_credencial
        """, (credential_hash, source)).fetchone()[0]

        # A credencial mínima pode ser localizada antes de o tenant ser conhecido.
        cursor.execute("SELECT set_config('app.tenant_id','',true)")
        auth = cursor.execute("""
            SELECT id_clinica,id_fonte,segredo_hash FROM auth_integracao_credencial
            WHERE identificador='identificador' AND revogada_em IS NULL
        """).fetchone()
        assert auth == (1, source, credential_hash), auth

        cursor.execute("SELECT set_config('app.tenant_id','1',true)")
        patient = cursor.execute("""
            INSERT INTO integracao_paciente_externo(
              id_fonte,identificador_externo,nome,telefone,atualizado_em_fonte
            ) VALUES (%s,'P-1','Paciente fictício','85999999999',now())
            RETURNING id_paciente_externo
        """, (source,)).fetchone()[0]
        origin = cursor.execute("""
            INSERT INTO integracao_atendimento_externo(
              id_fonte,id_paciente_externo,identificador_externo,agendado_em,fuso_horario,
              profissional,especialidade,tipo,situacao_origem,situacao_normalizada,
              atualizado_em_fonte,id_importacao
            ) VALUES (%s,%s,'A-ORIGEM',now()-interval '1 day','America/Fortaleza',
              'Dra. Exemplo','Clínica geral','Consulta','Faltou','FALTOU',now(),NULL)
            RETURNING id_atendimento_externo
        """, (source, patient)).fetchone()[0]
        case_id, case_origin = cursor.execute("""
            SELECT id,origem FROM pos_venda_caso WHERE id_atendimento_externo_origem=%s
        """, (origin,)).fetchone()
        assert case_origin == "integracao_api"

        rebook = cursor.execute("""
            INSERT INTO integracao_atendimento_externo(
              id_fonte,id_paciente_externo,identificador_externo,agendado_em,fuso_horario,
              profissional,especialidade,tipo,situacao_origem,situacao_normalizada,
              atualizado_em_fonte,id_importacao
            ) VALUES (%s,%s,'A-REAGENDADO',now()+interval '1 day','America/Fortaleza',
              'Dra. Exemplo','Clínica geral','Consulta','Agendado','AGENDADO',now(),NULL)
            RETURNING id_atendimento_externo
        """, (source, patient)).fetchone()[0]
        cursor.execute("""
            INSERT INTO pos_venda_atendimento_externo(id_atendimento_externo,id_caso) VALUES (%s,%s)
        """, (rebook, case_id))
        cursor.execute("""
            UPDATE pos_venda_caso SET status='reagendado',id_atendimento_externo_reagendado=%s
            WHERE id=%s
        """, (rebook, case_id))
        cursor.execute("""
            UPDATE integracao_atendimento_externo SET situacao_origem='Realizado',
              situacao_normalizada='REALIZADO',atualizado_em_fonte=now()+interval '2 minutes'
            WHERE id_atendimento_externo=%s
        """, (rebook,))
        assert cursor.execute("SELECT status FROM pos_venda_caso WHERE id=%s", (case_id,)).fetchone()[0] == "recuperado"

        payload_hash = "b" * 64
        event = cursor.execute("""
            INSERT INTO integracao_evento(
              id_fonte,id_credencial,identificador_externo,tipo,versao,ocorrido_em,
              atualizado_em_fonte,payload_hash,payload,resultado,processado_em
            ) VALUES (%s,%s,'evt-1','SITUACAO_ATENDIMENTO_ATUALIZADA',1,now(),now(),
              %s,'{}'::jsonb,'PROCESSADO',now()) RETURNING id_evento
        """, (source, credential, payload_hash)).fetchone()[0]
        cursor.execute("SAVEPOINT duplicate_event")
        try:
            cursor.execute("""
                INSERT INTO integracao_evento(
                  id_fonte,identificador_externo,tipo,versao,ocorrido_em,atualizado_em_fonte,
                  payload_hash,payload,resultado
                ) VALUES (%s,'evt-1','PACIENTE_ATUALIZADO',1,now(),now(),%s,'{}','PROCESSANDO')
            """, (source, payload_hash))
            raise AssertionError("eventoId repetido deveria ser rejeitado")
        except psycopg.errors.UniqueViolation:
            cursor.execute("ROLLBACK TO SAVEPOINT duplicate_event")

        # O outro tenant não vê fonte, mapeamento, eventos nem dados clínicos.
        cursor.execute("SELECT set_config('app.tenant_id','2',true)")
        for table in (
            "integracao_fonte_dados", "integracao_situacao_mapeamento", "integracao_evento",
            "integracao_paciente_externo", "integracao_atendimento_externo", "pos_venda_caso",
        ):
            assert cursor.execute(f"SELECT count(*) FROM {table}").fetchone()[0] == 0, table

        # Revogação é independente do RLS porque a autenticação ocorre antes do tenant.
        cursor.execute("UPDATE auth_integracao_credencial SET revogada_em=now() WHERE id_credencial=%s", (credential,))
        assert cursor.execute("""
            SELECT count(*) FROM auth_integracao_credencial
            WHERE identificador='identificador' AND revogada_em IS NULL
        """).fetchone()[0] == 0
    print("OK: credencial, RLS, idempotencia e falta -> reagendamento -> presenca via API.")
finally:
    conn.rollback()
    conn.close()
