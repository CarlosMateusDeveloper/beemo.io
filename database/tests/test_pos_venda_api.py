"""Smoke test somente de leitura da API local, com o backend em execução."""
import json
from urllib.request import urlopen
from urllib.error import HTTPError

def get(path):
    with urlopen('http://127.0.0.1:8080/api/pos-venda' + path, timeout=20) as response:
        return json.load(response)

fila = get('')
assert {'itens', 'total', 'pagina', 'resumo'} <= fila.keys()
assert isinstance(get('/responsaveis'), list)
for prioridade in ['hoje', 'sem_responsavel', 'conferir_agenda']:
    assert 'itens' in get('?fila=' + prioridade)
indicadores = get('/indicadores?inicio=2026-09-01&fim=2026-09-30')
assert {'resumo', 'motivos', 'equipe', 'inicio', 'fim'} <= indicadores.keys()
assert 'taxa_recuperacao' in indicadores['resumo']
if fila['itens']:
    detalhe = get('/' + str(fila['itens'][0]['id']))
    assert {'caso', 'historico', 'consultas'} <= detalhe.keys()
    assert detalhe['historico']
    print('OK: fila, responsaveis, detalhe, historico e consultas elegiveis.')
else:
    print('OK: fila e responsaveis; banco sem casos abertos para testar detalhe.')
try:
    get('?status=invalido')
    raise AssertionError('Filtro invalido deveria retornar 400')
except HTTPError as e:
    assert e.code == 400
print('OK: filtro invalido rejeitado.')
for path in ['?fila=invalida', '/indicadores?inicio=2026-09-30&fim=2026-09-01']:
    try:
        get(path)
        raise AssertionError('Filtro invalido deveria retornar 400')
    except HTTPError as e:
        assert e.code == 400
print('OK: prioridades, indicadores e validacao de periodo.')
