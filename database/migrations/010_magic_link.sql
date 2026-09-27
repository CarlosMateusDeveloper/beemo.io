BEGIN;
CREATE TABLE IF NOT EXISTS auth_magic_token (
    hash VARCHAR(64) PRIMARY KEY,
    id_usuario INTEGER NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    email VARCHAR(100) NOT NULL,
    expira_em TIMESTAMPTZ NOT NULL,
    usado_em TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_magic_expira ON auth_magic_token(expira_em);
COMMIT;
