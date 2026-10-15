"""Teste transacional da migração de importação CSV; não altera dados da clínica.
Executar: chatbot/.venv/Scripts/python.exe database/tests/test_pos_venda_importacao_csv.py
"""
import pathlib
import uuid
import psycopg
from psycopg import sql

migration = (pathlib.Path(__file__).parents[1] / 'migrations' / '018_pos_venda_importacao_csv.sql').read_text(encoding='utf-8')
migration = migration.replace('\nBEGIN;\n', '\n').replace('\nCOMMIT;', '')
conn = psycopg.connect(host='localhost', port=5432, dbname='clinica', user='clinica', password='clinica', connect_timeout=5)
try:
    with conn.cursor() as cursor:
        schema = 'test_import_csv_' + uuid.uuid4().hex
        cursor.execute(sql.SQL('CREATE SCHEMA {}').format(sql.Identifier(schema)))
        cursor.execute(sql.SQL('SET LOCAL search_path TO {}').format(sql.Identifier(schema)))
        cursor.execute('''
            CREATE TABLE auth_schema_version(version INTEGER PRIMARY KEY);
            INSERT INTO auth_schema_version VALUES (17);
            CREATE TABLE clinica(id_clinica INT PRIMARY KEY, nome TEXT);
            CREATE TABLE usuario(id INT PRIMARY KEY, nome TEXT);
            CREATE TABLE tenant_membro(id_clinica INT, id_usuario INT, PRIMARY KEY(id_clinica,id_usuario));
            INSERT INTO clinica VALUES (1,'Clínica A'),(2,'Clínica B');
            INSERT INTO usuario VALUES (1,'Operador A'),(2,'Operador B');
            INSERT INTO tenant_membro VALUES (1,1),(2,2);
            CREATE TABLE pos_venda_caso(
              id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
              id_clinica INT NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer,
              id_consulta_origem INT NOT NULL UNIQUE,
              data_falta TIMESTAMP NOT NULL,
              status VARCHAR(30) NOT NULL DEFAULT 'pendente',
              origem_legada TEXT,
              proxima_acao TIMESTAMPTZ,
              versao INT NOT NULL DEFAULT 0,
              atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
              UNIQUE(id_clinica,id)
            );
            CREATE TABLE pos_venda_evento(
              id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
              id_clinica INT NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer,
              id_caso BIGINT NOT NULL,
              tipo VARCHAR(40) NOT NULL,
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
        ''')
        cursor.execute(migration)
        cursor.execute("SELECT set_config('app.tenant_id','1',true)")
        source = cursor.execute("""
            INSERT INTO integracao_fonte_dados(codigo,nome,fuso_horario)
            VALUES ('piloto','Agenda piloto','America/Fortaleza') RETURNING id_fonte
        """).fetchone()[0]
        batch = cursor.execute("""
            INSERT INTO integracao_importacao(id_fonte,arquivo_nome,arquivo_sha256,fuso_horario,status,criado_por)
            VALUES (%s,'faltas.csv',repeat('a',64),'America/Fortaleza','PROCESSANDO',1) RETURNING id_importacao
        """, (source,)).fetchone()[0]
        patient = cursor.execute("""
            INSERT INTO integracao_paciente_externo(id_fonte,identificador_externo,nome,telefone)
            VALUES (%s,'P-1','Paciente fictício','85999999999') RETURNING id_paciente_externo
        """, (source,)).fetchone()[0]
        appointment = cursor.execute("""
            INSERT INTO integracao_atendimento_externo(id_fonte,id_paciente_externo,identificador_externo,
              agendado_em,fuso_horario,profissional,especialidade,tipo,situacao_origem,
              situacao_normalizada,id_importacao)
            VALUES (%s,%s,'A-1','2026-10-01T13:00:00Z','America/Fortaleza','Profissional fictício',
              'Clínica geral','Consulta','Faltou','FALTOU',%s) RETURNING id_atendimento_externo
        """, (source, patient, batch)).fetchone()[0]
        assert cursor.execute('SELECT count(*) FROM pos_venda_caso').fetchone()[0] == 1
        assert cursor.execute('SELECT origem FROM pos_venda_caso').fetchone()[0] == 'importacao_csv'

        cursor.execute("UPDATE integracao_atendimento_externo SET situacao_normalizada='REALIZADO' WHERE id_atendimento_externo=%s", (appointment,))
        assert cursor.execute('SELECT status FROM pos_venda_caso').fetchone()[0] == 'registro_corrigido'
        cursor.execute("UPDATE integracao_atendimento_externo SET situacao_normalizada='FALTOU' WHERE id_atendimento_externo=%s", (appointment,))
        assert cursor.execute('SELECT status FROM pos_venda_caso').fetchone()[0] == 'pendente'

        cursor.execute('SAVEPOINT duplicate_guard')
        try:
            cursor.execute("""
                INSERT INTO integracao_atendimento_externo(id_fonte,id_paciente_externo,identificador_externo,
                  agendado_em,fuso_horario,profissional,especialidade,tipo,situacao_origem,
                  situacao_normalizada,id_importacao)
                SELECT id_fonte,id_paciente_externo,identificador_externo,agendado_em,fuso_horario,
                  profissional,especialidade,tipo,situacao_origem,situacao_normalizada,id_importacao
                FROM integracao_atendimento_externo WHERE id_atendimento_externo=%s
            """, (appointment,))
            raise AssertionError('Identificador externo repetido deveria ser rejeitado')
        except psycopg.errors.UniqueViolation:
            cursor.execute('ROLLBACK TO SAVEPOINT duplicate_guard')

        cursor.execute("SELECT set_config('app.tenant_id','2',true)")
        assert cursor.execute('SELECT count(*) FROM integracao_atendimento_externo').fetchone()[0] == 0
        assert cursor.execute('SELECT count(*) FROM pos_venda_caso').fetchone()[0] == 0
        print('OK: falta externa, idempotência, correção de situação e isolamento por clínica.')
finally:
    conn.rollback()
    conn.close()
