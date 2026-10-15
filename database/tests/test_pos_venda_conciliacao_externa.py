"""Teste transacional das regras de conciliação de reagendamentos externos."""
import pathlib
import uuid

import psycopg
from psycopg import sql


root = pathlib.Path(__file__).resolve().parents[1]


def migration(number):
    text = (root / "migrations" / number).read_text(encoding="utf-8")
    return text.replace("\nBEGIN;\n", "\n").replace("\nCOMMIT;", "")


conn = psycopg.connect(
    host="localhost", port=5432, dbname="clinica", user="clinica", password="clinica", connect_timeout=5
)
try:
    with conn.cursor() as cursor:
        schema = "test_pv_conciliacao_" + uuid.uuid4().hex
        cursor.execute(sql.SQL("CREATE SCHEMA {}").format(sql.Identifier(schema)))
        cursor.execute(sql.SQL("SET LOCAL search_path TO {}").format(sql.Identifier(schema)))
        cursor.execute("""
            CREATE TABLE auth_schema_version(version INTEGER PRIMARY KEY);
            INSERT INTO auth_schema_version VALUES (17);
            CREATE TABLE clinica(id_clinica INT PRIMARY KEY,nome TEXT);
            CREATE TABLE usuario(id INT PRIMARY KEY,nome TEXT);
            CREATE TABLE tenant_membro(id_clinica INT,id_usuario INT,PRIMARY KEY(id_clinica,id_usuario));
            INSERT INTO clinica VALUES (1,'Clínica A'),(2,'Clínica B');
            INSERT INTO usuario VALUES (1,'Operador A'),(2,'Operador B');
            INSERT INTO tenant_membro VALUES (1,1),(2,2);
            CREATE TABLE pos_venda_caso(
              id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
              id_clinica INT NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer,
              id_consulta_origem INT UNIQUE,
              id_consulta_reagendada INT,
              data_falta TIMESTAMP NOT NULL,
              status VARCHAR(30) NOT NULL DEFAULT 'pendente',
              origem_legada TEXT,
              id_responsavel INT,
              proxima_acao TIMESTAMPTZ,
              motivo_falta TEXT,
              recorrente BOOLEAN NOT NULL DEFAULT false,
              versao INT NOT NULL DEFAULT 0,
              criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
              atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
              UNIQUE(id_clinica,id)
            );
            CREATE TABLE pos_venda_evento(
              id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
              id_clinica INT NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer,
              id_caso BIGINT NOT NULL,
              id_usuario INT,
              tipo VARCHAR(40) NOT NULL,
              canal VARCHAR(20),
              descricao TEXT NOT NULL,
              criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
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
        cursor.execute(migration("018_pos_venda_importacao_csv.sql"))
        cursor.execute(migration("019_pos_venda_fila_diaria.sql"))
        cursor.execute(migration("020_pos_venda_conciliacao_externa.sql"))
        cursor.execute("SELECT set_config('app.tenant_id','1',true)")

        source = cursor.execute("""
            INSERT INTO integracao_fonte_dados(codigo,nome,fuso_horario)
            VALUES ('api-clinica','API clínica','America/Fortaleza') RETURNING id_fonte
        """).fetchone()[0]
        batch = cursor.execute("""
            INSERT INTO integracao_importacao(id_fonte,arquivo_nome,arquivo_sha256,fuso_horario,status,criado_por)
            VALUES (%s,'eventos.csv',repeat('a',64),'America/Fortaleza','PROCESSANDO',1)
            RETURNING id_importacao
        """, (source,)).fetchone()[0]
        patient = cursor.execute("""
            INSERT INTO integracao_paciente_externo(id_fonte,identificador_externo,nome,telefone)
            VALUES (%s,'P-1','Paciente fictício','85999999999') RETURNING id_paciente_externo
        """, (source,)).fetchone()[0]

        def appointment(identifier, when, normalized, patient_id=patient):
            return cursor.execute("""
                INSERT INTO integracao_atendimento_externo(
                  id_fonte,id_paciente_externo,identificador_externo,agendado_em,fuso_horario,
                  profissional,especialidade,tipo,situacao_origem,situacao_normalizada,id_importacao
                ) VALUES (%s,%s,%s,now()+%s::interval,'America/Fortaleza','Dra. Exemplo',
                  'Clínica geral','Consulta',%s,%s,%s) RETURNING id_atendimento_externo
            """, (source, patient_id, identifier, when, normalized, normalized, batch)).fetchone()[0]

        origin = appointment("ORIGEM-1", "-1 day", "FALTOU")
        case_id = cursor.execute("""
            SELECT id FROM pos_venda_caso WHERE id_atendimento_externo_origem=%s
        """, (origin,)).fetchone()[0]
        cursor.execute("""
            INSERT INTO pos_venda_evento(id_caso,tipo,descricao)
            VALUES (%s,'contato','Contato anterior preservado.')
        """, (case_id,))
        rebook = appointment("REAG-1", "1 day", "AGENDADO")
        cursor.execute("""
            INSERT INTO pos_venda_atendimento_externo(id_atendimento_externo,id_caso) VALUES (%s,%s)
        """, (rebook, case_id))
        cursor.execute("""
            UPDATE pos_venda_caso SET status='reagendado',id_atendimento_externo_reagendado=%s
            WHERE id=%s
        """, (rebook, case_id))

        # Confirmação e estados administrativos não equivalem a comparecimento.
        cursor.execute("""
            UPDATE integracao_atendimento_externo
            SET situacao_origem='Autorizado pelo convênio',situacao_normalizada='CONFIRMADO'
            WHERE id_atendimento_externo=%s
        """, (rebook,))
        assert cursor.execute("SELECT status FROM pos_venda_caso WHERE id=%s", (case_id,)).fetchone()[0] == "reagendado"
        cursor.execute("""
            UPDATE integracao_atendimento_externo
            SET situacao_origem='Faturado',situacao_normalizada='DESCONHECIDO'
            WHERE id_atendimento_externo=%s
        """, (rebook,))
        assert cursor.execute("SELECT status FROM pos_venda_caso WHERE id=%s", (case_id,)).fetchone()[0] == "reagendado"

        cursor.execute("""
            UPDATE integracao_atendimento_externo SET situacao_normalizada='REALIZADO'
            WHERE id_atendimento_externo=%s
        """, (rebook,))
        assert cursor.execute("SELECT status FROM pos_venda_caso WHERE id=%s", (case_id,)).fetchone()[0] == "recuperado"
        events_before = cursor.execute("SELECT count(*) FROM pos_venda_evento WHERE id_caso=%s", (case_id,)).fetchone()[0]
        cursor.execute("""
            UPDATE integracao_atendimento_externo SET situacao_normalizada='REALIZADO'
            WHERE id_atendimento_externo=%s
        """, (rebook,))
        assert cursor.execute("SELECT count(*) FROM pos_venda_evento WHERE id_caso=%s", (case_id,)).fetchone()[0] == events_before

        # Uma retificação da fonte desfaz o desfecho anterior sem perder histórico.
        cursor.execute("""
            UPDATE integracao_atendimento_externo SET situacao_normalizada='FALTOU'
            WHERE id_atendimento_externo=%s
        """, (rebook,))
        state = cursor.execute("""
            SELECT status,recorrente,proxima_acao IS NOT NULL FROM pos_venda_caso WHERE id=%s
        """, (case_id,)).fetchone()
        assert state == ("nova_falta", True, True), state
        assert cursor.execute("""
            SELECT count(*) FROM pos_venda_alerta
            WHERE id_caso=%s AND tipo='revisar_recorrencia' AND status='aberto'
        """, (case_id,)).fetchone()[0] == 1
        assert cursor.execute("""
            SELECT count(*) FROM pos_venda_evento WHERE id_caso=%s AND tipo='contato'
        """, (case_id,)).fetchone()[0] == 1

        cursor.execute("""
            UPDATE integracao_atendimento_externo SET situacao_normalizada='CANCELADO'
            WHERE id_atendimento_externo=%s
        """, (rebook,))
        assert cursor.execute("SELECT status FROM pos_venda_caso WHERE id=%s", (case_id,)).fetchone()[0] == "pendente"

        # Um vínculo anterior continua auditável, mas não altera a tentativa atual.
        current = appointment("REAG-2", "2 days", "AGENDADO")
        cursor.execute("""
            INSERT INTO pos_venda_atendimento_externo(id_atendimento_externo,id_caso) VALUES (%s,%s)
        """, (current, case_id))
        cursor.execute("""
            UPDATE pos_venda_caso SET status='reagendado',id_atendimento_externo_reagendado=%s
            WHERE id=%s
        """, (current, case_id))
        cursor.execute("""
            UPDATE integracao_atendimento_externo SET situacao_normalizada='REALIZADO'
            WHERE id_atendimento_externo=%s
        """, (rebook,))
        assert cursor.execute("SELECT status FROM pos_venda_caso WHERE id=%s", (case_id,)).fetchone()[0] == "reagendado"

        # Fonte, paciente, tipo e especialidade precisam coincidir.
        other_patient = cursor.execute("""
            INSERT INTO integracao_paciente_externo(id_fonte,identificador_externo,nome,telefone)
            VALUES (%s,'P-2','Outro paciente','85888888888') RETURNING id_paciente_externo
        """, (source,)).fetchone()[0]
        invalid = appointment("INVALIDO-1", "3 days", "AGENDADO", other_patient)
        cursor.execute("SAVEPOINT invalid_link")
        try:
            cursor.execute("""
                INSERT INTO pos_venda_atendimento_externo(id_atendimento_externo,id_caso) VALUES (%s,%s)
            """, (invalid, case_id))
            raise AssertionError("Atendimento de outro paciente deveria ser rejeitado")
        except psycopg.errors.RaiseException:
            cursor.execute("ROLLBACK TO SAVEPOINT invalid_link")

        cursor.execute("SELECT set_config('app.tenant_id','2',true)")
        assert cursor.execute("SELECT count(*) FROM pos_venda_atendimento_externo").fetchone()[0] == 0
        assert cursor.execute("SELECT count(*) FROM pos_venda_alerta").fetchone()[0] == 0
        print("OK: presença real, correções, idempotência, recorrência, vínculo e isolamento.")
finally:
    conn.rollback()
    conn.close()
