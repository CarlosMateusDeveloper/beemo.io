-- Migração pra bancos já criados a partir de uma versão anterior de
-- schema_clinica.sql. Roda uma vez:
--   psql -U clinica -d clinica -f database/migrations/007_retorno_pacientes.sql
--
-- Schema completo da tela /retorno (docs/specs/retorno.md) — "quem deveria
-- ter voltado e não voltou".

ALTER TABLE prontuario ADD COLUMN retorno_sugerido_em DATE NULL;

CREATE TYPE grupo_retorno AS ENUM (
    'tratamento_interrompido', 'retorno_medico', 'exame_pendente', 'ritmo_quebrado'
);

CREATE TABLE paciente_retorno_status (
    id_paciente INT PRIMARY KEY REFERENCES paciente(id_paciente),
    status VARCHAR(15) NOT NULL DEFAULT 'pendente'
        CHECK (status IN ('pendente', 'adiado', 'nao_contatar')),
    adiado_ate DATE NULL,
    motivo_nao_contatar TEXT NULL,
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE regua_retorno (
    id_regua INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    grupo grupo_retorno NOT NULL UNIQUE,
    prazo_dias SMALLINT NOT NULL,
    ativa BOOLEAN NOT NULL DEFAULT TRUE,
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE mensagem_modelo_retorno (
    id_modelo INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    grupo grupo_retorno NOT NULL UNIQUE,
    texto TEXT NOT NULL
);

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

INSERT INTO regua_retorno (grupo, prazo_dias, ativa) VALUES
    ('retorno_medico', 15, TRUE),
    ('exame_pendente', 20, TRUE),
    ('tratamento_interrompido', 10, FALSE),
    ('ritmo_quebrado', 30, TRUE);

INSERT INTO mensagem_modelo_retorno (grupo, texto) VALUES
    ('tratamento_interrompido', 'Olá! Vimos que você não concluiu seu tratamento. Podemos te ajudar a retomar? Temos horários disponíveis essa semana.'),
    ('retorno_medico', 'Olá! O(a) Dr(a). {medico} pediu seu retorno e já passou o prazo indicado. Vamos agendar?'),
    ('exame_pendente', 'Olá! Notamos que o exame solicitado na sua última consulta ainda não foi realizado. Posso te ajudar a agendar?'),
    ('ritmo_quebrado', 'Olá! Faz um tempo que você não vem aqui. Que tal agendar uma consulta de acompanhamento?');
