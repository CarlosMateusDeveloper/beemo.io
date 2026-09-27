BEGIN;
CREATE TABLE IF NOT EXISTS auth_session (
    id VARCHAR(36) PRIMARY KEY,
    id_usuario INTEGER NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    expira_em TIMESTAMPTZ NOT NULL,
    revogada_em TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_auth_session_expira ON auth_session(expira_em);
CREATE TABLE IF NOT EXISTS auth_oauth_identity (
    id VARCHAR(36) PRIMARY KEY,
    id_usuario INTEGER NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    provider VARCHAR(30) NOT NULL,
    issuer VARCHAR(400) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    CONSTRAINT uq_oauth_subject UNIQUE(issuer,subject),
    CONSTRAINT uq_oauth_usuario_provider UNIQUE(id_usuario,provider)
);
COMMIT;
