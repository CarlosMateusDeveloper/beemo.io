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
    ativo BOOLEAN DEFAULT TRUE,
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
    descricao TEXT NOT NULL,
    -- Objetivo
    exame_fisico TEXT NULL,
    -- Avaliação
    hipotese_diagnostica TEXT NULL,
    diagnostico TEXT NOT NULL,
    tipo_diagnostico tipo_diagnostico NOT NULL DEFAULT 'DEFINITIVO',
    -- Plano
    prescricao TEXT NOT NULL,
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
