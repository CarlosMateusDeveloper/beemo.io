-- Migração pra bancos já criados a partir de uma versão anterior de
-- schema_clinica.sql. Roda uma vez:
--   psql -U clinica -d clinica -f database/migrations/008_despesas.sql
--
-- Schema de despesas/contas a pagar (/caixa/despesas) — primeiro dado de
-- custo operacional do sistema, além do repasse médico. Alimenta também o
-- DRE (CaixaDreService) e o Fluxo de Caixa Consolidado (CaixaFluxoConsolidadoService).
--
-- status/categoria ficam VARCHAR + CHECK, não enum nativo do Postgres —
-- mesmo padrão de Glosa.status/Medico.status. Dois módulos nesta mesma leva
-- (recurso_glosa, regra_auditoria) bateram no bug de ddl-auto do Hibernate
-- criar a coluna como VARCHAR antes desta migração rodar com o enum nativo,
-- quebrando a escrita via repository.save() depois; VARCHAR evita a classe
-- inteira do problema porque não há tipo nativo pra colidir.
--
-- Sem status "atrasado" armazenado: não existe job/cron neste sistema pra
-- fazer essa transição sozinho (tudo é request-driven) — "atrasado" é
-- calculado na leitura (status='pendente' AND vencimento < hoje).

CREATE TABLE despesa (
    id_despesa INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_clinica INT NOT NULL REFERENCES clinica(id_clinica),
    descricao VARCHAR(200) NOT NULL,
    categoria VARCHAR(30) NOT NULL
        CHECK (categoria IN ('aluguel', 'folha', 'fornecedores', 'insumos', 'impostos', 'marketing', 'manutencao', 'servicos', 'outros')),
    valor NUMERIC(10, 2) NOT NULL,
    vencimento DATE NOT NULL,
    pago_em DATE NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'pendente'
        CHECK (status IN ('pendente', 'pago', 'cancelado')),
    fornecedor VARCHAR(150) NULL,
    observacoes TEXT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_despesa_clinica_vencimento ON despesa(id_clinica, vencimento);
CREATE INDEX idx_despesa_status ON despesa(status);
