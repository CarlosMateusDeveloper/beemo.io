from pathlib import Path
import html
import json
import re

ROOT = Path(__file__).resolve().parent

def inline(value):
    value = html.escape(value)
    value = re.sub(r'\[([^\]]+)\]\((https?://[^ )]+)\)', r'<a href="\2">\1</a>', value)
    value = re.sub(r'\*\*(.+?)\*\*', r'<strong>\1</strong>', value)
    return re.sub(r'`([^`]+)`', r'<code>\1</code>', value)

def convert(source):
    lines = source.splitlines()
    result = []
    i = 0
    while i < len(lines):
        line = lines[i]
        if not line.strip():
            i += 1
            continue
        if line.startswith('|'):
            rows = []
            while i < len(lines) and lines[i].startswith('|'):
                if not re.fullmatch(r'[| :\-]+', lines[i]):
                    rows.append([inline(c.strip()) for c in lines[i].strip('|').split('|')])
                i += 1
            result.append('<div class="table-wrap"><table><thead><tr>' + ''.join('<th>'+c+'</th>' for c in rows[0]) + '</tr></thead><tbody>' + ''.join('<tr>'+''.join('<td>'+c+'</td>' for c in r)+'</tr>' for r in rows[1:]) + '</tbody></table></div>')
            continue
        if line.startswith('#'):
            n = len(line) - len(line.lstrip('#'))
            text = line[n:].strip()
            result.append(f'<h{n}>{inline(text)}</h{n}>')
        elif line.startswith('- ') or re.match(r'^\d+\. ', line):
            ordered = not line.startswith('- ')
            tag = 'ol' if ordered else 'ul'
            entries = []
            while i < len(lines) and ((bool(re.match(r'^\d+\. ', lines[i]))) if ordered else lines[i].startswith('- ')):
                entries.append('<li>'+inline(re.sub(r'^(?:\d+\.|-) ', '', lines[i]))+'</li>')
                i += 1
            result.append('<'+tag+'>'+''.join(entries)+'</'+tag+'>')
            continue
        else:
            result.append('<p>'+inline(line)+'</p>')
        i += 1
    return '\n'.join(result)

css = '''
:root {color-scheme:light; font-family:Arial,Helvetica,sans-serif; color:#192d33; background:#edf2f3;}
* {box-sizing:border-box} body {margin:0; font-size:16px; line-height:1.65}
main {max-width:1000px; margin:32px auto; padding:52px 64px; background:white; box-shadow:0 4px 32px #172f3412}
.toolbar {max-width:1000px; margin:24px auto 0; display:flex; align-items:center; justify-content:space-between; gap:16px; padding:0 12px; font-size:14px}
button {background:#115e59;color:white;border:0;border-radius:5px;padding:11px 18px;cursor:pointer;font:inherit}
h1 {font-size:42px;line-height:1.1;margin:0 0 18px;color:#102e33;letter-spacing:-1px}
h2 {font-size:25px;line-height:1.3;margin:44px 0 16px;color:#102e33;break-after:avoid}
h3 {font-size:19px;margin:28px 0 12px;break-after:avoid} p {margin:0 0 17px}
main>p:first-of-type {color:#657a7e; font-size:14px;margin-bottom:36px}
a {color:#0b625d;text-underline-offset:3px} strong {color:#112f34}
.table-wrap {overflow-x:auto;margin:22px 0 24px} table {border-collapse:collapse;width:100%;font-size:14px;line-height:1.45}
td,th {border:1px solid #d5dfdf;padding:10px 12px;text-align:left;vertical-align:middle}
th {background:#163e44;color:#fff;font-weight:600} tbody tr:nth-child(even) {background:#f3f7f7}
tr {break-inside:avoid} thead {display:table-header-group} li {padding-left:3px;margin:0 0 12px} code {font-size:.9em;overflow-wrap:anywhere}
@media(max-width:720px) {main {margin:16px 0;padding:28px 20px} h1 {font-size:34px} h2 {font-size:23px} .toolbar {flex-wrap:wrap} table {font-size:12px} td,th {padding:8px}}
@media print {@page {size:A4;margin:17mm} :root {background:white;color:black} body {font-size:10.5pt;line-height:1.4} main {margin:0;max-width:none;padding:0;box-shadow:none} .toolbar {display:none} h1 {font-size:28pt} h2 {font-size:17pt;margin:22pt 0 10pt} h3 {font-size:13pt} p {orphans:3;widows:3;margin-bottom:10pt} table {font-size:9pt} td,th {padding:6pt} th {background:#e5eded;color:black} .table-wrap {overflow:visible} a {color:inherit} }
'''

for stem, label in [('plano-de-negocio-clinicos','Plano de negócio ClinicOS'), ('roteiro-privado-da-negociacao','ClinicOS Roteiro privado')]:
    source = (ROOT / (stem+'.md')).read_text(encoding='utf-8')
    page = '<!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>'+label+'</title><style>'+css+'</style></head><body><div class="toolbar"><span>ClinicOS · Setembro de 2026</span><button onclick="window.print()">Imprimir ou salvar como PDF</button></div><main>'+convert(source)+'</main></body></html>'
    (ROOT / (stem+'.html')).write_text(page, encoding='utf-8')
    assert page.count('<table>') == page.count('</table>')
    assert '[Author' not in page and 'Lorem ipsum' not in page

scenarios = {
    'conservador': ([1,1,1,1,1,2,2,2,2,2,2,2], [0,0,0,0,0,1,1,1,1,1,1,1], 499, 6500, 12, 38922, -50398.5),
    'base': ([1,2,3,3,3,4,4,4,4,4,4,4], [0,0,0,0,1,1,1,1,1,1,2,2], 599, 6500, 30, 112612, 13059),
    'expansao': ([2,3,4,5,6,7,8,8,8,8,8,8], [0,0,0,1,1,1,1,2,2,2,2,3], 699, 11000, 60, 241854, 69640.5)
}
output = {}
for name, (new, cancellations, price, fixed, expected_active, expected_revenue, expected_net) in scenarios.items():
    active = 0
    cash = 60000 - 9000
    rows = []
    for month, (n,c) in enumerate(zip(new,cancellations),1):
        active += n-c
        revenue = active*price
        net = revenue*.75+n*390-fixed
        cash += net
        rows.append(dict(month=month,new=n,cancellations=c,active=active,subscription=revenue,operating_cash=net,cash=cash))
    assert active == expected_active
    assert sum(r['subscription'] for r in rows) == expected_revenue
    assert sum(r['operating_cash'] for r in rows)-9000 == expected_net
    output[name] = rows
(ROOT/'premissas-e-calculos.json').write_text(json.dumps(output,indent=2,ensure_ascii=False),encoding='utf-8')
print('HTML gerado; contagem de clientes, receitas e saldos dos três cenários conferidos.')
