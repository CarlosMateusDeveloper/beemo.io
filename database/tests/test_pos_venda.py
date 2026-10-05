"""Teste transacional do gatilho de pós-venda; nunca altera dados da clínica.
Executar: chatbot/.venv/Scripts/python.exe database/tests/test_pos_venda.py
"""
import pathlib
import uuid
import psycopg
from psycopg import sql

migration = (pathlib.Path(__file__).parents[1] / 'migrations/009_pos_venda.sql').read_text(encoding='utf-8')
migration = migration.replace('\nBEGIN;\n', '\n').replace('\nCOMMIT;', '')
conn = psycopg.connect(host='localhost', port=5432, dbname='clinica', user='clinica', password='clinica', connect_timeout=5)
try:
    with conn.cursor() as c:
        schema = 'test_pos_venda_' + uuid.uuid4().hex
        c.execute(sql.SQL('CREATE SCHEMA {}').format(sql.Identifier(schema)))
        c.execute(sql.SQL('SET LOCAL search_path TO {}').format(sql.Identifier(schema)))
        c.execute('''
            CREATE TABLE paciente(id_paciente INT PRIMARY KEY);
            CREATE TABLE usuario(id INT PRIMARY KEY);
            CREATE TABLE agenda(id_agenda INT PRIMARY KEY, data_slot DATE, hora_slot TIME);
            CREATE TABLE consulta(id_consulta INT PRIMARY KEY, id_paciente INT REFERENCES paciente,
                id_agenda INT REFERENCES agenda, status_consulta TEXT);
            CREATE TABLE paciente_retorno_status(id_paciente INT PRIMARY KEY, status TEXT);
            INSERT INTO paciente VALUES (1),(2);
            INSERT INTO agenda SELECT n,current_date + n - 10, '10:00'::time FROM generate_series(1,20) n;
            INSERT INTO consulta VALUES (1,1,1,'Faltou');
        ''')
        c.execute(migration)
        def scalar(query, params=()):
            c.execute(query, params)
            return c.fetchone()[0]
        def status(caso=1):
            return scalar('SELECT status FROM pos_venda_caso WHERE id=%s',(caso,))
        def link(consulta):
            c.execute('INSERT INTO pos_venda_consulta(id_consulta,id_caso) VALUES (%s,1)',(consulta,))
            c.execute("UPDATE pos_venda_caso SET id_consulta_reagendada=%s,status='reagendado' WHERE id=1",(consulta,))
        assert status() == 'pendente', 'Falta histórica não foi importada'
        c.execute(migration)
        assert scalar('SELECT count(*) FROM pos_venda_caso') == 1, 'Migração duplicou caso'
        c.execute("UPDATE consulta SET status_consulta='Faltou' WHERE id_consulta=1")
        assert scalar('SELECT count(*) FROM pos_venda_evento') == 1, 'Update idempotente duplicou histórico'
        c.execute("INSERT INTO consulta VALUES (2,1,2,'Agendada')")
        link(2)
        c.execute("UPDATE consulta SET status_consulta='Confirmada' WHERE id_consulta=2")
        assert status() == 'reagendado', 'Confirmação de agendamento não é comparecimento'
        c.execute("UPDATE consulta SET status_consulta='Faltou' WHERE id_consulta=2")
        assert status() == 'nova_falta'
        assert scalar('SELECT count(*) FROM pos_venda_caso') == 1, 'Nova falta duplicou caso'
        c.execute(migration)
        assert scalar('SELECT count(*) FROM pos_venda_caso') == 1, 'Backfill duplicou tentativa vinculada'
        c.execute("INSERT INTO consulta VALUES (3,1,3,'Agendada')")
        link(3)
        c.execute("UPDATE consulta SET status_consulta='Realizada' WHERE id_consulta=3")
        assert status() == 'recuperado'
        c.execute("UPDATE consulta SET status_consulta='Faltou' WHERE id_consulta=3")
        assert status() == 'nova_falta', 'Correção de presença não reabriu acompanhamento'
        c.execute("INSERT INTO consulta VALUES (4,1,4,'Agendada')")
        link(4)
        c.execute("UPDATE consulta SET status_consulta='Cancelada' WHERE id_consulta=4")
        assert status() == 'pendente', 'Cancelamento não devolveu à fila'
        assert scalar('SELECT proxima_acao IS NOT NULL FROM pos_venda_caso WHERE id=1')
        c.execute("INSERT INTO consulta VALUES (5,2,5,'Faltou')")
        caso2 = scalar('SELECT id FROM pos_venda_caso WHERE id_consulta_origem=5')
        c.execute("UPDATE consulta SET status_consulta='Agendada' WHERE id_consulta=5")
        assert status(caso2) == 'registro_corrigido'
        c.execute("UPDATE consulta SET status_consulta='Faltou' WHERE id_consulta=5")
        assert status(caso2) == 'pendente'
        c.execute("INSERT INTO paciente_retorno_status VALUES (2,'nao_contatar')")
        assert status(caso2) == 'nao_contatar', 'Opt-out não encerrou os outros casos do paciente'
        c.execute("INSERT INTO consulta VALUES (6,2,6,'Faltou')")
        assert scalar('SELECT status FROM pos_venda_caso WHERE id_consulta_origem=6') == 'nao_contatar'
        c.execute('SAVEPOINT patient_guard')
        try:
            c.execute('UPDATE consulta SET id_paciente=2 WHERE id_consulta=1')
            raise AssertionError('Troca de paciente deveria ser bloqueada')
        except psycopg.errors.RaiseException:
            c.execute('ROLLBACK TO SAVEPOINT patient_guard')
        assert scalar('SELECT versao FROM pos_venda_caso WHERE id=1') > 0
        print('OK: backfill, idempotencia, reagendamento, presenca, nova falta, cancelamento, correcao, opt-out e integridade do paciente.')
finally:
    conn.rollback()
    conn.close()
