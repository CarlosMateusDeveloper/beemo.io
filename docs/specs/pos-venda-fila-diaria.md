# Fila diária de recuperação

## Objetivo

A tela de Oportunidades é a entrada operacional do pós-venda. Ela ordena o trabalho que exige ação humana sem criar outro cadastro para o mesmo caso.

## Etapas operacionais

- **Organizar:** caso aberto sem responsável ou sem próxima ação.
- **Primeira tentativa:** nenhuma interação de contato foi registrada.
- **Nova tentativa:** já houve contato e o prazo seguinte venceu.
- **Resposta pendente:** o paciente respondeu e aguarda ação da equipe.
- **Conferir presença:** o horário reagendado passou, mas a agenda ainda não informou o resultado.
- **Aguardar consulta:** reagendamento futuro; não exige prazo artificial de contato.

Casos recuperados, corrigidos, encerrados ou marcados como não contatar não entram nas filas de contato.

## Alertas persistentes

Os alertas são registros por clínica e caso. Um índice parcial permite somente um alerta aberto de cada tipo, evitando duplicação quando a tela atualiza. Quando a condição deixa de existir, o alerta é resolvido; se o problema reaparecer depois, um novo alerta pode ser aberto e preserva a cronologia.

## Auditoria e concorrência

Responsável e próxima ação continuam no `pos_venda_caso`. A alteração usa a versão do caso para impedir sobrescrita concorrente e gera evento com autor, valor anterior e valor novo. Respostas recebidas também geram evento e permanecem pendentes até uma ação de contato, reagendamento ou encerramento.

## Integrações futuras

O endpoint de ações aceita `resposta_recebida` para que a integração oficial de WhatsApp registre a resposta no mesmo acompanhamento. A integração automática pertence às issues de WhatsApp; a fila já trata o evento sem depender dela.
