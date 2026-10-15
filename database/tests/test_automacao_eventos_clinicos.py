"""Valida catálogo, idempotência, ordenação, regras e reprocessamento da migração 025."""

import pathlib
import uuid

import psycopg
from psycopg import sql


root = pathlib.Path(__file__).resolve().parents[1]
migration = (root / "migrations" / "025_automacao_eventos_clinicos.sql").read_text(encoding="utf-8")
migration = migration.replace("\nBEGIN;\n", "\n").replace("\nCOMMIT;", "")

conn = psycopg.connect(
    host="localhost", port=5432, dbname="clinica", user="clinica", password="clinica", connect_timeout=5
)
try:
    with conn.cursor() as cursor:
        schema = "test_automacao_eventos_" + uuid.uuid4().hex
        cursor.execute(sql.SQL("CREATE SCHEMA {}").format(sql.Identifier(schema)))
        cursor.execute(sql.SQL("SET LOCAL search_path TO {}").format(sql.Identifier(schema)))
        cursor.execute("""
            CREATE TABLE auth_schema_version(version INTEGER PRIMARY KEY);
            CREATE TABLE clinica(id_clinica INTEGER PRIMARY KEY);
            CREATE TABLE usuario(id INTEGER PRIMARY KEY,nome TEXT);
            CREATE TABLE tenant_membro(
              id_clinica INTEGER,id_usuario INTEGER,perfil TEXT,ativo BOOLEAN DEFAULT true,
              PRIMARY KEY(id_clinica,id_usuario)
            );
            CREATE TABLE paciente(
              id_paciente INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
              id_clinica INTEGER NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer,
              nome TEXT,UNIQUE(id_clinica,id_paciente)
            );
            CREATE TABLE agenda(
              id_agenda INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
              id_clinica INTEGER NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer,
              data_slot DATE NOT NULL,hora_slot TIME NOT NULL,UNIQUE(id_clinica,id_agenda)
            );
            CREATE TABLE consulta(
              id_consulta INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
              id_clinica INTEGER NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer,
              id_paciente INTEGER NOT NULL,id_agenda INTEGER NOT NULL,status_consulta TEXT DEFAULT 'Agendada',
              UNIQUE(id_clinica,id_consulta),
              FOREIGN KEY(id_clinica,id_paciente) REFERENCES paciente(id_clinica,id_paciente),
              FOREIGN KEY(id_clinica,id_agenda) REFERENCES agenda(id_clinica,id_agenda)
            );
            CREATE TABLE prontuario(
              id_prontuario INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
              id_clinica INTEGER NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer,
              id_consulta INTEGER NOT NULL,assinado_em TIMESTAMPTZ,retorno_sugerido_em DATE,
              UNIQUE(id_clinica,id_prontuario),
              FOREIGN KEY(id_clinica,id_consulta) REFERENCES consulta(id_clinica,id_consulta)
            );
            CREATE TABLE paciente_retorno_status(
              id_clinica INTEGER NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer,
              id_paciente INTEGER NOT NULL,status TEXT,PRIMARY KEY(id_clinica,id_paciente)
            );
            CREATE TABLE pos_venda_caso(
              id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
              id_clinica INTEGER NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer,
              id_consulta_origem INTEGER,id_consulta_reagendada INTEGER,data_falta TIMESTAMP NOT NULL,
              status TEXT NOT NULL DEFAULT 'pendente',id_responsavel INTEGER,proxima_acao TIMESTAMPTZ,
              motivo_falta TEXT,recorrente BOOLEAN NOT NULL DEFAULT false,origem TEXT NOT NULL DEFAULT 'agenda_interna',
              criado_em TIMESTAMPTZ DEFAULT now(),atualizado_em TIMESTAMPTZ DEFAULT now(),versao INTEGER DEFAULT 0,
              UNIQUE(id_clinica,id),UNIQUE(id_clinica,id_consulta_origem)
            );
            CREATE TABLE pos_venda_consulta(
              id_clinica INTEGER NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer,
              id_consulta INTEGER PRIMARY KEY,id_caso BIGINT NOT NULL
            );
            CREATE TABLE pos_venda_evento(
              id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
              id_clinica INTEGER NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer,
              id_caso BIGINT NOT NULL,id_usuario INTEGER,tipo TEXT,canal TEXT,descricao TEXT,criado_em TIMESTAMPTZ DEFAULT now()
            );
            CREATE TABLE pos_venda_tarefa(
              id_tarefa BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
              id_clinica INTEGER NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer,
              id_paciente INTEGER,paciente_referencia TEXT,titulo TEXT NOT NULL,descricao TEXT,id_responsavel INTEGER,
              prioridade TEXT,prazo TIMESTAMPTZ,origem TEXT,status TEXT DEFAULT 'aberta',concluida_em TIMESTAMPTZ,
              criado_por INTEGER,atualizado_por INTEGER,criado_em TIMESTAMPTZ DEFAULT now(),
              atualizado_em TIMESTAMPTZ DEFAULT now(),versao INTEGER DEFAULT 0,UNIQUE(id_clinica,id_tarefa)
            );
            CREATE TABLE pos_venda_jornada(
              id_jornada BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
              id_clinica INTEGER NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer,
              nome TEXT,status TEXT,id_responsavel INTEGER,criado_por INTEGER,UNIQUE(id_clinica,id_jornada)
            );
            CREATE TABLE pos_venda_jornada_etapa(
              id_etapa BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
              id_clinica INTEGER NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer,
              id_jornada BIGINT,ordem INTEGER,canal TEXT
            );
            CREATE TABLE pos_venda_jornada_inscricao(
              id_inscricao BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
              id_clinica INTEGER NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer,
              id_jornada BIGINT,id_paciente INTEGER,etapa_atual INTEGER DEFAULT 0,status TEXT DEFAULT 'ativa',
              inscrito_em TIMESTAMPTZ DEFAULT now(),concluido_em TIMESTAMPTZ,criado_por INTEGER,
              atualizado_em TIMESTAMPTZ DEFAULT now(),versao INTEGER DEFAULT 0,
              UNIQUE(id_clinica,id_inscricao),UNIQUE(id_clinica,id_jornada,id_paciente)
            );
            CREATE TABLE pos_venda_historico(
              id_historico BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
              id_clinica INTEGER NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer,
              recurso TEXT,recurso_id BIGINT,acao TEXT,id_usuario INTEGER,dados JSONB,criado_em TIMESTAMPTZ DEFAULT now()
            );
            CREATE TABLE comunicacao_consentimento_evento(
              id_consentimento BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
              id_clinica INTEGER NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer,
              id_paciente INTEGER,canal TEXT,finalidade TEXT,decisao TEXT,ocorrido_em TIMESTAMPTZ
            );
            INSERT INTO clinica VALUES (1),(2);
            INSERT INTO usuario VALUES (10,'Administrador A'),(20,'Administrador B');
            INSERT INTO tenant_membro VALUES (1,10,'administrador',true),(2,20,'administrador',true);
        """)
        cursor.execute("SELECT set_config('app.tenant_id','1',true)")
        cursor.execute(migration)

        patient = cursor.execute("INSERT INTO paciente(nome) VALUES ('Paciente A') RETURNING id_paciente").fetchone()[0]
        agenda = cursor.execute("INSERT INTO agenda(data_slot,hora_slot) VALUES (CURRENT_DATE,'10:00') RETURNING id_agenda").fetchone()[0]
        consultation = cursor.execute(
            "INSERT INTO consulta(id_paciente,id_agenda) VALUES (%s,%s) RETURNING id_consulta", (patient, agenda)
        ).fetchone()[0]
        cursor.execute("UPDATE consulta SET status_consulta='Faltou' WHERE id_consulta=%s", (consultation,))
        assert cursor.execute("SELECT count(*) FROM pos_venda_caso").fetchone()[0] == 1
        assert cursor.execute("SELECT count(*) FROM pos_venda_tarefa").fetchone()[0] == 1
        assert cursor.execute("SELECT status FROM pos_venda_evento_clinico WHERE tipo='CONSULTA_FALTOU'").fetchone()[0] == "processado"
        event_count = cursor.execute("SELECT count(*) FROM pos_venda_evento_clinico").fetchone()[0]
        cursor.execute("UPDATE consulta SET status_consulta='Faltou' WHERE id_consulta=%s", (consultation,))
        assert cursor.execute("SELECT count(*) FROM pos_venda_evento_clinico").fetchone()[0] == event_count
        assert cursor.execute("SELECT count(*) FROM pos_venda_tarefa").fetchone()[0] == 1
        cursor.execute("UPDATE consulta SET status_consulta='Agendada' WHERE id_consulta=%s", (consultation,))
        assert cursor.execute("SELECT status FROM pos_venda_caso").fetchone()[0] == "registro_corrigido"

        case_id = cursor.execute("SELECT id FROM pos_venda_caso").fetchone()[0]
        agenda_rebook = cursor.execute(
            "INSERT INTO agenda(data_slot,hora_slot) VALUES (CURRENT_DATE+7,'10:00') RETURNING id_agenda"
        ).fetchone()[0]
        rebook = cursor.execute(
            "INSERT INTO consulta(id_paciente,id_agenda) VALUES (%s,%s) RETURNING id_consulta", (patient, agenda_rebook)
        ).fetchone()[0]
        cursor.execute("INSERT INTO pos_venda_consulta(id_consulta,id_caso) VALUES (%s,%s)", (rebook, case_id))
        cursor.execute(
            "UPDATE pos_venda_caso SET id_consulta_reagendada=%s,status='reagendado' WHERE id=%s", (rebook, case_id)
        )
        for agenda_status, case_status in (
            ("Confirmada", "reagendado"), ("Realizada", "recuperado"),
            ("Faltou", "nova_falta"), ("Cancelada", "pendente"),
        ):
            cursor.execute("UPDATE consulta SET status_consulta=%s WHERE id_consulta=%s", (agenda_status, rebook))
            assert cursor.execute("SELECT status FROM pos_venda_caso WHERE id=%s", (case_id,)).fetchone()[0] == case_status

        journey = cursor.execute("""
            INSERT INTO pos_venda_jornada(nome,status,criado_por) VALUES ('Pós-procedimento','ativa',10)
            RETURNING id_jornada
        """).fetchone()[0]
        cursor.execute(
            "INSERT INTO pos_venda_jornada_etapa(id_jornada,ordem,canal) VALUES (%s,0,'tarefa'),(%s,1,'tarefa')",
            (journey, journey),
        )
        cursor.execute("""
            INSERT INTO pos_venda_automacao_regra(
              nome,evento_tipo,acao,id_jornada,configuracao,prioridade,criado_por,atualizado_por
            ) VALUES ('Entrar após procedimento','PRONTUARIO_PROCEDIMENTO_CONCLUIDO','inscrever_jornada',%s,'{}',10,10,10)
        """, (journey,))
        cursor.execute("""
            INSERT INTO pos_venda_automacao_regra(
              nome,evento_tipo,acao,id_jornada,configuracao,prioridade,criado_por,atualizado_por
            ) VALUES ('Avançar no retorno indicado','PRONTUARIO_RETORNO_INDICADO','avancar_jornada',%s,'{}',10,10,10)
        """, (journey,))
        cursor.execute("""
            INSERT INTO prontuario(id_consulta,assinado_em,retorno_sugerido_em)
            VALUES (%s,now(),CURRENT_DATE+30)
        """, (consultation,))
        assert cursor.execute("SELECT count(*) FROM pos_venda_jornada_inscricao").fetchone()[0] == 1
        assert cursor.execute("SELECT etapa_atual,status FROM pos_venda_jornada_inscricao").fetchone() == (1, "ativa")
        assert cursor.execute("""
            SELECT count(*) FROM pos_venda_evento_clinico
            WHERE tipo IN ('PRONTUARIO_PROCEDIMENTO_CONCLUIDO','PRONTUARIO_RETORNO_INDICADO')
        """).fetchone()[0] == 2

        cursor.execute("""
            INSERT INTO pos_venda_evento_clinico(
              identificador,tipo,ocorrido_em,origem_tipo,origem_id,origem_versao,id_paciente
            ) VALUES ('manual:novo','CONSULTA_CRIADA',now(),'consulta','999',10,%s)
        """, (patient,))
        cursor.execute("""
            INSERT INTO pos_venda_evento_clinico(
              identificador,tipo,ocorrido_em,origem_tipo,origem_id,origem_versao,id_paciente
            ) VALUES ('manual:antigo','CONSULTA_CRIADA',now()-interval '1 day','consulta','999',9,%s)
        """, (patient,))
        assert cursor.execute("SELECT status FROM pos_venda_evento_clinico WHERE identificador='manual:antigo'").fetchone()[0] == "ignorado"

        cursor.execute("""
            INSERT INTO pos_venda_automacao_regra(
              nome,evento_tipo,acao,configuracao,prioridade,criado_por,atualizado_por
            ) VALUES ('Regra reparável','CONSULTA_CRIADA','criar_tarefa',
              '{"prazoHoras":"inválido"}',5,10,10)
        """)
        agenda2 = cursor.execute("INSERT INTO agenda(data_slot,hora_slot) VALUES (CURRENT_DATE,'11:00') RETURNING id_agenda").fetchone()[0]
        cursor.execute("INSERT INTO consulta(id_paciente,id_agenda) VALUES (%s,%s)", (patient, agenda2))
        failed = cursor.execute("""
            SELECT id_evento FROM pos_venda_evento_clinico
            WHERE tipo='CONSULTA_CRIADA' AND status='erro' ORDER BY id_evento DESC LIMIT 1
        """).fetchone()[0]
        cursor.execute(
            "UPDATE pos_venda_automacao_regra SET configuracao=%s::jsonb WHERE nome='Regra reparável'",
            ('{"prazoHoras":1}',),
        )
        cursor.execute("UPDATE pos_venda_evento_clinico SET status='pendente',erro=NULL WHERE id_evento=%s", (failed,))
        cursor.execute("SELECT pos_venda_processar_evento(%s)", (failed,))
        assert cursor.execute("SELECT status FROM pos_venda_evento_clinico WHERE id_evento=%s", (failed,)).fetchone()[0] == "processado"
        assert cursor.execute("""
            SELECT status,tentativas FROM pos_venda_automacao_execucao x
            JOIN pos_venda_automacao_regra r USING(id_clinica,id_regra)
            WHERE x.id_evento=%s AND r.nome='Regra reparável'
        """, (failed,)).fetchone() == ("executada", 2)

        blocked_patient = cursor.execute("INSERT INTO paciente(nome) VALUES ('Paciente B') RETURNING id_paciente").fetchone()[0]
        cursor.execute("INSERT INTO paciente_retorno_status(id_paciente,status) VALUES (%s,'nao_contatar')", (blocked_patient,))
        agenda3 = cursor.execute("INSERT INTO agenda(data_slot,hora_slot) VALUES (CURRENT_DATE,'12:00') RETURNING id_agenda").fetchone()[0]
        cursor.execute("INSERT INTO consulta(id_paciente,id_agenda,status_consulta) VALUES (%s,%s,'Faltou')", (blocked_patient, agenda3))
        assert cursor.execute("SELECT status FROM pos_venda_caso WHERE id_consulta_origem=(SELECT max(id_consulta) FROM consulta)").fetchone()[0] == "nao_contatar"
        assert cursor.execute("""
            SELECT count(*) FROM pos_venda_automacao_execucao x
            JOIN pos_venda_automacao_regra r USING(id_clinica,id_regra)
            WHERE r.nome='Criar tarefa após falta' AND x.status='bloqueada'
        """).fetchone()[0] == 1

        cursor.execute("SELECT set_config('app.tenant_id','2',true)")
        assert cursor.execute("SELECT count(*) FROM pos_venda_evento_clinico").fetchone()[0] == 0
        print("OK: eventos, regras, trilha, idempotência, ordem, bloqueio e reprocessamento validados.")
finally:
    conn.rollback()
    conn.close()
