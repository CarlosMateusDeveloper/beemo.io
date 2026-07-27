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

-- 'paciente' não entra aqui: paciente nunca cria conta/login, a identidade
-- dele é o número de WhatsApp (ver paciente + mensagem.telefone). usuario
-- é só para quem de fato acessa um painel: médico e administrador.
CREATE TYPE perfil_usuario AS ENUM ('medico', 'administrador');

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
    FOREIGN KEY (id_convenio) REFERENCES convenio(id_convenio)
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
    descricao TEXT NOT NULL,
    prescricao TEXT NOT NULL,
    diagnostico TEXT NOT NULL,
    UNIQUE (id_consulta),
    FOREIGN KEY (id_consulta) REFERENCES consulta(id_consulta)
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

-- Diagnóstico codificado (CID). Muitos-para-muitos: um prontuário pode ter
-- mais de um código associado.
CREATE TABLE diagnostico_cid (
    id_prontuario INT NOT NULL,
    codigo_cid VARCHAR(10) NOT NULL,
    descricao VARCHAR(255) NOT NULL,
    PRIMARY KEY (id_prontuario, codigo_cid),
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
