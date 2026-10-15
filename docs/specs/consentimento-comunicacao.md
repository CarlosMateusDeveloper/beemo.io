# Consentimento e modelos de comunicação

O Clinisis decide a elegibilidade no servidor imediatamente antes de cada tentativa de envio. A decisão considera clínica, paciente ou telefone, canal, finalidade, caráter essencial e versão do modelo. Toda tentativa permitida ou bloqueada fica no histórico imutável com a versão exata do consentimento e do modelo usados na decisão.

## Finalidades

- `atendimento`: resposta solicitada pelo paciente ou continuidade de uma conversa ativa.
- `pos_venda`: recuperação, acompanhamento e retenção após atendimento.
- `campanha`: comunicação segmentada iniciada pela clínica.
- `transacional`: informação necessária para executar um serviço contratado.

Uma comunicação não essencial exige consentimento vigente para canal e finalidade e, nos canais digitais, um modelo aprovado. Ausência de evidência é tratada como bloqueio. A importação CSV preserva decisão, texto, data e referência da evidência quando fornecidos; pacientes importados sem esses campos recebem uma revogação técnica `importacao_sem_evidencia` até regularização.

## Descadastro

No WhatsApp, mensagens exatas como `sair`, `pare`, `parar`, `cancelar`, `descadastrar`, `remover`, `não quero receber`, `não me envie` e `não enviar mais` criam imediatamente uma revogação para o canal. O bot não responde ao evento e encaminha a conversa à equipe. Frases operacionais como “quero cancelar minha consulta” não são interpretadas como descadastro.

Uma nova autorização nunca sobrescreve a anterior: ela cria outro evento. A decisão atual é o evento mais recente compatível com canal e finalidade. O histórico não permite atualização nem exclusão.

## Ciclo dos modelos

Cada alteração do conteúdo cria uma versão imutável. Submissão, aprovação, rejeição e inativação criam eventos de estado imutáveis sobre essa versão. As transições aceitas são:

1. `rascunho` → `submetido` ou `inativo`;
2. `submetido` → `aprovado`, `rejeitado` ou `inativo`;
3. `aprovado` ou `rejeitado` → `inativo`.

A aprovação exige provedor e identificador do modelo no provedor, desativando qualquer versão anteriormente aprovada do mesmo código. A rejeição exige motivo. Campanhas agendadas ou iniciadas passam por uma nova avaliação de público e excluem pacientes inelegíveis com o motivo registrado.

## APIs e acesso

As rotas ficam sob `/api/pos-venda/comunicacao`: consulta e registro do consentimento do paciente, criação de rascunho, transição, catálogo e versões dos modelos. Administradores gerenciam consentimentos e modelos; recepcionistas gerenciam consentimentos e consultam modelos; médicos consultam consentimentos. Financeiro não recebe acesso a dados de comunicação.

## Liberação comercial

A implementação aplica uma política conservadora e mantém evidências técnicas, mas a redação dos termos, as bases legais, os prazos de retenção e a classificação do que é essencial precisam de revisão jurídica/LGPD antes da liberação comercial. Essa revisão é um gate de lançamento e deve ser registrada pela empresa responsável pelo produto.
