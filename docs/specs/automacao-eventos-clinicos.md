# Automação por eventos clínicos

A migração `025_automacao_eventos_clinicos.sql` converte mudanças da Agenda e do prontuário em eventos versionados do pós-venda. A publicação acontece no PostgreSQL, portanto funciona para alterações feitas pela API Java, pelo serviço Go ou por uma integração que grave no mesmo modelo.

## Catálogo

- `CONSULTA_CRIADA`
- `CONSULTA_CONFIRMADA`
- `CONSULTA_CANCELADA`
- `CONSULTA_FALTOU`
- `CONSULTA_REALIZADA`
- `PRONTUARIO_PROCEDIMENTO_CONCLUIDO`
- `PRONTUARIO_RETORNO_INDICADO`

Cada evento registra identificador idempotente, clínica, versão do contrato, instante, tipo e versão da origem, paciente e payload de contexto. Uma mudança que não altera o estado não publica outro evento. Uma versão atrasada é preservada para auditoria e marcada como ignorada quando uma versão posterior já foi processada.

## Regras e ações

As regras são isoladas por clínica, ordenadas por prioridade e podem ser ativadas ou pausadas. As ações disponíveis são criar ou atualizar oportunidade, criar tarefa, inscrever paciente em jornada e avançar, pausar ou concluir jornadas. A migração cria regras iniciais para faltas e atualizações de comparecimento, mantendo o comportamento operacional existente.

Uma inscrição automática exige jornada ativa. Jornadas com etapas de WhatsApp ou e-mail exigem consentimento concedido para o canal e finalidade de pós-venda. A opção **não contatar** bloqueia tarefas de contato e inscrições, mas o evento continua auditável.

Cada combinação de evento e regra possui uma única execução em `pos_venda_automacao_execucao`, com explicação, objetos criados e erro. Reprocessar um evento com erro tenta apenas as execuções que falharam; ações concluídas não são repetidas.

## API administrativa

- `GET /api/pos-venda/automacoes/catalogo`
- `GET|POST /api/pos-venda/automacoes/regras`
- `PUT /api/pos-venda/automacoes/regras/{id}`
- `GET /api/pos-venda/automacoes/eventos`
- `GET /api/pos-venda/automacoes/eventos/{id}`
- `POST /api/pos-venda/automacoes/eventos/{id}/reprocessar`

Leitura exige acesso às oportunidades. Configuração e reprocessamento exigem `jornada.configurar` e entram no log de auditoria de acesso.
