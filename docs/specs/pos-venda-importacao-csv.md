# Importação CSV para o pós-venda

## Objetivo

Receber pacientes e atendimentos exportados por uma agenda externa sem criar consultas artificiais na agenda do Clinisis. O arquivo alimenta um contrato normalizado que também será usado pelos conectores da issue #36.

## Identidade e idempotência

- A clínica autenticada é sempre obtida da sessão; o arquivo não escolhe o tenant.
- `fonte + paciente_id` identifica o paciente na origem.
- `fonte + atendimento_id` identifica o atendimento na origem.
- Reimportar a mesma linha não cria outro paciente, atendimento ou caso.
- Uma linha mais antiga que `atualizado_em` não substitui o estado mais recente.
- Um atendimento já associado a outro paciente fica em revisão e não é religado automaticamente.

## Colunas

| Campo | Obrigatório | Uso |
|---|---:|---|
| ID externo do paciente | Sim | Identidade estável na fonte |
| Nome do paciente | Sim | Operação da fila |
| Telefone | Sim | Contato, normalizado para 10 a 15 dígitos |
| ID externo do atendimento | Sim | Idempotência e futuras atualizações |
| Data e hora | Sim | Instante do atendimento no fuso informado |
| Profissional | Sim | Contexto operacional |
| Especialidade | Sim | Contexto operacional |
| Tipo/procedimento | Sim | Contexto operacional |
| Situação | Sim | Mapeada explicitamente antes da importação |
| Atualizado em | Não | Ordenação de correções da fonte |

Datas aceitas: ISO 8601, `aaaa-MM-dd HH:mm` e `dd/MM/aaaa HH:mm`. O arquivo deve estar em UTF-8, ter até 5 MB e até 20.000 linhas.

## Situações normalizadas

- `FALTOU` abre um caso de recuperação.
- `REALIZADO`, `AGENDADO`, `CONFIRMADO` e `CANCELADO` são armazenados como informado pela fonte.
- `DESCONHECIDO` registra estados administrativos sem inferir presença.
- Autorização de convênio, faturamento e pagamento nunca são sugeridos como comparecimento.
- A conciliação de um novo atendimento com um caso anterior pertence à issue #39. Esta entrega não considera um reagendamento como recuperação.

## Auditoria e privacidade

Cada processamento registra arquivo, SHA-256, fonte, fuso, responsável, horário e resultado por linha. Linhas com problema guardam número, identificadores e motivo; o conteúdo integral da linha não é persistido no log de importação.

Use somente arquivos autorizados pela clínica. O exemplo em `docs/examples/pos-venda-importacao.csv` contém dados fictícios.
