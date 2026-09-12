"""Carga fictícia aditiva e transacional. Execute com o Python do chatbot.
Sem HTTP, sem mensagens reais e sem alterações em registros existentes.
"""
import argparse
from collections import Counter
from datetime import date, datetime, time, timedelta, timezone
from decimal import Decimal
import json
import os
from pathlib import Path

import psycopg
from psycopg import sql

TAG = '[DEMO]'
EMAIL_PATTERN = 'demo.v1.%@example.invalid'
TZ = timezone(timedelta(hours=-3))


def vincular_caixa(cur, clinic, today):
    """Vincula apenas recebimentos fictícios de hoje ao turno exibido no balcão."""
    cur.execute("""SELECT pg.id_pagamento FROM pagamento pg
        JOIN fatura f USING(id_fatura) JOIN consulta c USING(id_consulta)
        JOIN paciente p USING(id_paciente)
        WHERE p.email LIKE %s AND pg.referencia_externa LIKE 'DEMO-V1-%%'
        AND pg.pago_em::date=%s AND pg.id_turno_caixa IS NULL""", (EMAIL_PATTERN, today))
    payments = [r[0] for r in cur.fetchall()]
    if not payments:
        return {'pagamentos_vinculados': 0}
    cur.execute('SELECT id_turno_caixa FROM turno_caixa WHERE id_clinica=%s AND aberto_em::date=%s AND fechado_em IS NULL ORDER BY aberto_em DESC LIMIT 1', (clinic,today))
    row = cur.fetchone()
    if row:
        turn = row[0]
    else:
        cur.execute("INSERT INTO turno_caixa (id_clinica,operador_nome,aberto_em,observacao) VALUES (%s,'Recepção Demo',%s,'[DEMO] Turno para demonstração') RETURNING id_turno_caixa", (clinic,datetime.combine(today,time(7),TZ)))
        turn = cur.fetchone()[0]
    cur.execute('UPDATE pagamento SET id_turno_caixa=%s WHERE id_pagamento=ANY(%s)', (turn,payments))
    return {'id_turno_caixa':turn,'pagamentos_vinculados':len(payments)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--dry-run', action='store_true', help='Valida a carga e desfaz a transação')
    args = parser.parse_args()
    # Mesmos defaults locais do backend/docker-compose.yml; não imprime credenciais.
    conn = psycopg.connect(host=os.getenv('DB_HOST', 'localhost'), port=os.getenv('DB_PORT', '5432'),
                          dbname=os.getenv('DB_NAME', 'clinica'), user=os.getenv('DB_USER', 'clinica'),
                          password=os.getenv('DB_PASSWORD', 'clinica'), connect_timeout=5)
    counts = Counter()
    ids = {}
    try:
        with conn.cursor() as cur:
            cur.execute("SET LOCAL TIME ZONE 'America/Fortaleza'")
            cur.execute('SELECT pg_advisory_xact_lock(867530912)')
            cur.execute('SELECT CURRENT_DATE')
            today = cur.fetchone()[0]
            cur.execute('SELECT count(*) FROM paciente WHERE email LIKE %s', (EMAIL_PATTERN,))
            existing = cur.fetchone()[0]
            if existing:
                cash = vincular_caixa(cur, int(os.getenv('ID_CLINICA', '1')), today)
                if args.dry_run: conn.rollback()
                else: conn.commit()
                print(json.dumps({'status': 'carga existente; sem duplicação', 'pacientes_demo': existing, 'caixa': cash}, ensure_ascii=False))
                return
            cur.execute('SELECT id_clinica FROM clinica WHERE id_clinica=%s', (int(os.getenv('ID_CLINICA', '1')),))
            clinic = cur.fetchone()
            if not clinic:
                raise RuntimeError('Clínica configurada não encontrada. Nenhuma alteração foi aplicada.')
            clinic = clinic[0]

            def insert(table, pk, **values):
                query = sql.SQL('INSERT INTO {} ({}) VALUES ({}) RETURNING {}').format(
                    sql.Identifier(table), sql.SQL(',').join(map(sql.Identifier, values)),
                    sql.SQL(',').join(sql.Placeholder() for _ in values), sql.Identifier(pk))
                cur.execute(query, list(values.values()))
                ident = cur.fetchone()[0]
                counts[table] += 1
                ids.setdefault(table, []).append(ident)
                return ident

            def stamp(day, hour=9, minute=0):
                return datetime.combine(day, time(hour, minute), TZ)

            insurers = []
            for i, name in enumerate(['Aurora Saúde', 'BemViver Assistência', 'Horizonte Médico', 'Sereno Saúde']):
                cv = insert('convenio', 'id_convenio', nome=f'{TAG} {name}', registro_ans=f'DM{i+1:04}', ativo=True,
                            contato=f'convenio{i+1}@example.invalid', observacoes='Operadora fictícia de demonstração; registro ANS inválido.')
                insurers.append(cv)
                for j, plan in enumerate(['Essencial', 'Ampliado']):
                    pl = insert('convenio_plano', 'id_plano', id_convenio=cv, nome=plan, codigo=f'DEMO-{i}-{j}', ativo=True)
                    for k, proc in enumerate(['Consulta em consultório', 'Consulta de retorno', 'Exame ambulatorial']):
                        insert('convenio_procedimento', 'id_convenio_procedimento', id_convenio=cv, id_plano=pl,
                               codigo=f'DEMO-{k+1}', descricao=proc, valor_negociado=Decimal(140+i*25+k*40), cobertura=True, exige_autorizacao=k==2)
                insert('documento_obrigatorio_convenio', 'id_documento_obrigatorio', id_convenio=cv, nome_documento='Guia de atendimento assinada')
                insert('regra_auditoria', 'id_regra', id_convenio=cv, tipo='autorizacao_obrigatoria', severidade='alta', descricao='Verificar autorização de exames (demonstração)')

            doctors = []
            for i, (name, specialty) in enumerate([
                ('Dra. Helena Monteiro', 'Clínica Geral'), ('Dr. Rafael Dantas', 'Cardiologia'),
                ('Dra. Marina Azevedo', 'Dermatologia'), ('Dr. André Nogueira', 'Ortopedia'),
                ('Dra. Camila Duarte', 'Ginecologia'), ('Dr. Lucas Farias', 'Endocrinologia')]):
                cur.execute('SELECT id_especialidade FROM especialidade WHERE nome=%s', (specialty,))
                row = cur.fetchone()
                spec = row[0] if row else insert('especialidade', 'id_especialidade', nome=specialty)
                doctor = insert('medico', 'id_medico', nome=f'{TAG} {name}', crm=f'DEMO-CE-{i+1:04}', id_especialidade=spec, status='ativo', repasse_percentual=Decimal(45+i*2))
                doctors.append((doctor, spec))

            patients = []
            first = ['Ana Clara', 'Bruno', 'Cecília', 'Daniel', 'Elisa', 'Felipe', 'Gabriela', 'Hugo', 'Isabela', 'João', 'Larissa', 'Miguel']
            surnames = ['Alencar', 'Barreto', 'Castro', 'Dourado', 'Esteves']
            for i in range(60):
                cv = insurers[i % 4] if i % 3 else None
                name = f'{first[i % 12]} {surnames[i // 12]}'
                # Identificadores deliberadamente inválidos, sem correspondência de contato real.
                phone = f'00000{i+1:04}'
                patient = insert('paciente', 'id_paciente', nome=f'{TAG} {name}', cpf=f'DEMO{i+1:07}',
                                 data_nascimento=date(1958+i%48, 1+i%12, 1+i%27), ddd='00', numero=phone,
                                 id_convenio=cv, email=f'demo.v1.{i+1:03}@example.invalid', cidade='Fortaleza', uf='CE',
                                 logradouro='Rua da Demonstração', numero_endereco=str(100+i), bairro='Bairro Fictício',
                                 historia_familiar='Dados sintéticos para demonstração.', historia_social='Cadastro fictício; não utilizar para atendimento real.')
                patients.append((patient, cv, '00'+phone, name))
                if i % 10 == 0:
                    insert('alergia', 'id_alergia', id_paciente=patient, tipo='AMBIENTAL', substancia='Poeira (cenário fictício)', gravidade='LEVE', observacao='Registro demonstrativo')
                    insert('comorbidade', 'id_comorbidade', id_paciente=patient, descricao='Acompanhamento de condição crônica (fictício)', ativo=True)

            bills_by_insurer = {cv: [] for cv in insurers}
            visits = []
            schedule = []
            for offset in range(-84, 0, 3):
                day = today+timedelta(days=offset)
                for j in range(12):
                    status = 'Faltou' if (j+offset)%13==0 else 'Cancelada' if (j+offset)%17==0 else 'Realizada'
                    schedule.append((day, j, (j*7-offset)%60, status))
            for offset in range(1,15):
                day = today+timedelta(days=offset)
                if day.weekday()==6: continue
                for j in range(8):
                    schedule.append((day,j,12+(offset*5+j)%48, 'Confirmada' if j%3 else 'Agendada'))
            for j in range(24):
                status = 'Realizada' if j < 6 else ['Em Atendimento','Em Espera','Em Espera','Confirmada','Agendada','Em Espera'][j-6] if j < 12 else 'Confirmada' if j%2 else 'Agendada'
                schedule.append((today,j,24+j,status))

            for n,(day,j,pidx,status) in enumerate(schedule):
                patient, cv, phone, name = patients[pidx]
                doctor, spec = doctors[j%6]
                hour, minute = 8+j//6, (j%2)*30
                when = stamp(day,hour,minute)
                slot = insert('agenda','id_agenda',id_medico=doctor,data_slot=day,hora_slot=time(hour,minute),situacao='Livre' if status=='Cancelada' else 'Ocupado')
                consult = insert('consulta','id_consulta',id_paciente=patient,id_agenda=slot,status_consulta=status,
                                 tipo='Retorno' if n%7==0 else 'Consulta',duracao_minutos=30,
                                 iniciado_em=when+timedelta(minutes=n%12) if status in ('Realizada','Em Atendimento') else None,
                                 cancelado_em=when-timedelta(hours=5) if status=='Cancelada' else None)
                if status in ('Em Espera','Em Atendimento'):
                    insert('fila_atendimento','id_fila',id_consulta=consult,status='em_atendimento' if status=='Em Atendimento' else 'aguardando',
                           checkin_em=when-timedelta(minutes=15),atendimento_iniciado_em=when if status=='Em Atendimento' else None)
                if status=='Realizada':
                    chart=insert('prontuario','id_prontuario',id_consulta=consult,queixa_principal='Consulta de acompanhamento (demonstração)',
                                 descricao='Evolução fictícia para apresentar o prontuário eletrônico.',diagnostico='Avaliação de rotina — cenário fictício',
                                 prescricao='Sem prescrição real. Conteúdo exclusivo para demonstração.',exame_fisico='Registro demonstrativo de avaliação clínica.',
                                 conduta='Acompanhamento de rotina (fictício).',id_medico_responsavel=doctor,assinado_em=when+timedelta(minutes=25),
                                 criado_em=when,retorno_sugerido_em=day+timedelta(days=30) if n%4==0 else None)
                    insert('sinal_vital','id_sinal_vital',id_consulta=consult,pressao_sistolica=118+n%15,pressao_diastolica=76+n%9,
                           frequencia_cardiaca=65+n%20,temperatura=Decimal('36.5'),peso=Decimal(60+n%25),altura=Decimal('1.68'),medido_em=when)
                    visits.append((patient,consult,day))
                if status in ('Cancelada','Faltou'): continue
                amount=Decimal(180+(j%6)*40)
                paid=status=='Realizada' and (n%5!=0)
                bill=insert('fatura','id_fatura',id_consulta=consult,valor=amount,status='pago' if paid else 'atrasado' if day<today-timedelta(days=7) else 'pendente',
                            vencimento=day+timedelta(days=7) if cv else day,criado_em=when-timedelta(days=3))
                if paid:
                    insert('pagamento','id_pagamento',id_fatura=bill,metodo='convenio' if cv else ['pix','cartao_credito','dinheiro'][n%3],
                           valor_pago=amount,pago_em=when+timedelta(minutes=30),referencia_externa=f'DEMO-V1-{n:04}',desconto=0)
                if cv:
                    insert('autorizacao_convenio','id_autorizacao',id_consulta=consult,id_convenio=cv,numero_guia=f'DEMO-{n:05}',
                           status='pendente' if n%9==0 else 'autorizado',solicitado_em=when-timedelta(days=4),respondido_em=None if n%9==0 else when-timedelta(days=2))
                    if status=='Realizada':
                        audit=insert('auditoria_atendimento','id_auditoria_atendimento',id_consulta=consult,status='aprovado' if paid else 'atencao',
                                     valor_em_risco=0 if paid else amount,avaliado_em=when+timedelta(hours=1))
                        insert('auditoria_item','id_auditoria_item',id_auditoria_atendimento=audit,status='ok' if paid else 'falha',
                               descricao='Documentação conferida (DEMO)' if paid else 'Conferir guia assinada (DEMO)',severidade='baixa' if paid else 'alta')
                        bills_by_insurer[cv].append((bill,amount,paid))

            for cv, bills in bills_by_insurer.items():
                selected=[b for b in bills if not b[2]][:6]
                lot=insert('lote_faturamento','id_lote',id_convenio=cv,codigo=f'DEMO-V1-{cv:04}',status='com_glosas',data_envio=today-timedelta(days=10),
                           valor_total=sum(b[1] for b in selected),criado_em=stamp(today-timedelta(days=12)))
                for k,(bill,amount,_) in enumerate(selected):
                    insert('lote_item','id_lote_item',id_lote=lot,id_fatura=bill)
                    if k<3:
                        gl=insert('glosa','id_glosa',id_fatura=bill,id_convenio=cv,motivo=f'{TAG} Guia incompleta' if k%2==0 else f'{TAG} Divergência de código',
                                  valor=amount/2,valor_faturado=amount,prazo_recurso=today+timedelta(days=3+k*7),status=['nova','em_analise','recurso_enviado'][k],
                                  data_glosa=today-timedelta(days=5),origem='manual',recorribilidade='recorrivel',categoria_motivo='documentacao',codigo_motivo='DEMO',criado_em=stamp(today-timedelta(days=5)))
                        insert('glosa_historico','id_glosa_historico',id_glosa=gl,evento='Glosa fictícia criada para demonstração')
                        if k==2:
                            insert('recurso_glosa','id_recurso',id_glosa=gl,status='enviado',justificativa='Contestação fictícia; nenhum recurso enviado ao convênio.',
                                   prazo_limite=today+timedelta(days=17),canal_envio='manual',protocolo=f'DEMO-{gl}',enviado_em=stamp(today-timedelta(days=2)),criado_em=stamp(today-timedelta(days=3)),evidencias_conferidas=True)

            categories=[('aluguel','Aluguel da unidade',6500),('folha','Folha da recepção',12500),('fornecedores','Material administrativo',840),
                        ('insumos','Materiais de atendimento',2200),('impostos','Tributos mensais',3200),('marketing','Campanha institucional',950),
                        ('manutencao','Manutenção de equipamentos',680),('servicos','Energia e internet',1100),('outros','Treinamento da equipe',450)]
            for month in range(3):
                for j,(cat,desc,value) in enumerate(categories):
                    due=today+timedelta(days=8-month*30-j*2)
                    status='pago' if month>0 or j%3==0 else 'cancelado' if j==8 else 'pendente'
                    paid_day=min(due,today) if status=='pago' else None
                    insert('despesa','id_despesa',id_clinica=clinic,descricao=f'{TAG} {desc} — ciclo {month+1}',categoria=cat,valor=Decimal(value+month*35),
                           vencimento=due,status=status,pago_em=paid_day,fornecedor=f'{TAG} Fornecedor {j+1}',observacoes='Conta fictícia; não pagar.',criado_em=stamp(due-timedelta(days=12)))

            for i,(patient,consult,day) in enumerate(visits[::12][:24]):
                insert('exame','id_exame',id_paciente=patient,id_consulta=consult,nome_exame=f'{TAG} Exame de acompanhamento',laboratorio='Laboratório Fictício',
                       status=['solicitado','agendado','realizado'][i%3],solicitado_em=stamp(day))
            for i in range(6):
                insert('lista_espera','id_lista_espera',id_paciente=patients[i][0],id_especialidade=doctors[i][1],status='aguardando',
                       data_preferencia_inicio=today,data_preferencia_fim=today+timedelta(days=14),criado_em=stamp(today-timedelta(days=2)))

            for i,(patient,cv,phone,name) in enumerate(patients[:18]):
                state=['aguardando','com_agente','bot'][i%3]
                when=datetime.now(TZ)-timedelta(minutes=4+i*7)
                insert('conversa','id_conversa',id_clinica=clinic,id_paciente=patient,telefone=phone,estado=state,
                       agente_nome='Recepção Demo' if state=='com_agente' else None,criado_em=when-timedelta(minutes=5),atualizado_em=when)
                turns=[('paciente','Olá! Gostaria de verificar os horários da clínica.'),('bot','Olá! Esta é uma conversa fictícia para demonstração do atendimento.'),
                       ('paciente',['Gostaria de falar com a recepção.','Posso remarcar minha consulta?','Obrigado pelas informações!'][i%3])]
                if state=='com_agente': turns.append(('agente','Vou consultar os horários disponíveis para você.'))
                for j,(sender,body) in enumerate(turns):
                    insert('mensagem','id_mensagem',id_paciente=patient,telefone=phone,direcao='entrada' if sender=='paciente' else 'saida',tipo='texto',
                           remetente=sender,conteudo=body,criado_em=when-timedelta(minutes=len(turns)-j-1))

            cash = vincular_caixa(cur, clinic, today)

            # Validação contábil dos registros criados antes de efetivar a transação.
            cur.execute('SELECT count(*) FROM fatura f WHERE f.id_fatura=ANY(%s) AND f.status=\'pago\' AND f.valor <> (SELECT COALESCE(sum(p.valor_pago),0) FROM pagamento p WHERE p.id_fatura=f.id_fatura)', (ids['fatura'],))
            assert cur.fetchone()[0]==0, 'Faturas pagas divergentes dos recebimentos'
            cur.execute('SELECT count(*) FROM lote_faturamento l WHERE l.id_lote=ANY(%s) AND l.valor_total<>(SELECT sum(f.valor) FROM lote_item i JOIN fatura f USING(id_fatura) WHERE i.id_lote=l.id_lote)',(ids['lote_faturamento'],))
            assert cur.fetchone()[0]==0, 'Lotes com totais divergentes'
            report={'status':'validado sem gravar' if args.dry_run else 'gravado','data_base':str(today),'registros_inseridos':dict(counts),'caixa':cash,'ids':ids}
            if args.dry_run:
                conn.rollback()
            else:
                conn.commit()
                Path(__file__).with_name('demo_manifest.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
            print(json.dumps({k:v for k,v in report.items() if k!='ids'},ensure_ascii=False,indent=2))
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


if __name__=='__main__':
    main()
