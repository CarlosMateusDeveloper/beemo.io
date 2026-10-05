"""Executa as consultas reais dos indicadores em schema temporário com rollback."""
import pathlib
import re
import uuid
from decimal import Decimal
import psycopg
from psycopg import sql
from psycopg.rows import dict_row

root = pathlib.Path(__file__).resolve().parents[2]
java = (root / 'backend/src/main/java/br/com/clinica/service/PosVendaIndicadoresService.java').read_text(encoding='utf-8')
base = re.search(r'String BASE = """(.*?)""";', java, re.S).group(1)
queries = re.findall(r'db.queryFor(?:Map|List)\(BASE\+"""(.*?)"""', java, re.S)
assert len(queries) == 3, 'Atualize a extração das consultas caso o serviço mude'
conn = psycopg.connect(host='localhost', port=5432, dbname='clinica', user='clinica', password='clinica', connect_timeout=5, row_factory=dict_row)
try:
    with conn.cursor() as c:
        schema = 'test_pv_indicadores_' + uuid.uuid4().hex
        c.execute(sql.SQL('CREATE SCHEMA {}').format(sql.Identifier(schema)))
        c.execute(sql.SQL('SET LOCAL search_path TO {}').format(sql.Identifier(schema)))
        c.execute('''
            CREATE TABLE usuario(id INT PRIMARY KEY, nome TEXT);
            CREATE TABLE pos_venda_caso(id INT PRIMARY KEY, id_consulta_origem INT, data_falta TIMESTAMP,
                status TEXT, id_responsavel INT, motivo_falta TEXT);
            CREATE TABLE pos_venda_consulta(id_consulta INT PRIMARY KEY,id_caso INT);
            CREATE TABLE pos_venda_evento(id_caso INT,tipo TEXT,criado_em TIMESTAMPTZ);
            INSERT INTO usuario VALUES (1,'Equipe de teste');
            INSERT INTO pos_venda_caso VALUES
                (1,10,'2026-09-01 10:00','recuperado',1,'Esquecimento'),
                (2,20,'2026-09-30 23:59','reagendado',1,'Esquecimento'),
                (3,30,'2026-09-10 10:00','sem_resposta',NULL,NULL),
                (4,40,'2026-09-10 10:00','registro_corrigido',1,'Corrigido'),
                (5,50,'2026-08-31 23:59','recuperado',1,'Fora'),
                (6,60,'2026-10-01 00:00','recuperado',1,'Fora');
            INSERT INTO pos_venda_consulta VALUES (10,1),(11,1),(12,1),(20,2),(21,2),(30,3);
            INSERT INTO pos_venda_evento VALUES
                (1,'contato','2026-09-01 14:00+00'),
                (1,'contato','2026-09-02 14:00+00'),
                (3,'contato','2026-09-10 16:00+00'),
                (3,'organizar','2026-09-10 13:00+00');
        ''')
        def run(index, de='2026-09-01', ate='2026-10-01'):
            params = [de, ate] + (['America/Fortaleza'] if index == 0 else [])
            c.execute((base + queries[index]).replace('?', '%s'), params)
            return c.fetchall()
        r = run(0)[0]
        assert r['faltas'] == 3, r
        assert r['contatados'] == 2 and r['tentativas'] == 3, r
        assert r['reagendados'] == 2 and r['recuperados'] == 1, r
        assert r['aguardando_comparecimento'] == 1 and r['abertos'] == 2, r
        assert r['taxa_recuperacao'] == Decimal('33.3'), r
        assert r['horas_primeiro_contato'] == Decimal('2.0'), r
        motivos = run(1)
        assert motivos[0] == {'motivo': 'Esquecimento', 'quantidade': 2}, motivos
        assert motivos[1]['motivo'] == 'Não informado', motivos
        equipe = run(2)
        assert equipe[0]['casos'] == 2 and equipe[0]['recuperados'] == 1, equipe
        assert equipe[1]['responsavel'] == 'Sem responsável', equipe
        vazio = run(0, '2025-01-01', '2025-02-01')[0]
        assert vazio['faltas'] == 0 and vazio['tentativas'] == 0, vazio
        assert vazio['taxa_recuperacao'] is None and vazio['horas_primeiro_contato'] is None, vazio
        print('OK: periodo inclusivo, registros corrigidos, contatos unicos, reagendamento distinto de recuperacao, fuso, motivos, equipe e periodo vazio.')
finally:
    conn.rollback()
    conn.close()
