# CRM: primeiro contato até consulta

O CRM mantém **interessado**, **oportunidade**, **paciente** e **consulta** como registros diferentes. Nome e um meio de contato bastam para abrir uma oportunidade. CPF, nascimento e prontuário só aparecem quando a recepção cria ou escolhe um paciente para concluir o agendamento.

## Fluxo

1. O primeiro contato cria uma oportunidade com origem, serviço, responsável e próxima ação. Eventos do WhatsApp usam a `wamid` como chave idempotente.
2. As etapas válidas são `novo`, `em_contato`, `qualificado`, `aguardando_agendamento`, `agendado` e `perdido`. Toda transição registra autoria e versão; uma perda exige motivo.
3. Respostas automáticas e humanas são eventos distintos. O SLA usa apenas `primeira_resposta_humana_em`.
4. A conversão escolhe um paciente existente ou reúne os dados mínimos de um novo paciente e cria uma linha real em `agenda` e `consulta`.
5. CPF, e-mail ou telefone semelhantes interrompem a criação automática e abrem uma revisão. Um telefone pode pertencer a várias pessoas; ele nunca associa prontuários por conta própria.
6. Mudanças posteriores da consulta registram cancelamento ou reagendamento no histórico imutável da oportunidade.

## Indicadores

As visões por origem e responsável contam oportunidades pelo momento do primeiro contato. O numerador da conversão contém somente oportunidades com `id_consulta`; bot, cadastro de interessado e cadastro de paciente não contam como agendamento. O endpoint devolve as definições junto dos valores para evitar mudança silenciosa de denominador.

## Segurança

Todas as tabelas usam RLS forçada por `app.tenant_id` e chaves estrangeiras compostas pelo tenant. Leitura exige `oportunidade.visualizar`; qualquer escrita exige `oportunidade.gerenciar`. A recepção acessa os dados comerciais do interessado, sem acesso implícito ao prontuário clínico.

Aplicar `database/migrations/027_crm_conversao.sql` depois da migração 026.
