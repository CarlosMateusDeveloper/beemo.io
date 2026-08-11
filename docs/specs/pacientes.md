# Spec: Tela /pacientes (ClinicOS)

Crie a tela `/pacientes` de um sistema web de gestão para clínicas. Desktop (1440×900), português do Brasil, uso diário por recepção e gestão.

A tela acumula duas funções que devem conviver sem se misturar: **cadastro** (encontrar qualquer paciente entre milhares) e **fila de trabalho** (o que está acontecendo hoje). A ficha completa do paciente — prontuário, laudos, documentos — **não** vive aqui: ela é a tela `/pacientes/{id}`, descrita no fim desta spec.

## Estrutura geral

Três blocos empilhados:

1. **Barra de filtros e busca** (topo)
2. **4 cards de KPI** em uma linha, larguras iguais
3. **Área principal com alternador de visão**: Tabela ↔ Kanban

## 1. Barra de filtros

Título "Pacientes" à esquerda. À direita:

- Campo de busca com destaque (por nome, CPF ou telefone) — é a ação mais frequente da tela, deve ser o elemento mais fácil de alcançar
- Filtro de período (Hoje / 7 dias / Mês / Personalizado)
- Filtro de convênio (multi-seleção) — padrão: Todos
- Filtro de status do cadastro (Ativos / Todos)
- Botão primário "Novo paciente" (único botão primário da tela)

## 2. Os 4 KPIs

Cada card: ícone outline, label curto, número grande, linha de apoio com contexto ou comparação com o período anterior. Cada card cobre uma dimensão diferente — não repita informação entre eles.

**KPI 1 — Base ativa**
- Valor: pacientes com ao menos uma consulta nos últimos 12 meses (ex.: 1.284)
- Apoio: total cadastrado e o percentual que isso representa (ex.: "de 2.017 cadastros · 64%")
- Responde: qual o tamanho real da carteira?

**KPI 2 — Novos pacientes**
- Valor: primeiros atendimentos no período (ex.: 47)
- Apoio: variação vs. período anterior + canal de origem principal, quando houver (ex.: "+12% · 60% via WhatsApp")
- Responde: estamos captando?

**KPI 3 — Em risco de abandono**
- Valor: pacientes com histórico que não retornam há 6–12 meses (ex.: 89)
- Apoio: quanto isso representa da base ativa
- Cor de alerta (âmbar/vermelho conforme o volume)
- **O número é clicável**: leva à tabela já filtrada por esses pacientes, pronta para virar lista de recontato
- Responde: quem estamos perdendo?

**KPI 4 — Cadastros incompletos**
- Valor: pacientes sem documento, sem contato válido ou sem convênio vinculado (ex.: 34)
- Apoio: o motivo mais frequente (ex.: "23 sem documento")
- Também clicável, filtrando a tabela
- Responde: o que trava faturamento e gera retrabalho na recepção?

Não inclua KPI de receita, ocupação, no-show ou taxa de retorno — esses números já vivem no dashboard principal e na tela de médicos.

## 3. Alternador de visão

Toggle de dois estados no canto superior direito da área principal: **Tabela** (padrão) e **Kanban**. A escolha persiste por usuário.

Regra importante: a **tabela** mostra todos os pacientes do filtro atual (base inteira, paginada). O **kanban** mostra apenas pacientes com consulta no período selecionado — cinco mil cartões em colunas é inutilizável. Ao alternar para Kanban, se o período for maior que "7 dias", ajuste automaticamente para "Hoje" e avise discretamente ("mostrando apenas hoje").

### 3a. Visão Tabela

Tabela densa, cabeçalho fixo, ordenação clicável, paginação. Colunas:

| Coluna | Conteúdo |
|---|---|
| Paciente | Avatar com iniciais, nome, e abaixo em texto menor: idade + sexo |
| Contato | Telefone principal (com ícone indicando WhatsApp válido) |
| Convênio | Nome do convênio ou badge "Particular" |
| Última consulta | Data + especialidade, em texto menor |
| Próxima consulta | Data/hora, ou "—" quando não houver |
| Status | Badge: Ativo / Em risco / Inativo / Cadastro incompleto |

Comportamento:
- Linha inteira clicável → abre `/pacientes/{id}`
- Seleção múltipla com checkbox, habilitando ações em lote (exportar, enviar mensagem de recontato)
- Ordenação padrão: última consulta (mais recente primeiro)
- Estado vazio com mensagem útil e ação ("Nenhum paciente encontrado para essa busca")

### 3b. Visão Kanban — fila do dia

Cinco colunas, na ordem do fluxo real de atendimento:

1. **Agendado** — consulta marcada, ainda sem confirmação
2. **Confirmado** — paciente confirmou presença (pelo bot do WhatsApp ou por telefone)
3. **Recepção** — paciente fez check-in e está aguardando
4. **Em atendimento** — está com o médico
5. **Concluído** — atendimento encerrado

Cada coluna tem título, contador de cartões e rolagem própria quando necessário.

**Cartão do paciente** (compacto, 3 linhas no máximo):
- Nome do paciente
- Horário + especialidade/médico
- Sinalização contextual quando houver: tempo de espera em âmbar acima de 15 min e vermelho acima de 30 min (coluna Recepção); badge de pendência na coluna Concluído (ex.: "pend. laudo", "pend. pagamento")

**Interações:**
- Arrastar e soltar entre colunas **registra o evento real** — mover para "Recepção" faz o check-in, mover para "Concluído" encerra o atendimento. Não é um quadro decorativo.
- Movimentos só avançam ou retrocedem uma etapa por vez; bloqueie saltos (de "Agendado" direto para "Concluído")
- Clique no cartão abre a ficha do paciente
- A coluna "Recepção" ordena por tempo de espera (maior primeiro), as demais por horário da consulta

## 4. Ficha do paciente — `/pacientes/{id}`

Tela separada, aberta ao clicar em qualquer linha ou cartão. Cabeçalho com dados de identificação (nome, idade, convênio, contato, status) e abas:

- **Resumo** — próxima consulta, últimas visitas, alertas
- **Prontuário** — evoluções clínicas
- **Documentos** — RG, CPF, carteirinha do convênio, termos assinados
- **Exames e laudos** — arquivos anexados, com data e profissional solicitante
- **Histórico de consultas** — linha do tempo de atendimentos
- **Financeiro** — pagamentos, pendências, guias de convênio

**Requisitos de segurança e privacidade (obrigatórios):**
- Prontuário, exames e laudos são dado clínico sensível: visíveis apenas para perfis com permissão de atendimento. Recepção não acessa essas abas — elas nem aparecem para esse perfil.
- Todo acesso a prontuário e laudos gera registro de auditoria: quem abriu, o quê, quando.
- A tela de listagem (tabela e kanban) **nunca** exibe conteúdo clínico — nada de motivo da consulta, diagnóstico ou observação médica no cartão ou na linha.

## Estilo visual

Siga o design system ClinicOS: tema escuro, canvas quase preto, superfícies elevadas por luz (não por sombra), cantos de 12px em cartões, tipografia display para números de KPI, accent verde reservado a ação primária e dado principal. Cores semânticas (verde/âmbar/vermelho) usadas exclusivamente para status real, nunca decorativas. Ícones outline. Números em fonte tabular.

## Estados e comportamento

- Skeleton por bloco durante o carregamento — nunca spinner de tela cheia
- Estado vazio específico por visão (tabela sem resultados ≠ kanban sem consultas hoje)
- Formatação pt-BR: datas dd/mm, telefone formatado, CPF mascarado conforme permissão do perfil
- Atualização automática da visão kanban (polling ou websocket), já que ela reflete o que está acontecendo agora
- Responsivo é secundário: KPIs empilham em 2×2 e o kanban ganha rolagem horizontal

## Dados (mock)

Clínica de médio porte: ~2.000 cadastros, 1.284 na base ativa, 47 novos no mês, 89 em risco, 34 incompletos. Para o kanban, popule um dia realista: 18 agendados, 12 confirmados, 3 na recepção (um deles esperando 22 min, outro 34 min), 2 em atendimento, 9 concluídos (um com pendência de laudo). Misture convênios e particulares, e inclua ao menos um cadastro incompleto visível na tabela.
