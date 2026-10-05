"""Aplica a migração aditiva 009 apenas no PostgreSQL local de desenvolvimento."""
from pathlib import Path
import psycopg

sql = (Path(__file__).parent / 'migrations/009_pos_venda.sql').read_text(encoding='utf-8')
with psycopg.connect(host='localhost',port=5432,dbname='clinica',user='clinica',password='clinica',connect_timeout=5,autocommit=True) as conn:
    conn.execute(sql)
    print('Migracao 009 aplicada no banco local clinica.')
