-- Issue #19: índices das coortes e eventos usados nas métricas rastreáveis.
-- Aplicar depois de 016_pos_venda_operacao.sql.
BEGIN;

CREATE INDEX IF NOT EXISTS idx_pos_venda_caso_coorte
    ON pos_venda_caso(id_clinica,data_falta,status,id);
CREATE INDEX IF NOT EXISTS idx_pos_venda_evento_metricas
    ON pos_venda_evento(id_clinica,id_caso,tipo,criado_em);
CREATE INDEX IF NOT EXISTS idx_pos_venda_consulta_caso
    ON pos_venda_consulta(id_clinica,id_caso,id_consulta);

INSERT INTO auth_schema_version(version) VALUES (17) ON CONFLICT DO NOTHING;
COMMIT;
