"""Valida a política central, o histórico imutável e o isolamento por tenant."""
import pathlib
import uuid

import psycopg
from psycopg import sql


root = pathlib.Path(__file__).resolve().parents[1]
migration = (root / "migrations" / "022_comunicacao_consentimento.sql").read_text(encoding="utf-8")
migration = migration.replace("\nBEGIN;\n", "\n").replace("\nCOMMIT;", "")

conn = psycopg.connect(host="localhost", port=5432, dbname="clinica", user="clinica", password="clinica", connect_timeout=5)
try:
    with conn.cursor() as cursor:
        schema = "test_comunicacao_" + uuid.uuid4().hex
        cursor.execute(sql.SQL("CREATE SCHEMA {}").format(sql.Identifier(schema)))
        cursor.execute(sql.SQL("SET LOCAL search_path TO {}").format(sql.Identifier(schema)))
        cursor.execute("""
            CREATE TABLE auth_schema_version(version INTEGER PRIMARY KEY);
            CREATE TABLE clinica(id_clinica INT PRIMARY KEY,nome TEXT);
            CREATE TABLE usuario(id INT PRIMARY KEY,nome TEXT);
            CREATE TABLE tenant_membro(id_clinica INT,id_usuario INT,ativo BOOLEAN DEFAULT true,
              PRIMARY KEY(id_clinica,id_usuario));
            CREATE TABLE tenant_membro_papel(id_clinica INT,id_usuario INT,papel TEXT,
              PRIMARY KEY(id_clinica,id_usuario,papel));
            CREATE TABLE paciente(id_paciente INT PRIMARY KEY,id_clinica INT NOT NULL,nome TEXT,
              UNIQUE(id_clinica,id_paciente));
            CREATE TABLE integracao_paciente_externo(id_paciente_externo BIGINT PRIMARY KEY,id_clinica INT NOT NULL,
              UNIQUE(id_clinica,id_paciente_externo));
            CREATE TABLE paciente_retorno_status(id_clinica INT NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::int,
              id_paciente INT,status TEXT,motivo_nao_contatar TEXT,atualizado_em TIMESTAMPTZ,
              UNIQUE(id_clinica,id_paciente));
            CREATE TABLE mensagem(id_clinica INT NOT NULL,id_mensagem BIGINT PRIMARY KEY);
            CREATE TABLE envio_retorno(id_clinica INT NOT NULL,id_envio BIGINT PRIMARY KEY);
            CREATE TABLE pos_venda_campanha(id_clinica INT NOT NULL,id_campanha BIGINT PRIMARY KEY);
            CREATE TABLE pos_venda_campanha_publico(id_campanha BIGINT,id_paciente INT,status TEXT);
            CREATE TABLE mensagem_modelo_retorno(id_clinica INT NOT NULL,grupo TEXT,texto TEXT);
            INSERT INTO clinica VALUES (1,'A'),(2,'B');
            INSERT INTO usuario VALUES (1,'Admin A'),(2,'Admin B');
            INSERT INTO tenant_membro VALUES (1,1,true),(2,2,true);
            INSERT INTO tenant_membro_papel VALUES (1,1,'administrador'),(2,2,'administrador');
            INSERT INTO paciente VALUES (10,1,'Paciente A'),(20,2,'Paciente B');
        """)
        cursor.execute(migration)
        cursor.execute("SELECT set_config('app.tenant_id','1',true)")
        approved = cursor.execute("""
            INSERT INTO comunicacao_modelo(
              codigo,versao,nome,canal,finalidade,categoria,idioma,conteudo,id_usuario
            ) VALUES ('campanha_teste',1,'Teste','whatsapp','campanha','utilidade','pt_BR','Olá',1)
            RETURNING id_modelo
        """).fetchone()[0]
        cursor.execute("""
            INSERT INTO comunicacao_modelo_status_evento(
              id_modelo,status,provedor,provedor_modelo_id,id_usuario
            ) VALUES (%s,'aprovado','Meta','tpl-1',1)
        """, (approved,))

        missing = cursor.execute("SELECT * FROM comunicacao_avaliar(10,'85999999999','whatsapp','campanha',false,%s)", (approved,)).fetchone()
        assert missing[0] is False and missing[1] == "consentimento_ausente", missing

        granted = cursor.execute("""
            INSERT INTO comunicacao_consentimento_evento(
              id_paciente,canal,finalidade,decisao,origem,texto_aceito,evidencia_referencia,ocorrido_em,id_usuario
            ) VALUES (10,'whatsapp','campanha','concedido','formulario','Aceito receber campanhas',
              'form-1',now(),1) RETURNING id_consentimento
        """).fetchone()[0]
        attempt = cursor.execute("""
            SELECT * FROM comunicacao_registrar_tentativa(
              10,'85999999999','whatsapp','campanha',false,%s,'teste',1
            )
        """, (approved,)).fetchone()
        assert attempt[1] is True and attempt[3] == granted and attempt[4] == approved and attempt[5] == 1, attempt

        wrong_purpose = cursor.execute("SELECT * FROM comunicacao_avaliar(10,'85999999999','whatsapp','pos_venda',false,%s)", (approved,)).fetchone()
        assert wrong_purpose[0] is False and wrong_purpose[1] == "modelo_incompativel", wrong_purpose

        rejected = cursor.execute("""
            INSERT INTO comunicacao_modelo(
              codigo,versao,nome,canal,finalidade,categoria,idioma,conteudo,id_usuario
            ) VALUES ('rejeitado',1,'Rejeitado','whatsapp','campanha','utilidade','pt_BR','Não usar',1)
            RETURNING id_modelo
        """).fetchone()[0]
        cursor.execute("""
            INSERT INTO comunicacao_modelo_status_evento(
              id_modelo,status,provedor,motivo_rejeicao,id_usuario
            ) VALUES (%s,'rejeitado','Meta','Política do provedor',1)
        """, (rejected,))
        denied_model = cursor.execute("SELECT * FROM comunicacao_avaliar(10,'85999999999','whatsapp','campanha',false,%s)", (rejected,)).fetchone()
        assert denied_model[0] is False and denied_model[1] == "modelo_nao_aprovado", denied_model

        revoked = cursor.execute("""
            INSERT INTO comunicacao_consentimento_evento(
              id_paciente,canal,finalidade,decisao,origem,evidencia_referencia,ocorrido_em,id_usuario
            ) VALUES (10,'whatsapp','campanha','revogado','canal','wamid-1',now()+interval '1 second',1)
            RETURNING id_consentimento
        """).fetchone()[0]
        denied = cursor.execute("SELECT * FROM comunicacao_avaliar(10,'85999999999','whatsapp','campanha',false,%s)", (approved,)).fetchone()
        assert denied[0] is False and denied[1] == "consentimento_revogado" and denied[2] == revoked, denied

        cursor.execute("""
            INSERT INTO comunicacao_modelo_status_evento(
              id_modelo,status,provedor,provedor_modelo_id,id_usuario
            ) VALUES (%s,'inativo','Meta','tpl-1',1)
        """, (approved,))
        inactive = cursor.execute("SELECT * FROM comunicacao_avaliar(10,'85999999999','whatsapp','campanha',false,%s)", (approved,)).fetchone()
        assert inactive[0] is False and inactive[1] == "modelo_nao_aprovado", inactive

        cursor.execute("SAVEPOINT immutable")
        try:
            cursor.execute("UPDATE comunicacao_consentimento_evento SET origem='usuario' WHERE id_consentimento=%s", (granted,))
            raise AssertionError("histórico deveria ser imutável")
        except psycopg.errors.RaiseException:
            cursor.execute("ROLLBACK TO SAVEPOINT immutable")

        cursor.execute("SELECT set_config('app.tenant_id','2',true)")
        assert cursor.execute("SELECT count(*) FROM comunicacao_consentimento_evento").fetchone()[0] == 0
        assert cursor.execute("SELECT count(*) FROM comunicacao_modelo").fetchone()[0] == 0
        assert cursor.execute("SELECT count(*) FROM comunicacao_modelo_status_evento").fetchone()[0] == 0
        assert cursor.execute("SELECT count(*) FROM comunicacao_tentativa").fetchone()[0] == 0
    print("OK: ausência, finalidade, revogação, modelo, snapshot, imutabilidade e RLS.")
finally:
    conn.rollback()
    conn.close()
