-- PostgreSQL não usa CREATE DATABASE + USE dentro do script.
-- O banco é criado uma vez fora do schema (ex: createdb clinica) e o script
-- roda conectado a ele. Ex: psql -U postgres -d clinica -f schema_clinica.sql

-- =====================================================================
-- NÚCLEO — cadastro, agenda, consulta e prontuário
-- =====================================================================

-- MySQL aceita ENUM(...) direto na coluna. Postgres não: o ENUM precisa
-- existir como um tipo próprio no banco, criado antes das tabelas que o usam.
CREATE TYPE situacao_agenda AS ENUM ('Livre', 'Ocupado', 'Bloqueado');

CREATE TYPE status_consulta_enum AS ENUM (
    'Agendada',
    'Confirmada',
    'Em Espera',
    'Em Atendimento',
    'Realizada',
    'Cancelada',
    'Faltou'
);

-- Tipo do atendimento, usado pela tela de Agenda (frontend/src/pages/Agenda)
-- pra rotular o card e calcular a duração padrão sugerida.
CREATE TYPE tipo_consulta_enum AS ENUM ('Consulta', 'Retorno', 'Exame', 'Avaliação');

-- 'paciente' não entra aqui: paciente nunca cria conta/login, a identidade
-- dele é o número de WhatsApp (ver paciente + mensagem.telefone). usuario
-- é só para quem de fato acessa um painel: médico e administrador.
CREATE TYPE perfil_usuario AS ENUM ('medico', 'administrador');

-- Enriquecimento do prontuário (histórico do paciente, SOAP, documentos clínicos)
CREATE TYPE tipo_alergia AS ENUM ('MEDICAMENTOSA', 'ALIMENTAR', 'AMBIENTAL', 'OUTRA');
CREATE TYPE gravidade_alergia AS ENUM ('LEVE', 'MODERADA', 'GRAVE');
CREATE TYPE tipo_diagnostico AS ENUM ('PROVISORIO', 'DEFINITIVO');
CREATE TYPE status_solicitacao_exame AS ENUM ('SOLICITADO', 'REALIZADO', 'CANCELADO');
CREATE TYPE tipo_documento_clinico AS ENUM ('ATESTADO', 'DECLARACAO_COMPARECIMENTO');
CREATE TYPE prioridade_encaminhamento AS ENUM ('ROTINA', 'URGENTE');

CREATE TABLE convenio (
    id_convenio INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nome VARCHAR(100) NOT NULL,
    registro_ans CHAR(6) NOT NULL UNIQUE,
    ativo BOOLEAN DEFAULT TRUE
);

CREATE TABLE paciente (
    id_paciente INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nome VARCHAR(100) NOT NULL,
    cpf CHAR(11) NOT NULL UNIQUE, -- Otimizado para apenas números
    data_nascimento DATE NOT NULL,
    ddd CHAR(2) NOT NULL,
    numero VARCHAR(10) NOT NULL, -- Aumentado para prever máscaras com hífen
    id_convenio INT NULL, -- NULL para casos particulares
    historia_familiar TEXT NULL,
    historia_social TEXT NULL, -- tabagismo, etilismo, ocupação etc. (texto livre)
    FOREIGN KEY (id_convenio) REFERENCES convenio(id_convenio)
);

-- Histórico clínico do paciente: persiste entre consultas, não é preenchido por consulta.
CREATE TABLE alergia (
    id_alergia INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_paciente INT NOT NULL,
    tipo tipo_alergia NOT NULL,
    substancia VARCHAR(150) NOT NULL,
    gravidade gravidade_alergia NOT NULL,
    observacao TEXT NULL,
    registrado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (id_paciente) REFERENCES paciente(id_paciente)
);

CREATE TABLE comorbidade (
    id_comorbidade INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_paciente INT NOT NULL,
    descricao VARCHAR(150) NOT NULL,
    codigo_cid VARCHAR(10) NULL,
    data_diagnostico DATE NULL,
    ativo BOOLEAN DEFAULT TRUE,
    FOREIGN KEY (id_paciente) REFERENCES paciente(id_paciente)
);

CREATE TABLE medicamento_uso_continuo (
    id_medicamento_uso_continuo INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_paciente INT NOT NULL,
    medicamento VARCHAR(150) NOT NULL,
    dosagem VARCHAR(50) NULL,
    posologia VARCHAR(100) NULL,
    data_inicio DATE NULL,
    ativo BOOLEAN DEFAULT TRUE,
    FOREIGN KEY (id_paciente) REFERENCES paciente(id_paciente)
);

CREATE TABLE cirurgia_previa (
    id_cirurgia_previa INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_paciente INT NOT NULL,
    descricao VARCHAR(150) NOT NULL,
    data_cirurgia DATE NULL,
    observacao TEXT NULL,
    FOREIGN KEY (id_paciente) REFERENCES paciente(id_paciente)
);

CREATE TABLE especialidade (
    id_especialidade INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nome VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE medico (
    id_medico INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nome VARCHAR(100) NOT NULL,
    crm VARCHAR(20) UNIQUE NOT NULL,
    -- Substitui o antigo "ativo BOOLEAN": a tela /medicos precisa distinguir
    -- férias/afastamento de desligamento definitivo (issue #17).
    status VARCHAR(10) NOT NULL DEFAULT 'ativo'
        CHECK (status IN ('ativo', 'ferias', 'afastado', 'desligado')),
    -- % repassado ao médico sobre o faturamento bruto, usado pra receita
    -- líquida em /medicos. NULL = repasse ainda não configurado pra esse
    -- médico (issue #17) — a tela cai pra mostrar só a bruta nesse caso.
    repasse_percentual NUMERIC(5, 2) NULL
        CHECK (repasse_percentual IS NULL OR (repasse_percentual BETWEEN 0 AND 100)),
    id_especialidade INT NOT NULL,
    FOREIGN KEY (id_especialidade) REFERENCES especialidade(id_especialidade)
);

CREATE TABLE agenda (
    id_agenda INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_medico INT NOT NULL,
    situacao situacao_agenda NOT NULL DEFAULT 'Livre',
    data_slot DATE NOT NULL,
    hora_slot TIME NOT NULL,
    UNIQUE (id_medico, data_slot, hora_slot), -- UNIQUE por médico
    FOREIGN KEY (id_medico) REFERENCES medico(id_medico)
);

CREATE TABLE consulta (
    id_consulta INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_paciente INT NOT NULL,
    id_agenda INT NOT NULL, -- consulta sempre precisa de um horário
    status_consulta status_consulta_enum DEFAULT 'Agendada',
    tipo tipo_consulta_enum NOT NULL DEFAULT 'Consulta',
    -- Resolve parcialmente a discussão da issue #3 (frontend): a duração fica
    -- aqui só para exibição/cálculo de altura do card no calendário. Ainda NÃO
    -- há checagem de sobreposição de horário no banco para consultas de mais
    -- de um slot — "agenda" continua sendo um slot fixo por médico/data/hora.
    duracao_minutos SMALLINT NOT NULL DEFAULT 30,
    -- Horário real em que o atendimento começou (distinto de agenda.hora_slot,
    -- que é o horário marcado) e momento do cancelamento — usados pra
    -- pontualidade/atraso médio e horas perdidas por cancelamento <24h em
    -- /medicos (issue #17). Preenchidos pelo agenda-service nas transições de
    -- status pra 'Em Atendimento'/'Cancelada', não por este backend.
    iniciado_em TIMESTAMPTZ NULL,
    cancelado_em TIMESTAMPTZ NULL,
    FOREIGN KEY (id_paciente) REFERENCES paciente(id_paciente),
    FOREIGN KEY (id_agenda) REFERENCES agenda(id_agenda),
    UNIQUE (id_agenda) -- Garante que um slot de agenda só tenha uma consulta ativa
);

CREATE TABLE usuario (
    id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nome VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    senha VARCHAR(255) NOT NULL,
    perfil perfil_usuario NOT NULL DEFAULT 'administrador'
);

CREATE TABLE prontuario (
    id_prontuario INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_consulta INT NOT NULL,
    -- Subjetivo
    queixa_principal TEXT NULL,
    historia_doenca_atual TEXT NULL,
    -- NULL enquanto o atendimento é rascunho (/prontuario, issue schema
    -- pendente) — assinado_em NULL é o sinal de "Pendente"; finalizar exige
    -- descricao/diagnostico/prescricao preenchidos, mas isso é regra de
    -- aplicação, não mais constraint de banco.
    descricao TEXT NULL,
    -- Objetivo
    exame_fisico TEXT NULL,
    -- Avaliação
    hipotese_diagnostica TEXT NULL,
    diagnostico TEXT NULL,
    tipo_diagnostico tipo_diagnostico NOT NULL DEFAULT 'DEFINITIVO',
    -- Plano
    prescricao TEXT NULL,
    plano_terapeutico TEXT NULL,
    conduta TEXT NULL,
    -- Assinatura e auditoria (Resolução CFM 2.299/2021)
    id_medico_responsavel INT NULL,
    assinado_em TIMESTAMPTZ NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (id_consulta),
    FOREIGN KEY (id_consulta) REFERENCES consulta(id_consulta),
    FOREIGN KEY (id_medico_responsavel) REFERENCES medico(id_medico)
);

-- Sinais vitais/antropometria: múltiplas aferições possíveis por consulta.
CREATE TABLE sinal_vital (
    id_sinal_vital INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_consulta INT NOT NULL,
    pressao_sistolica SMALLINT NULL,
    pressao_diastolica SMALLINT NULL,
    frequencia_cardiaca SMALLINT NULL,
    frequencia_respiratoria SMALLINT NULL,
    temperatura NUMERIC(4, 1) NULL,
    saturacao_oxigenio SMALLINT NULL,
    peso NUMERIC(5, 2) NULL,
    altura NUMERIC(4, 2) NULL,
    escala_dor SMALLINT NULL CHECK (escala_dor BETWEEN 0 AND 10),
    medido_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (id_consulta) REFERENCES consulta(id_consulta)
);

-- Prescrição estruturada (complementa o texto livre em prontuario.prescricao).
CREATE TABLE item_prescricao (
    id_item_prescricao INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_prontuario INT NOT NULL,
    medicamento VARCHAR(150) NOT NULL,
    principio_ativo VARCHAR(150) NULL,
    dosagem VARCHAR(50) NULL,
    via_administracao VARCHAR(50) NULL,
    posologia VARCHAR(100) NULL,
    duracao_tratamento VARCHAR(50) NULL,
    quantidade VARCHAR(50) NULL,
    FOREIGN KEY (id_prontuario) REFERENCES prontuario(id_prontuario)
);

CREATE TABLE solicitacao_exame (
    id_solicitacao_exame INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_prontuario INT NOT NULL,
    exame VARCHAR(150) NOT NULL,
    urgente BOOLEAN DEFAULT FALSE,
    justificativa TEXT NULL,
    status status_solicitacao_exame NOT NULL DEFAULT 'SOLICITADO',
    solicitado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (id_prontuario) REFERENCES prontuario(id_prontuario)
);

-- Atestados e declarações de comparecimento emitidos a partir do prontuário.
CREATE TABLE documento_clinico (
    id_documento_clinico INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_prontuario INT NOT NULL,
    tipo tipo_documento_clinico NOT NULL,
    dias_afastamento SMALLINT NULL,
    codigo_cid_relacionado VARCHAR(10) NULL,
    texto TEXT NOT NULL,
    emitido_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (id_prontuario) REFERENCES prontuario(id_prontuario)
);

CREATE TABLE encaminhamento (
    id_encaminhamento INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_prontuario INT NOT NULL,
    id_especialidade_destino INT NOT NULL,
    motivo TEXT NOT NULL,
    prioridade prioridade_encaminhamento NOT NULL DEFAULT 'ROTINA',
    emitido_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (id_prontuario) REFERENCES prontuario(id_prontuario),
    FOREIGN KEY (id_especialidade_destino) REFERENCES especialidade(id_especialidade)
);

-- =====================================================================
-- FASE 1 — Agendamento inteligente via WhatsApp
-- =====================================================================

CREATE TYPE direcao_mensagem AS ENUM ('entrada', 'saida');
CREATE TYPE tipo_mensagem AS ENUM ('texto', 'audio', 'imagem', 'documento', 'botao', 'localizacao');

-- id_paciente fica NULL enquanto o número que está escrevendo ainda não
-- virou um cadastro completo em paciente. telefone é o que garante que a
-- IA sempre consegue puxar o histórico, mesmo antes do cadastro existir.
CREATE TABLE mensagem (
    id_mensagem BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_paciente INT NULL,
    telefone VARCHAR(20) NOT NULL,
    direcao direcao_mensagem NOT NULL,
    tipo tipo_mensagem NOT NULL DEFAULT 'texto',
    conteudo TEXT NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (id_paciente) REFERENCES paciente(id_paciente)
);

CREATE INDEX idx_mensagem_paciente ON mensagem(id_paciente);
CREATE INDEX idx_mensagem_telefone ON mensagem(telefone);

-- =====================================================================
-- FASE 2 — Gestão dinâmica da fila de atendimento
-- =====================================================================

CREATE TYPE status_fila AS ENUM ('aguardando', 'chamado', 'em_atendimento', 'finalizado', 'cancelado');

CREATE TABLE fila_atendimento (
    id_fila INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_consulta INT NOT NULL UNIQUE,
    status status_fila NOT NULL DEFAULT 'aguardando',
    checkin_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    chamado_em TIMESTAMPTZ NULL,
    atendimento_iniciado_em TIMESTAMPTZ NULL,
    atendimento_finalizado_em TIMESTAMPTZ NULL,
    previsao_atendimento TIMESTAMPTZ NULL,
    FOREIGN KEY (id_consulta) REFERENCES consulta(id_consulta)
);

-- =====================================================================
-- FASE 3 — Remarcação e cancelamento inteligentes
-- =====================================================================

CREATE TYPE origem_acao AS ENUM ('ia', 'sistema', 'humano');

-- Auditoria de toda ação que o sistema/IA executa sem o paciente estar
-- pedindo naquele exato momento (reagendamento automático, preenchimento
-- de vaga, redistribuição de horário). dados_anteriores/dados_novos guardam
-- um snapshot em JSON pra permitir investigar ou até desfazer a ação.
CREATE TABLE log_acao_automatica (
    id_log BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tipo_acao VARCHAR(50) NOT NULL,
    origem origem_acao NOT NULL DEFAULT 'ia',
    id_paciente INT NULL,
    id_consulta INT NULL,
    descricao TEXT NOT NULL,
    dados_anteriores JSONB NULL,
    dados_novos JSONB NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (id_paciente) REFERENCES paciente(id_paciente),
    FOREIGN KEY (id_consulta) REFERENCES consulta(id_consulta)
);

-- =====================================================================
-- FASE 4 — Lista de espera automatizada
-- =====================================================================

CREATE TYPE status_lista_espera AS ENUM ('aguardando', 'atendido', 'expirado', 'cancelado');

CREATE TABLE lista_espera (
    id_lista_espera INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_paciente INT NOT NULL,
    id_especialidade INT NOT NULL,
    id_medico INT NULL, -- NULL = aceita qualquer médico da especialidade
    data_preferencia_inicio DATE NULL,
    data_preferencia_fim DATE NULL,
    status status_lista_espera NOT NULL DEFAULT 'aguardando',
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (id_paciente) REFERENCES paciente(id_paciente),
    FOREIGN KEY (id_especialidade) REFERENCES especialidade(id_especialidade),
    FOREIGN KEY (id_medico) REFERENCES medico(id_medico)
);

-- =====================================================================
-- FASE 5 — Assistente de IA para documentação clínica
-- =====================================================================

CREATE TABLE transcricao_consulta (
    id_transcricao INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_consulta INT NOT NULL UNIQUE,
    texto_transcrito TEXT NOT NULL,
    resumo_gerado TEXT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (id_consulta) REFERENCES consulta(id_consulta)
);

CREATE TYPE origem_documento AS ENUM ('paciente', 'clinica', 'laboratorio');

CREATE TABLE documento_anexo (
    id_documento INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_paciente INT NOT NULL,
    id_consulta INT NULL,
    origem origem_documento NOT NULL DEFAULT 'paciente',
    nome_arquivo VARCHAR(255) NOT NULL,
    url_arquivo TEXT NOT NULL,
    tipo_conteudo VARCHAR(100) NULL,
    enviado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (id_paciente) REFERENCES paciente(id_paciente),
    FOREIGN KEY (id_consulta) REFERENCES consulta(id_consulta)
);

-- Diagnóstico codificado (CID-10). Muitos-para-muitos: um prontuário pode ter
-- mais de um código associado. "principal" marca o diagnóstico que gerou o atendimento.
CREATE TABLE diagnostico_cid (
    id_diagnostico_cid INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_prontuario INT NOT NULL,
    codigo_cid VARCHAR(10) NOT NULL,
    descricao VARCHAR(255) NOT NULL,
    principal BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (id_prontuario) REFERENCES prontuario(id_prontuario)
);

-- Diagnóstico codificado em CIAP2 (Classificação Internacional de Atenção
-- Primária), usado em paralelo ao CID-10 — mesmo padrão do Tasy para APS.
CREATE TABLE diagnostico_ciap2 (
    id_diagnostico_ciap2 INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_prontuario INT NOT NULL,
    codigo_ciap2 VARCHAR(10) NOT NULL,
    descricao VARCHAR(255) NOT NULL,
    principal BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (id_prontuario) REFERENCES prontuario(id_prontuario)
);

-- =====================================================================
-- FASE 6 — Automação financeira
-- =====================================================================

CREATE TYPE status_pagamento AS ENUM ('pendente', 'pago', 'atrasado', 'cancelado', 'estornado');
CREATE TYPE metodo_pagamento AS ENUM ('pix', 'cartao_credito', 'cartao_debito', 'boleto', 'convenio');

CREATE TABLE fatura (
    id_fatura INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_consulta INT NOT NULL UNIQUE,
    valor NUMERIC(10, 2) NOT NULL,
    status status_pagamento NOT NULL DEFAULT 'pendente',
    vencimento DATE NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (id_consulta) REFERENCES consulta(id_consulta)
);

CREATE TABLE pagamento (
    id_pagamento INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_fatura INT NOT NULL,
    metodo metodo_pagamento NOT NULL,
    valor_pago NUMERIC(10, 2) NOT NULL,
    pago_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    referencia_externa VARCHAR(100) NULL, -- id da transação no gateway de pagamento
    FOREIGN KEY (id_fatura) REFERENCES fatura(id_fatura)
);

-- =====================================================================
-- FASE 7 — Integração com laboratórios
-- =====================================================================

CREATE TYPE status_exame AS ENUM ('solicitado', 'agendado', 'realizado', 'resultado_disponivel', 'cancelado');

CREATE TABLE exame (
    id_exame INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_paciente INT NOT NULL,
    id_consulta INT NULL, -- exame também pode ser solicitado fora de uma consulta
    nome_exame VARCHAR(150) NOT NULL,
    laboratorio VARCHAR(150) NULL,
    status status_exame NOT NULL DEFAULT 'solicitado',
    solicitado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (id_paciente) REFERENCES paciente(id_paciente),
    FOREIGN KEY (id_consulta) REFERENCES consulta(id_consulta)
);

CREATE TABLE resultado_exame (
    id_resultado INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_exame INT NOT NULL UNIQUE,
    url_arquivo TEXT NOT NULL,
    observacoes TEXT NULL,
    recebido_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (id_exame) REFERENCES exame(id_exame)
);

-- =====================================================================
-- FASE 8 — Integração com convênios
-- =====================================================================

CREATE TYPE status_autorizacao AS ENUM ('pendente', 'autorizado', 'negado', 'expirado');

CREATE TABLE autorizacao_convenio (
    id_autorizacao INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_consulta INT NOT NULL UNIQUE,
    id_convenio INT NOT NULL,
    numero_guia VARCHAR(50) NULL,
    status status_autorizacao NOT NULL DEFAULT 'pendente',
    solicitado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    respondido_em TIMESTAMPTZ NULL,
    FOREIGN KEY (id_consulta) REFERENCES consulta(id_consulta),
    FOREIGN KEY (id_convenio) REFERENCES convenio(id_convenio)
);

-- =====================================================================
-- FASE 9 — Telemedicina integrada
-- =====================================================================

CREATE TABLE teleconsulta (
    id_teleconsulta INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_consulta INT NOT NULL UNIQUE,
    link_video TEXT NOT NULL,
    plataforma VARCHAR(50) NOT NULL DEFAULT 'interno',
    gravacao_url TEXT NULL,
    iniciada_em TIMESTAMPTZ NULL,
    finalizada_em TIMESTAMPTZ NULL,
    FOREIGN KEY (id_consulta) REFERENCES consulta(id_consulta)
);

-- =====================================================================
-- Indicadores de sucesso — pesquisa de satisfação (seção 10 do vision doc)
-- =====================================================================

CREATE TABLE pesquisa_satisfacao (
    id_pesquisa INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_consulta INT NOT NULL UNIQUE,
    nota SMALLINT NULL CHECK (nota BETWEEN 0 AND 10),
    comentario TEXT NULL,
    enviado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    respondido_em TIMESTAMPTZ NULL,
    FOREIGN KEY (id_consulta) REFERENCES consulta(id_consulta)
);

-- =====================================================================
-- FASE 10 — Painel /whatsapp (conversas, assistente, desempenho)
-- =====================================================================

-- Tabela de tenant minima. O restante do schema ainda e mono-clinica (ver
-- roadmap "multiempresa" na Fase 0, ainda nao implementada) — so as tabelas
-- novas desta secao ja nascem preparadas com id_clinica, pra nao ter que
-- reescrever exatamente a parte que o produto chama de coracao do sistema.
CREATE TABLE clinica (
    id_clinica INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nome VARCHAR(150) NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TYPE estado_conversa_bot AS ENUM ('bot', 'aguardando', 'com_agente');

-- Uma linha por (clinica, telefone): a "caixa de entrada" do WhatsApp. O
-- historico de mensagens continua em `mensagem` (correlacionado por
-- telefone, mesmo padrao que a tabela ja usa) — esta tabela guarda so o
-- estado da conversa (quem esta cuidando dela agora).
CREATE TABLE conversa (
    id_conversa INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_clinica INT NOT NULL REFERENCES clinica(id_clinica),
    id_paciente INT NULL REFERENCES paciente(id_paciente),
    telefone VARCHAR(20) NOT NULL,
    estado estado_conversa_bot NOT NULL DEFAULT 'bot',
    -- Sem FK pra usuario: ainda nao existe sessao/login real (Fase 0 do
    -- roadmap). Mesmo placeholder que o frontend ja usa hoje.
    agente_nome VARCHAR(100) NULL,
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (id_clinica, telefone)
);

CREATE INDEX idx_conversa_clinica_estado ON conversa(id_clinica, estado);

-- Quem mandou cada mensagem de saida (bot ou atendente humano). Aditivo e
-- nullable — nao quebra nada que ja grava em `mensagem` hoje. Mensagens de
-- entrada (direcao='entrada') sao sempre do paciente; a aplicacao e quem
-- garante essa regra, sem CHECK constraint (mesmo estilo do resto do schema).
CREATE TYPE remetente_mensagem AS ENUM ('paciente', 'bot', 'agente');
ALTER TABLE mensagem ADD COLUMN remetente remetente_mensagem NULL;
ALTER TABLE mensagem ADD COLUMN id_usuario_remetente INT NULL REFERENCES usuario(id);

-- Liga/desliga por clinica. Nome e texto de impacto de cada capacidade sao
-- copy de produto (constante no backend), nao dado de linha.
CREATE TABLE capacidade_bot_config (
    id_clinica INT NOT NULL REFERENCES clinica(id_clinica),
    capacidade_id VARCHAR(40) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (id_clinica, capacidade_id)
);

-- Override por clinica dos textos de cada capacidade. Sem linha aqui = usa
-- o default (constante no backend, mesmo texto que ja existia hardcoded no
-- frontend).
CREATE TABLE mensagem_template_bot (
    id_clinica INT NOT NULL REFERENCES clinica(id_clinica),
    capacidade_id VARCHAR(40) NOT NULL,
    campo VARCHAR(20) NOT NULL,
    texto TEXT NOT NULL,
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (id_clinica, capacidade_id, campo)
);

-- Regras de escalonamento/horario/disparo, uma linha por clinica. JSONB
-- porque o formato ja e aninhado no frontend e nao ha necessidade de
-- consultar campo a campo via SQL.
CREATE TABLE regra_atendimento_bot (
    id_clinica INT PRIMARY KEY REFERENCES clinica(id_clinica),
    regras JSONB NOT NULL,
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------
-- Seeds — clinica 1 (mono-clinica por enquanto) com o mesmo conteudo que
-- ja estava hardcoded no frontend mock. Nao e dado inventado: e o copy
-- real que o produto ja tinha decidido, so migrando pra dentro do banco.
-- ---------------------------------------------------------------------

INSERT INTO clinica (id_clinica, nome) OVERRIDING SYSTEM VALUE VALUES (1, 'Clínica Vitalis');
SELECT setval(pg_get_serial_sequence('clinica', 'id_clinica'), 1, true);

INSERT INTO capacidade_bot_config (id_clinica, capacidade_id, ativo) VALUES
    (1, 'confirmar_presenca', TRUE),
    (1, 'consultar_horario', TRUE),
    (1, 'marcar_consulta', TRUE),
    (1, 'remarcar_cancelar', TRUE),
    (1, 'vaga_aberta', TRUE),
    (1, 'cadastro_novo', TRUE),
    (1, 'aviso_exame', FALSE);

INSERT INTO mensagem_template_bot (id_clinica, capacidade_id, campo, texto) VALUES
    (1, 'confirmar_presenca', 'saudacao', 'Oi {nome}! Confirma presença na consulta de {data} às {hora} com {medico}?' || E'\n' || '1 · confirmar · 2 · remarcar'),
    (1, 'confirmar_presenca', 'opcoes', 'Responda 1 para confirmar ou 2 se precisar remarcar.'),
    (1, 'confirmar_presenca', 'confirmacao', 'Presença confirmada para {data} às {hora}. Até lá!'),
    (1, 'confirmar_presenca', 'naoEntendi', 'Não entendi. Responda 1 para confirmar ou 2 para remarcar.'),

    (1, 'consultar_horario', 'saudacao', 'Oi {nome}! Sua próxima consulta é {data} às {hora}, com {medico} ({especialidade}).'),
    (1, 'consultar_horario', 'opcoes', 'Quer que eu remarque ou cancele esse horário?' || E'\n' || '1 · remarcar · 2 · cancelar · 3 · não, obrigado'),
    (1, 'consultar_horario', 'confirmacao', 'Certo, {nome}! Fica como está: {data} às {hora}.'),
    (1, 'consultar_horario', 'naoEntendi', 'Não entendi. Responda o número da opção ou escreva "atendente".'),

    (1, 'marcar_consulta', 'saudacao', 'Oi {nome}, tudo bem? Sou o assistente da {clinica}.'),
    (1, 'marcar_consulta', 'opcoes', 'Para qual especialidade? Ex.: cardiologia, ortopedia, dermatologia.'),
    (1, 'marcar_consulta', 'confirmacao', 'Pronto, {nome}! {data} às {hora} com {medico}.'),
    (1, 'marcar_consulta', 'naoEntendi', 'Não entendi. Responda o número da opção ou escreva "atendente".'),

    (1, 'remarcar_cancelar', 'saudacao', 'Oi {nome}! Sua consulta de {data} às {hora} precisa ser remarcada?' || E'\n' || '1 · sim · 2 · não'),
    (1, 'remarcar_cancelar', 'opcoes', 'Tenho estes horários com {medico}:' || E'\n' || '1 · próxima quinta às 14:20 · 2 · próxima sexta às 09:40'),
    (1, 'remarcar_cancelar', 'confirmacao', 'Pronto! Remarquei para {data} às {hora}.'),
    (1, 'remarcar_cancelar', 'naoEntendi', 'Não entendi. Responda 1 ou 2, ou escreva "atendente".'),

    (1, 'vaga_aberta', 'saudacao', 'Oi {nome}! Abriu uma vaga com {medico} ({especialidade}) em {data} às {hora}. Quer ficar com ela?'),
    (1, 'vaga_aberta', 'opcoes', 'Responda 1 para confirmar essa vaga ou 2 para continuar na fila de espera.'),
    (1, 'vaga_aberta', 'confirmacao', 'Vaga confirmada, {nome}! {data} às {hora} com {medico}.'),
    (1, 'vaga_aberta', 'naoEntendi', 'Não entendi. Responda 1 para confirmar a vaga ou 2 para continuar esperando.'),

    (1, 'cadastro_novo', 'saudacao', 'Oi! Não encontrei esse número no nosso cadastro. Antes de marcar, pode me dizer seu nome completo?'),
    (1, 'cadastro_novo', 'opcoes', 'Agora preciso da sua data de nascimento e, se tiver, o convênio.'),
    (1, 'cadastro_novo', 'confirmacao', 'Cadastro feito, {nome}! Agora vamos escolher o horário.'),
    (1, 'cadastro_novo', 'naoEntendi', 'Não entendi. Pode escrever seu nome completo, por favor?'),

    (1, 'aviso_exame', 'saudacao', 'Oi {nome}! O resultado do seu exame já está disponível.'),
    (1, 'aviso_exame', 'opcoes', 'Quer que eu envie por aqui ou prefere retirar na recepção da {clinica}?' || E'\n' || '1 · enviar por aqui · 2 · vou retirar'),
    (1, 'aviso_exame', 'confirmacao', 'Certo, {nome}! Já providenciamos.'),
    (1, 'aviso_exame', 'naoEntendi', 'Não entendi. Responda 1 para receber por aqui ou 2 se for retirar na recepção.');

INSERT INTO regra_atendimento_bot (id_clinica, regras) VALUES (1, '{
    "escalonamento": {
        "pedirAtendenteSempre": true,
        "naoEntendeuLimite": 2,
        "palavrasChave": ["urgente", "dor", "emergência"]
    },
    "horarios": {
        "atendimentoHumano": "seg–sex 08:00–18:00",
        "sabado": "08:00–12:00"
    },
    "disparos": {
        "confirmacaoPresencaHoras": 24,
        "avisoResultadoExameAtivo": true
    },
    "limiteMensagensPorPacientePorDia": 3
}'::jsonb);

-- =====================================================================
-- FASE 11 — Caixa
-- =====================================================================

-- 'dinheiro' nao existia no enum original (Fase 6 so previa liquidacao
-- eletronica) — a tela /caixa precisa registrar recebimento em espécie.
-- ADD VALUE roda fora de bloco de transacao explicito, por isso e um
-- statement isolado aqui.
ALTER TYPE metodo_pagamento ADD VALUE IF NOT EXISTS 'dinheiro';

-- Turno de caixa: abertura/fechamento, contagem de dinheiro na gaveta e
-- diferenca. id_clinica pronta pra multi-tenant, mesmo padrao da Fase 10
-- (reaproveita a tabela `clinica` criada la). Sem FK pra usuario: ainda
-- nao existe sessao/login real (Fase 0 do roadmap).
CREATE TABLE turno_caixa (
    id_turno_caixa INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_clinica INT NOT NULL REFERENCES clinica(id_clinica),
    aberto_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    fechado_em TIMESTAMPTZ NULL,
    operador_nome VARCHAR(100) NOT NULL,
    dinheiro_contado NUMERIC(10, 2) NULL,
    diferenca NUMERIC(10, 2) NULL,
    observacao TEXT NULL
);

CREATE INDEX idx_turno_caixa_clinica_aberto ON turno_caixa(id_clinica, fechado_em);

-- Campos que o registro de recebimento em /caixa precisa e `pagamento`
-- (Fase 6) ainda nao tinha: parcelamento, desconto com motivo obrigatorio,
-- e o turno em que o recebimento foi lançado (pra "movimento do dia" e
-- pro fechamento saber quanto dinheiro era esperado na gaveta).
ALTER TABLE pagamento ADD COLUMN parcelas SMALLINT NULL;
ALTER TABLE pagamento ADD COLUMN desconto NUMERIC(10, 2) NOT NULL DEFAULT 0;
ALTER TABLE pagamento ADD COLUMN motivo_desconto TEXT NULL;
ALTER TABLE pagamento ADD COLUMN id_turno_caixa INT NULL REFERENCES turno_caixa(id_turno_caixa);

-- =====================================================================
-- FASE 12 — Dados cadastrais completos do paciente
-- =====================================================================

-- `paciente` (Nucleo) so tinha nome/cpf/nascimento/telefone/convenio. A
-- ficha do paciente (/pacientes/:id, aba Resumo) precisa de contato e
-- endereco completos, que ainda nao existiam em lugar nenhum do schema.
ALTER TABLE paciente ADD COLUMN email VARCHAR(150) NULL;
ALTER TABLE paciente ADD COLUMN cep CHAR(8) NULL;
ALTER TABLE paciente ADD COLUMN logradouro VARCHAR(150) NULL;
ALTER TABLE paciente ADD COLUMN numero_endereco VARCHAR(10) NULL;
ALTER TABLE paciente ADD COLUMN complemento VARCHAR(100) NULL;
ALTER TABLE paciente ADD COLUMN bairro VARCHAR(100) NULL;
ALTER TABLE paciente ADD COLUMN cidade VARCHAR(100) NULL;
ALTER TABLE paciente ADD COLUMN uf CHAR(2) NULL;

-- =====================================================================
-- FASE 13 — Índices de listagem (issues #14 e #15)
-- =====================================================================

-- GET /api/faturas e GET /api/autorizacoes-convenio filtram por status
-- (e fatura tambem por periodo de vencimento) numa listagem paginada.
CREATE INDEX idx_fatura_status ON fatura(status);
CREATE INDEX idx_fatura_vencimento ON fatura(vencimento);
CREATE INDEX idx_autorizacao_convenio_status ON autorizacao_convenio(status);

-- GET /api/exames filtra por paciente/status numa listagem paginada (issue #13).
CREATE INDEX idx_exame_paciente ON exame(id_paciente);
CREATE INDEX idx_exame_status ON exame(status);

-- GET /api/pacientes busca por nome/CPF/telefone numa listagem paginada
-- (issue #11) — mesmo padrao de idx_mensagem_telefone (Fase 1).
CREATE INDEX idx_paciente_ddd_numero ON paciente(ddd, numero);

-- =====================================================================
-- FASE 14 — Convênios: auditoria, lotes e glosas
-- =====================================================================

-- --- Convênios (config) — implementado nesta fase ---

ALTER TABLE convenio ADD COLUMN contato VARCHAR(150) NULL;
ALTER TABLE convenio ADD COLUMN observacoes TEXT NULL;
-- Tocado pelo service sempre que o convenio OU qualquer plano/procedimento/
-- regra/documento obrigatorio dele muda — alimenta a coluna "Última
-- atualização" da listagem da aba Convênios.
ALTER TABLE convenio ADD COLUMN atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE TABLE convenio_plano (
    id_plano INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_convenio INT NOT NULL REFERENCES convenio(id_convenio),
    nome VARCHAR(100) NOT NULL,
    codigo VARCHAR(50) NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE convenio_procedimento (
    id_convenio_procedimento INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_convenio INT NOT NULL REFERENCES convenio(id_convenio),
    -- NULL = vale pra todos os planos do convenio; preenchido = preco/regra
    -- especifica de um plano.
    id_plano INT NULL REFERENCES convenio_plano(id_plano),
    codigo VARCHAR(30) NOT NULL,
    descricao VARCHAR(200) NOT NULL,
    valor_negociado NUMERIC(10, 2) NULL,
    cobertura BOOLEAN NOT NULL DEFAULT TRUE,
    exige_autorizacao BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TYPE tipo_regra_auditoria AS ENUM (
    'autorizacao_obrigatoria', 'documento_obrigatorio', 'codigo_incompativel',
    'procedimento_nao_coberto', 'paciente_inelegivel', 'quantidade_acima_permitido',
    'prazo_faturamento_excedido', 'profissional_nao_habilitado', 'divergencia_atendimento_faturamento'
);
CREATE TYPE severidade_regra AS ENUM ('critica', 'alta', 'media', 'baixa');

-- parametros guarda config especifica de cada regra (ex: {"quantidadeMaxima": 3})
-- pra permitir novas variacoes sem alterar o schema, como a spec pede
-- ("permitir adicionar novas regras posteriormente sem alterar a estrutura
-- principal do modulo").
CREATE TABLE regra_auditoria (
    id_regra INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_convenio INT NOT NULL REFERENCES convenio(id_convenio),
    -- NULL = regra geral do convenio; preenchido = so vale pra esse procedimento.
    id_convenio_procedimento INT NULL REFERENCES convenio_procedimento(id_convenio_procedimento),
    tipo tipo_regra_auditoria NOT NULL,
    severidade severidade_regra NOT NULL,
    descricao VARCHAR(255) NOT NULL,
    parametros JSONB NOT NULL DEFAULT '{}'::jsonb,
    ativo BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE documento_obrigatorio_convenio (
    id_documento_obrigatorio INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_convenio INT NOT NULL REFERENCES convenio(id_convenio),
    id_convenio_procedimento INT NULL REFERENCES convenio_procedimento(id_convenio_procedimento),
    nome_documento VARCHAR(150) NOT NULL,
    obrigatorio BOOLEAN NOT NULL DEFAULT TRUE
);

-- --- Auditoria, Lotes, Glosas — schema pronto agora (pra nao remodelar
-- depois), motor de regras e telas entram numa fase futura ---

CREATE TYPE status_auditoria_atendimento AS ENUM ('bloqueado', 'atencao', 'aprovado');

CREATE TABLE auditoria_atendimento (
    id_auditoria_atendimento INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_consulta INT NOT NULL UNIQUE REFERENCES consulta(id_consulta),
    status status_auditoria_atendimento NOT NULL,
    valor_em_risco NUMERIC(10, 2) NOT NULL DEFAULT 0,
    avaliado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TYPE status_auditoria_item AS ENUM ('ok', 'falha');

CREATE TABLE auditoria_item (
    id_auditoria_item INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_auditoria_atendimento INT NOT NULL REFERENCES auditoria_atendimento(id_auditoria_atendimento),
    id_regra INT NULL REFERENCES regra_auditoria(id_regra),
    status status_auditoria_item NOT NULL,
    descricao VARCHAR(255) NOT NULL,
    severidade severidade_regra NULL,
    acao_recomendada VARCHAR(255) NULL
);

CREATE TYPE status_lote_faturamento AS ENUM (
    'rascunho', 'pronto_envio', 'enviado', 'processando', 'pago_parcial', 'pago', 'com_glosas'
);

CREATE TABLE lote_faturamento (
    id_lote INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_convenio INT NOT NULL REFERENCES convenio(id_convenio),
    codigo VARCHAR(30) NOT NULL UNIQUE,
    status status_lote_faturamento NOT NULL DEFAULT 'rascunho',
    data_envio DATE NULL,
    valor_total NUMERIC(12, 2) NOT NULL DEFAULT 0,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Liga ao `fatura` ja existente em vez de duplicar valor/status.
CREATE TABLE lote_item (
    id_lote_item INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_lote INT NOT NULL REFERENCES lote_faturamento(id_lote),
    id_fatura INT NOT NULL UNIQUE REFERENCES fatura(id_fatura)
);

CREATE TYPE status_glosa AS ENUM (
    'nova', 'em_analise', 'recurso_preparacao', 'recurso_enviado', 'recuperada', 'confirmada'
);

CREATE TABLE glosa (
    id_glosa INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_fatura INT NOT NULL REFERENCES fatura(id_fatura),
    id_convenio INT NOT NULL REFERENCES convenio(id_convenio),
    motivo VARCHAR(255) NOT NULL,
    valor NUMERIC(10, 2) NOT NULL,
    prazo_recurso DATE NULL,
    status status_glosa NOT NULL DEFAULT 'nova',
    id_usuario_responsavel INT NULL REFERENCES usuario(id),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE glosa_historico (
    id_glosa_historico INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_glosa INT NOT NULL REFERENCES glosa(id_glosa),
    evento VARCHAR(255) NOT NULL,
    id_usuario INT NULL REFERENCES usuario(id),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Documentos de glosa reaproveitam `documento_anexo` (ja existe, generico
-- por id_consulta) — nao criamos tabela nova so pra isso.

CREATE INDEX idx_convenio_procedimento_convenio ON convenio_procedimento(id_convenio);
CREATE INDEX idx_regra_auditoria_convenio ON regra_auditoria(id_convenio);
CREATE INDEX idx_glosa_status ON glosa(status);
CREATE INDEX idx_glosa_prazo_recurso ON glosa(prazo_recurso);
CREATE INDEX idx_lote_faturamento_status ON lote_faturamento(status);

-- =====================================================================
-- FASE 15 — Recuperação de glosas (docs/specs/recuperacao-glosas.md)
-- =====================================================================

-- Aditivo sobre o schema da Fase 14 (glosa/glosa_historico) — não remodela
-- nada que já existia, só completa o que a spec de recuperação pede além
-- do registro básico da glosa. "negada" e "recuperada_parcialmente" fecham
-- os desfechos que status_glosa ainda não cobria.
ALTER TYPE status_glosa ADD VALUE IF NOT EXISTS 'negada';
ALTER TYPE status_glosa ADD VALUE IF NOT EXISTS 'recuperada_parcialmente';

-- valor da glosa (Fase 14) já existe como `glosa.valor`; aqui só o que
-- faltava pra seção 3-5 da spec. "Lote de origem" não vira coluna: dá pra
-- achar via lote_item.id_fatura = glosa.id_fatura. "Plano" fica de fora —
-- paciente/fatura não guardam id_plano em lugar nenhum do schema ainda.
-- Distinta de criado_em: origem='importada' pode registrar uma glosa que o
-- convênio emitiu antes de alguém lançar no sistema.
ALTER TABLE glosa ADD COLUMN data_glosa DATE NOT NULL DEFAULT CURRENT_DATE;
ALTER TABLE glosa ADD COLUMN valor_faturado NUMERIC(10, 2) NULL;
ALTER TABLE glosa ADD COLUMN codigo_motivo VARCHAR(50) NULL;
ALTER TABLE glosa ADD COLUMN origem VARCHAR(10) NOT NULL DEFAULT 'manual'
    CHECK (origem IN ('importada', 'manual'));
ALTER TABLE glosa ADD COLUMN recorribilidade VARCHAR(20) NULL
    CHECK (recorribilidade IN ('recorrivel', 'nao_recorrivel', 'necessita_analise'));
ALTER TABLE glosa ADD COLUMN categoria_motivo VARCHAR(20) NULL
    CHECK (categoria_motivo IN (
        'autorizacao', 'documentacao', 'codigo_procedimento', 'elegibilidade',
        'cobertura', 'cobranca', 'prazo', 'duplicidade', 'outros'
    ));

-- O recurso é uma entidade própria (não cabe em glosa.status, que só
-- rastreia o estágio macro) — seção 6-12 da spec: justificativa, envio,
-- prazo e resultado (valor recuperado x não recuperado) por tentativa de
-- recurso. glosa.status continua espelhando o desfecho pra listagem/KPI.
CREATE TYPE status_recurso_glosa AS ENUM (
    'rascunho', 'em_preparacao', 'enviado', 'aguardando_retorno', 'em_analise_convenio',
    'recuperado', 'recuperado_parcialmente', 'negado', 'prazo_expirado'
);
CREATE TYPE canal_envio_recurso AS ENUM ('manual', 'portal_convenio', 'email');

CREATE TABLE recurso_glosa (
    id_recurso INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_glosa INT NOT NULL REFERENCES glosa(id_glosa),
    status status_recurso_glosa NOT NULL DEFAULT 'rascunho',
    justificativa TEXT NULL,
    prazo_limite DATE NULL,
    id_usuario_responsavel INT NULL REFERENCES usuario(id),
    canal_envio canal_envio_recurso NULL,
    protocolo VARCHAR(100) NULL,
    enviado_em TIMESTAMPTZ NULL,
    id_usuario_envio INT NULL REFERENCES usuario(id),
    respondido_em TIMESTAMPTZ NULL,
    valor_recuperado NUMERIC(10, 2) NULL,
    valor_nao_recuperado NUMERIC(10, 2) NULL,
    motivo_negativa TEXT NULL,
    documento_resposta_url TEXT NULL,
    -- Checklist (seção 8) trata "documentação anexada" e "evidências
    -- conferidas" como itens distintos — anexar é automático (documento
    -- vinculado), conferir é uma confirmação manual de quem prepara o recurso.
    evidencias_conferidas BOOLEAN NOT NULL DEFAULT FALSE,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Evidências do recurso (seção 6) — "documentos existentes no ClinicOS
-- devem poder ser selecionados sem precisar fazer novo upload": quando a
-- evidência é um arquivo real, liga em documento_anexo; quando é só a
-- existência de um registro (prontuário/guia/autorização), fica NULL e o
-- tipo já basta.
CREATE TABLE recurso_glosa_documento (
    id_recurso_glosa_documento INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_recurso INT NOT NULL REFERENCES recurso_glosa(id_recurso),
    tipo VARCHAR(30) NOT NULL
        CHECK (tipo IN ('prontuario', 'guia', 'solicitacao_medica', 'autorizacao', 'laudo', 'comprovante', 'outro')),
    id_documento_anexo INT NULL REFERENCES documento_anexo(id_documento),
    descricao VARCHAR(255) NULL
);

CREATE INDEX idx_recurso_glosa_glosa ON recurso_glosa(id_glosa);
CREATE INDEX idx_recurso_glosa_status ON recurso_glosa(status);
CREATE INDEX idx_recurso_glosa_prazo ON recurso_glosa(prazo_limite);
CREATE INDEX idx_recurso_glosa_documento_recurso ON recurso_glosa_documento(id_recurso);

-- =====================================================================
-- FASE 16 — Retorno de pacientes (docs/specs/retorno.md)
-- =====================================================================

-- Data que o médico indicou pro retorno ("retorno em X dias"), calculada
-- em dias -> data absoluta no momento de finalizar o atendimento. É a
-- ponte entre o prontuário e o grupo "retorno pedido pelo médico" —
-- sem isso, /retorno não tem como saber que um retorno foi pedido.
ALTER TABLE prontuario ADD COLUMN retorno_sugerido_em DATE NULL;

CREATE TYPE grupo_retorno AS ENUM (
    'tratamento_interrompido', 'retorno_medico', 'exame_pendente', 'ritmo_quebrado'
);

-- Estado de contato por paciente, aplicado nos 4 grupos igualmente — sem
-- isso a mesma pessoa reaparece toda semana mesmo depois de pedir pra não
-- ser mais contatada (spec: "o não contatar é obrigatório").
CREATE TABLE paciente_retorno_status (
    id_paciente INT PRIMARY KEY REFERENCES paciente(id_paciente),
    status VARCHAR(15) NOT NULL DEFAULT 'pendente'
        CHECK (status IN ('pendente', 'adiado', 'nao_contatar')),
    adiado_ate DATE NULL,
    motivo_nao_contatar TEXT NULL,
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Uma régua por grupo (gatilho fixo = o próprio grupo, prazo em dias e
-- liga/desliga editáveis). Sem @Scheduled em lugar nenhum do backend
-- ainda — a régua fica configurável e visível (o que a spec pede: "a tela
-- existe pra configurar e acompanhar"), disparo automático fica pra uma
-- fase futura, mesmo espírito da Auditoria de /convenios (Fase 14).
CREATE TABLE regua_retorno (
    id_regua INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    grupo grupo_retorno NOT NULL UNIQUE,
    prazo_dias SMALLINT NOT NULL,
    ativa BOOLEAN NOT NULL DEFAULT TRUE,
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Texto editável por grupo (spec: "cada grupo tem um texto diferente...
-- usar o mesmo texto pros quatro derruba a conversão").
CREATE TABLE mensagem_modelo_retorno (
    id_modelo INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    grupo grupo_retorno NOT NULL UNIQUE,
    texto TEXT NOT NULL
);

-- Histórico de envios — alimenta a aba Resultados (mensagens enviadas,
-- conversão por grupo, receita gerada). id_mensagem liga no registro real
-- em `mensagem` (Fase 10, compartilhada com /whatsapp); id_usuario_disparou
-- NULL = disparo automático (régua), preenchido = alguém clicou "enviar".
CREATE TABLE envio_retorno (
    id_envio INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_paciente INT NOT NULL REFERENCES paciente(id_paciente),
    grupo grupo_retorno NOT NULL,
    texto TEXT NOT NULL,
    id_mensagem BIGINT NULL REFERENCES mensagem(id_mensagem),
    id_usuario_disparou INT NULL REFERENCES usuario(id),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_envio_retorno_paciente ON envio_retorno(id_paciente);
CREATE INDEX idx_envio_retorno_criado_em ON envio_retorno(criado_em);

-- Seed: réguas de exemplo da spec (3 delas) + uma 4ª pro grupo ritmo_quebrado
-- (a spec não dá exemplo desse grupo, completo por consistência — todos os
-- 4 grupos precisam de régua pra Aba 2 fazer sentido).
INSERT INTO regua_retorno (grupo, prazo_dias, ativa) VALUES
    ('retorno_medico', 15, TRUE),
    ('exame_pendente', 20, TRUE),
    ('tratamento_interrompido', 10, FALSE),
    ('ritmo_quebrado', 30, TRUE);

-- Seed: um texto padrão por grupo (spec, seção "Mensagem por grupo") —
-- editável depois via "Personalizar mensagem".
INSERT INTO mensagem_modelo_retorno (grupo, texto) VALUES
    ('tratamento_interrompido', 'Olá! Vimos que você não concluiu seu tratamento. Podemos te ajudar a retomar? Temos horários disponíveis essa semana.'),
    ('retorno_medico', 'Olá! O(a) Dr(a). {medico} pediu seu retorno e já passou o prazo indicado. Vamos agendar?'),
    ('exame_pendente', 'Olá! Notamos que o exame solicitado na sua última consulta ainda não foi realizado. Posso te ajudar a agendar?'),
    ('ritmo_quebrado', 'Olá! Faz um tempo que você não vem aqui. Que tal agendar uma consulta de acompanhamento?');
