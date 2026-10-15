# Integração com sistemas clínicos

A API pública recebe eventos dos sistemas que a clínica já utiliza e os converte para o mesmo modelo externo usado pela importação CSV. O contrato executável fica disponível em `GET /api/integracoes/v1/openapi.yaml` e no arquivo `backend/src/main/resources/openapi/integracao-clinicas-v1.yaml`.

## Segurança e isolamento

Um administrador cria uma credencial vinculada a uma fonte. A chave aleatória é exibida uma vez; somente seu SHA-256 fica armazenado. A credencial determina a clínica, a fonte e os escopos `eventos:escrever` e `estado:ler`. Não existe campo de tenant no contrato público.

Credenciais podem ser revogadas sem afetar as demais fontes. Os dados normalizados, eventos e mapeamentos usam RLS. A tabela de autenticação contém apenas metadados e o hash necessário para descobrir o tenant antes de abrir a transação isolada.

## Processamento

Cada envio possui um `eventoId`. Repetir o mesmo identificador e conteúdo devolve o resultado anterior, inclusive quando os reenvios chegam ao mesmo tempo. Reutilizá-lo com outro conteúdo retorna conflito. `atualizadoEmFonte` impede que eventos atrasados sobrescrevam versões mais recentes.

O fluxo esperado é:

1. `PACIENTE_ATUALIZADO` cria ou atualiza a identidade externa.
2. `ATENDIMENTO_ATUALIZADO` exige o identificador desse paciente e nunca associa por nome ou telefone.
3. `SITUACAO_ATENDIMENTO_ATUALIZADA` altera somente a situação de um atendimento já conhecido.

Associações ausentes e situações sem mapeamento ficam em `REVISAO`. O payload necessário para reprocessar permanece protegido no banco e nunca é devolvido nas listagens ou registrado em logs. Um administrador pode reprocessar depois de corrigir a dependência.

Somente valores explicitamente configurados como presença (`Realizado`, `Atendido`, `Compareceu` ou `Concluído`) podem mapear para `REALIZADO`. Autorização de convênio, pagamento, faturamento e confirmação não contam como comparecimento.

## Matriz de compatibilidade

| Conector | Entrada | Estado | Observação |
| --- | --- | --- | --- |
| Clinisis Standard API v1 | REST/JSON | Validado por contrato e testes locais | Contrato de referência para integrações diretas e adaptadores. |
| CSV canônico | Arquivo | Validado | Alternativa para sistemas sem API. |
| Fornecedores comerciais | API, webhook ou CSV | A validar com a clínica piloto | Nenhum fornecedor é anunciado como compatível sem documentação e credenciais de teste. |

Para validar um fornecedor, registrar autenticação, limites, webhooks, ambiente de teste, campos disponíveis e restrições contratuais. O adaptador deve implementar `ClinicaIntegrationAdapter`; as regras de tenant, idempotência, associação e acompanhamento permanecem no núcleo.

## Operação

`GET /api/pos-venda/integracoes` mostra fontes, última sincronização, estado e pendências. A listagem de eventos não expõe o payload. Eventos em revisão ou erro podem ser reprocessados pelo endpoint administrativo. A API nunca cancela, libera nem modifica vagas no sistema de origem.
