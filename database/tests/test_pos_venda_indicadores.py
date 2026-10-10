"""Executa as agregações reais dos indicadores em schema temporário com rollback."""
import pathlib
import re
import uuid
from decimal import Decimal
import psycopg
from psycopg import sql
from psycopg.rows import dict_row

root = pathlib.Path(__file__).resolve().parents[2]
java = (root / 'backend/src/main/java/br/com/clinica/service/PosVendaIndicadoresService.java').read_text(encoding='utf-8')
base = re.search(r'static final String BASE = """(.*?)""";', java, re.S).group(1)
summary = re.search(r'var resumo = db\.queryForMap\(BASE \+ """(.*?)""", append', java, re.S).group(1)
motives = re.search(r'var motivos = db\.queryForList\(BASE \+ """(.*?)""", params', java, re.S).group(1)
team = re.search(r'var equipe = db\.queryForList\(BASE \+ """(.*?)""", params', java, re.S).group(1)

conn = psycopg.connect(host='localhost', port=5432, dbname='clinica', user='clinica', password='clinica',
                       connect_timeout=5, row_factory=dict_row)
try:
    with conn.cursor() as c:
        schema = 'test_pv_indicadores_' + uuid.uuid4().hex
        c.execute(sql.SQL('CREATE SCHEMA {}').format(sql.Identifier(schema)))
        c.execute(sql.SQL('SET LOCAL search_path TO {}').format(sql.Identifier(schema)))
        c.execute('''
            CREATE TABLE usuario(id INT PRIMARY KEY,nome TEXT);
            CREATE TABLE especialidade(id_especialidade INT PRIMARY KEY,nome TEXT);
            CREATE TABLE medico(id_medico INT PRIMARY KEY,nome TEXT,id_especialidade INT);
            CREATE TABLE agenda(id_agenda INT PRIMARY KEY,id_medico INT,data_slot DATE,hora_slot TIME);
            CREATE TABLE paciente(id_paciente INT PRIMARY KEY,nome TEXT,ddd TEXT,numero TEXT);
            CREATE TABLE consulta(id_consulta INT PRIMARY KEY,id_paciente INT,id_agenda INT,status_consulta TEXT,tipo TEXT);
            CREATE TABLE pos_venda_caso(id INT PRIMARY KEY,status TEXT,id_responsavel INT,motivo_falta TEXT,
                recorrente BOOLEAN,id_consulta_origem INT,id_consulta_reagendada INT,data_falta TIMESTAMP,
                proxima_acao TIMESTAMPTZ,atualizado_em TIMESTAMPTZ);
            CREATE TABLE pos_venda_consulta(id_consulta INT PRIMARY KEY,id_caso INT);
            CREATE TABLE pos_venda_evento(id_caso INT,tipo TEXT,criado_em TIMESTAMPTZ);

            INSERT INTO usuario VALUES (1,'Equipe de teste');
            INSERT INTO especialidade VALUES (1,'Clínica geral');
            INSERT INTO medico VALUES (1,'Médica A',1);
            INSERT INTO paciente VALUES (100,'Paciente repetido','85','900000001'),
                (200,'Paciente B','85','900000002'),(300,'Paciente C','85','900000003');
            INSERT INTO agenda VALUES
                (10,1,'2026-09-01','10:00'),(11,1,'2026-09-08','10:00'),
                (20,1,'2026-09-05','10:00'),(21,1,'2026-09-12','10:00'),
                (30,1,'2026-09-10','10:00'),
                (40,1,'2026-09-15','10:00'),(41,1,'2026-09-22','10:00'),
                (50,1,'2026-09-20','10:00'),(51,1,'2026-09-27','10:00'),
                (60,1,'2026-09-21','10:00');
            INSERT INTO consulta VALUES
                (10,100,10,'Faltou','Consulta'),(11,100,11,'Realizada','Consulta'),
                (20,100,20,'Faltou','Consulta'),(21,100,21,'Confirmada','Consulta'),
                (30,200,30,'Faltou','Consulta'),
                (40,200,40,'Faltou','Consulta'),(41,200,41,'Faltou','Consulta'),
                (50,300,50,'Faltou','Consulta'),(51,300,51,'Cancelada','Consulta'),
                (60,300,60,'Agendada','Consulta');
            INSERT INTO pos_venda_caso VALUES
                (1,'recuperado',1,'Esquecimento',false,10,11,'2026-09-01 10:00',NULL,'2026-09-08 11:00+00'),
                (2,'reagendado',1,'Esquecimento',false,20,21,'2026-09-05 10:00',NULL,'2026-09-05 15:00+00'),
                (3,'sem_resposta',NULL,NULL,false,30,NULL,'2026-09-10 10:00','2026-09-11 10:00+00','2026-09-10 16:00+00'),
                (4,'nova_falta',1,'Trânsito',true,40,41,'2026-09-15 10:00','2026-10-01 10:00+00','2026-09-22 13:00+00'),
                (5,'pendente',NULL,'Cancelamento',false,50,51,'2026-09-20 10:00','2026-09-21 10:00+00','2026-09-27 13:00+00'),
                (6,'registro_corrigido',1,'Corrigido',false,60,NULL,'2026-09-21 10:00',NULL,'2026-09-21 13:00+00');
            INSERT INTO pos_venda_consulta VALUES
                (10,1),(11,1),(20,2),(21,2),(30,3),(40,4),(41,4),(50,5),(51,5),(60,6);
            INSERT INTO pos_venda_evento VALUES
                (1,'contato','2026-09-02 14:00+00'),(1,'contato','2026-09-01 14:00+00'),
                (3,'contato','2026-09-10 16:00+00'),(3,'contato','2026-09-10 13:30+00'),
                (4,'contato','2026-09-15 15:00+00'),(4,'agenda','2026-09-22 13:00+00');
        ''')

        def run(query, extra=None, de='2026-09-01', ate='2026-10-01'):
            params = [de, ate, None, None] + (extra or [])
            c.execute((base + query).replace('?', '%s'), params)
            return c.fetchall()

        result = run(summary, ['America/Fortaleza'])[0]
        assert result['faltas'] == 5 and result['pacientes_unicos'] == 3, result
        assert result['contatados'] == 3 and result['contatados_pacientes'] == 2, result
        assert result['tentativas'] == 5, result
        assert result['reagendados'] == 4 and result['recuperados'] == 1, result
        assert result['recuperados_pacientes'] == 1 and result['taxa_recuperacao_pacientes'] == Decimal('33.3'), result
        assert result['aguardando_comparecimento'] == 1 and result['novas_faltas'] == 1, result
        assert result['abertos'] == 4 and result['pendencias_vencidas'] == 2 and result['sem_responsavel'] == 2, result
        assert result['taxa_recuperacao'] == Decimal('20.0'), result
        assert result['horas_primeiro_contato'] == Decimal('1.2'), result
        assert result['ultima_atualizacao'] is not None, result

        grouped_motives = run(motives)
        assert grouped_motives[0] == {'motivo': 'Esquecimento', 'quantidade': 2}, grouped_motives
        assert any(row['motivo'] == 'Não informado' for row in grouped_motives), grouped_motives
        grouped_team = run(team)
        assert grouped_team[0]['casos'] == 3 and grouped_team[0]['recuperados'] == 1, grouped_team
        assert grouped_team[1]['responsavel'] == 'Sem responsável' and grouped_team[1]['casos'] == 2, grouped_team

        empty = run(summary, ['America/Fortaleza'], '2025-01-01', '2025-02-01')[0]
        assert empty['faltas'] == 0 and empty['tentativas'] == 0, empty
        assert empty['taxa_recuperacao'] is None and empty['ultima_atualizacao'] is None, empty
        print('OK: casos e pacientes únicos, eventos fora de ordem, nova falta, cancelamento, correção, fuso e rastreabilidade.')
finally:
    conn.rollback()
    conn.close()
