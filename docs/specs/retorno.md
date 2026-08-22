# Spec — Tela /retorno (ClinicOS)

## Premissa

Esta tela responde a uma pergunta que nenhuma outra do sistema responde: **quem deveria ter voltado e não voltou.**

A Agenda sabe quem tem consulta marcada. Pacientes sabe quem está cadastrado. Ninguém sabe quem está faltando — e essa é a única fila do sistema que não existe em lugar nenhum hoje.

É também a única funcionalidade do ClinicOS que **gera receita nova** em vez de recuperar perda operacional: glosa devolve dinheiro que já era da clínica, vaga preenchida evita perder o que já estava agendado, mas trazer um paciente de volta cria uma consulta que não existiria.

**Por que é tela e não aba de Pacientes:** `/pacientes` é reativo (alguém liga, você busca); `/retorno` é proativo (ninguém pediu nada, a clínica vai atrás). Trabalho proativo escondido em aba de outra tela não é aberto.

**Depende de dado que sistema separado não tem:** o "retorno em 30 dias" que o médico escreveu no prontuário. É a ponte entre o módulo Médico e a operação da clínica.

---

## Rota

`/retorno` — item próprio no menu do módulo Operacional.

Permissão: Recepção, Gestor, Dono. Faturamento não precisa.

---

## Estrutura

Três abas: **Pendentes** (padrão) · **Réguas** · **Resultados**.

### Cabeçalho

À esquerda, o número que define a tela:

> Deveriam ter voltado
> **142 pacientes**
> R$ 17.040 em consultas não agendadas

O valor é estimado: número de pacientes × ticket médio da especialidade. Deve estar identificado como estimativa em tooltip.

À direita: abas e filtro de período.

---

## Aba 1 — Pendentes

### Agrupamento

A tela **nunca** lista 142 nomes na entrada. Agrupados por motivo, viram quatro decisões. Cada grupo é uma linha; clicar abre a lista completa daquele grupo.

**Ordem: por valor, não por quantidade.** Tratamento interrompido tem poucos pacientes mas alto valor unitário e urgência clínica; ritmo quebrado tem muita gente e é o contato mais frio.

| Grupo | Como é detectado | Exemplo |
|---|---|---|
| **Tratamento interrompido** | Série de sessões iniciada e não concluída, sem agendamento futuro | 18 · R$ 5.400 |
| **Retorno pedido pelo médico** | Campo "retorno em X dias" do prontuário vencido, sem consulta marcada | 31 · R$ 3.720 |
| **Exame pedido e não feito** | Solicitação de exame sem resultado anexado após N dias | 24 · R$ 2.880 |
| **Ritmo próprio quebrado** | Intervalo entre consultas do paciente excedeu significativamente a média dele | 69 · R$ 5.040 |

Cada linha mostra: nome do grupo · descrição em uma frase · quantidade · valor estimado · ação "enviar mensagem".

### Lista expandida do grupo

Ao abrir um grupo: tabela com seleção múltipla.

Colunas: Paciente (avatar de iniciais, nome, idade) · Última consulta (data + especialidade) · Contexto do grupo (varia: "4 de 10 sessões", "retorno vencido há 45 dias", "exame pedido em 12/06") · Médico · Valor estimado · Telefone com indicador de WhatsApp válido.

Ações em lote sobre a seleção:
- **Enviar mensagem**
- **Adiar 30 dias** — tira da lista temporariamente sem descartar
- **Marcar como não contatar** — permanente, com motivo (mudou de cidade, faleceu, pediu para não receber mensagens)

O "não contatar" é obrigatório: sem ele, a mesma pessoa reaparece toda semana e alguém acaba mandando mensagem para quem não deveria.

### Mensagem por grupo

**Cada grupo tem um texto diferente.** O motivo do contato muda o tom, e usar o mesmo texto para os quatro derruba a conversão e soa robótico:

- Tratamento interrompido → cuidado clínico ("você parou na 4ª sessão de 10, quer retomar?")
- Retorno pedido pelo médico → orientação do profissional ("a Dra. Ana pediu seu retorno")
- Exame não feito → lembrete prático
- Ritmo quebrado → convite leve, sem cobrança

Painel de pré-visualização ao lado da lista, com o texto editável antes do envio e link "Personalizar mensagem" que salva o modelo do grupo.

Toda mensagem enviada oferece horários e cai na agenda quando o paciente responde — mesmo fluxo do bot de agendamento já existente.

---

## Aba 2 — Réguas

Automação é o coração do módulo. A tela existe para configurar e acompanhar, não para alguém disparar campanha na mão todo dia.

Cada régua: gatilho + prazo + ação.

| Régua | Estado |
|---|---|
| Retorno vencido há **15 dias** → mensagem com horários | ativa |
| Exame sem resultado há **20 dias** → lembrete | ativa |
| Sessão perdida há **10 dias** → convite para remarcar | pausada |

Requisitos:
- O prazo em dias é editável inline
- Ativar/pausar por régua
- Botão "Adicionar régua"
- **Limite de frequência global**: nenhum paciente recebe mais de X mensagens automáticas por período (padrão: 1 a cada 30 dias), independentemente de quantas réguas ele acionar. Sem isso o sistema vira spam e a clínica queima o canal de WhatsApp.
- Janela de envio (ex.: só em dias úteis, das 9h às 18h)
- Pacientes marcados como "não contatar" são excluídos de todas as réguas

As réguas ficam visíveis na tela principal (versão resumida), não escondidas em Configurações — é o que ensina o usuário que existe automação.

---

## Aba 3 — Resultados

Três métricas do período, e a conversão é obrigatória — número de mensagens enviadas sozinho não diz nada:

- **Mensagens enviadas** — 204
- **Voltaram a agendar** — 37 · 18% de conversão
- **Receita gerada** — R$ 4.440 (consultas efetivamente realizadas, não agendadas)

Complementos:
- Conversão por grupo (revela qual mensagem funciona e qual precisa mudar)
- Evolução mensal
- Histórico de envios: data, grupo, quantidade, quem disparou (ou "automático")

---

## Estilo visual

Design system ClinicOS: tema escuro, elevação por luz, cantos de 12px, Space Grotesk nos números, accent verde para ação primária e receita gerada.

**Regra específica desta tela: nada de urgência.** Diferente das glosas, aqui não existe prazo legal correndo. Sem contagem regressiva, sem vermelho, sem alerta. O tom é oportunidade, não emergência — a tela deve dar vontade de trabalhar nela, não ansiedade.

---

## Restrições de conteúdo

- Nenhuma informação clínica além da especialidade e do progresso do tratamento ("4 de 10 sessões"). Sem diagnóstico, sem conteúdo de prontuário.
- Não repetir KPIs de outras telas (receita geral, ocupação, no-show).
- A estimativa de valor deve estar sempre identificada como estimativa.

---

## Estados

- Skeleton por bloco
- Grupo vazio: "Ninguém pendente neste grupo" — é boa notícia, não erro
- Tela toda vazia (clínica nova, sem histórico): estado explicativo dizendo que os grupos aparecem conforme o histórico de atendimentos se acumula

---

## Dados (mock)

142 pacientes pendentes, R$ 17.040 estimados. Grupos conforme a tabela acima. Resultado de julho: 204 mensagens, 37 retornos, 18% de conversão, R$ 4.440. Na lista expandida de tratamento interrompido, 6 pacientes com progresso variado (3 de 8, 4 de 10, 2 de 6), especialidades e médicos diferentes.
