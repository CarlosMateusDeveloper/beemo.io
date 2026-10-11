-- Issue #38: fila diária e alertas persistentes do pós-venda.
-- Aplicar depois de 018_pos_venda_importacao_csv.sql.
BEGIN;

CREATE TABLE IF NOT EXISTS pos_venda_alerta (
    id_alerta BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_clinica INTEGER NOT NULL DEFAULT nullif(current_setting('app.tenant_id',true),'')::integer REFERENCES clinica(id_clinica),
    id_caso BIGINT NOT NULL,
    tipo VARCHAR(30) NOT NULL CHECK (tipo IN (
        'sem_responsavel','sem_proxima_acao','prazo_vencido','resposta_pendente','conferir_presenca'
    )),
    status VARCHAR(12) NOT NULL DEFAULT 'aberto' CHECK (status IN ('aberto','resolvido')),
    mensagem VARCHAR(500) NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolvido_em TIMESTAMPTZ,
    UNIQUE(id_clinica,id_alerta),
    FOREIGN KEY(id_clinica,id_caso) REFERENCES pos_venda_caso(id_clinica,id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX IF NOT EXISTS pos_venda_alerta_aberto_uk
    ON pos_venda_alerta(id_clinica,id_caso,tipo) WHERE status='aberto';
CREATE INDEX IF NOT EXISTS pos_venda_alerta_fila_idx
    ON pos_venda_alerta(id_clinica,status,tipo,criado_em,id_caso);
CREATE INDEX IF NOT EXISTS pos_venda_evento_caso_tipo_idx
    ON pos_venda_evento(id_clinica,id_caso,tipo,criado_em DESC);

ALTER TABLE pos_venda_alerta ENABLE ROW LEVEL SECURITY;
ALTER TABLE pos_venda_alerta FORCE ROW LEVEL SECURITY;
DO $rls$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies
        WHERE schemaname=current_schema() AND tablename='pos_venda_alerta' AND policyname='tenant_isolation'
    ) THEN
        CREATE POLICY tenant_isolation ON pos_venda_alerta
            USING(id_clinica=nullif(current_setting('app.tenant_id',true),'')::integer)
            WITH CHECK(id_clinica=nullif(current_setting('app.tenant_id',true),'')::integer);
    END IF;
END $rls$;

INSERT INTO auth_schema_version(version) VALUES (19) ON CONFLICT DO NOTHING;
COMMIT;
