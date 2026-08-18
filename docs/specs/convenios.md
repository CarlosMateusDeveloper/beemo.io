# Spec — Convênios e gestão de glosas (ClinicOS)

Conjunto de três telas que transformam o módulo Convênios de cadastro em cobrança: a lista de glosas com prazo, a página de uma glosa individual e a página de um convênio.

**Premissa do módulo:** glosa é quando o convênio se recusa a pagar um atendimento já realizado — historicamente 3% a 5% do faturamento bruto. O problema principal não é técnico, é **prazo**: o recurso precisa ser enviado em 30 a 60 dias, e o que fica "para depois" quase sempre vira nunca. Toda decisão de design abaixo existe para que o recurso seja feito antes de vencer.

---

## Rotas

| Rota | Tela |
|---|---|
| `/convenios` | Painel do módulo: KPIs + fila de glosas (padrão), abas Convênios e Lotes |
| `/convenios/glosas/{id}` | Página de uma glosa individual |
| `/convenios/{id}` | Página de um convênio (desempenho, contrato, tabela) |

---

## Tela 1 — `/convenios` (painel do módulo)

### Barra de filtros

Título "Convênios" à esquerda. À direita: período (padrão: Mês), convênio (multi-seleção), status da glosa, e responsável.

### Os 4 KPIs — dinheiro em quatro estados

Cada card: ícone outline, label, número grande, linha de apoio.

**KPI 1 — A receber**
Total faturado e enviado ao convênio, ainda não pago. Apoio: número de lotes em aberto e prazo médio de recebimento (ex.: "12 lotes · média 47 dias").

**KPI 2 — Glosado no período**
Valor negado pelos convênios. Apoio: percentual sobre o faturado (ex.: "4,2% do faturado"). Cor de alerta acima de 5%.

**KPI 3 — Prazo vencendo**
Valor em glosas cujo prazo de recurso vence nos próximos 7 dias. Apoio: quantos recursos e em quantos dias (ex.: "3 recursos vencem em 5 dias"). **Sempre em vermelho quando maior que zero** — é o card mais importante da tela. Clicável: filtra a fila por prazo crítico.

**KPI 4 — Recuperado**
Valor revertido no período. Apoio: taxa de reversão dos recursos enviados (ex.: "61% dos recursos revertidos"). Em verde. É o número que prova o valor do sistema.

### Abas da área principal

**Aba 1 — Glosas (padrão)**

Tabela densa, **ordenada por prazo crescente** (quem vence primeiro aparece primeiro). Essa ordenação é o ponto central da tela: não é uma tabela para consultar, é uma fila para zerar.

Colunas: Paciente / procedimento (com data do atendimento em texto menor) · Convênio · Motivo da glosa · Valor · **Prazo** (contagem regressiva) · Status · Responsável.

Regras:
- Prazo em vermelho abaixo de 5 dias, âmbar abaixo de 15, neutro acima
- Status como badge: A analisar / Em recurso / Recorrida / Revertida / Perdida
- Linha inteira clicável → `/convenios/glosas/{id}`
- Seleção múltipla habilitando **recurso em lote**: várias glosas do mesmo convênio e mesmo motivo viram um recurso só (é assim que o faturamento trabalha na prática)
- Atribuir responsável em lote

**Aba 2 — Convênios**

Uma linha por convênio, transformando cadastro em comparação: Convênio · Atendimentos no período · Faturado · Recebido · **% de glosa** · Prazo médio real de pagamento (com o contratado ao lado) · Taxa de reversão dos recursos.

Objetivo: virar arma de negociação. "A Unimed glosa 8% e paga em 62 dias; o Bradesco glosa 3% e paga em 45" é a informação que hoje não existe e que embasa renegociar contrato ou deixar de aceitar um convênio.

Alerta obrigatório na linha: **tabela de preços desatualizada** ou reajuste vencido — a clínica continua cobrando valor antigo e perde dinheiro em silêncio.

Linha clicável → `/convenios/{id}`.

**Aba 3 — Lotes**

Remessas enviadas e seu status: enviado / processado / pago / pago parcialmente. Inclui **conciliação**: comparação entre valor enviado e valor pago por lote, para pegar pagamento a menor que não veio acompanhado de glosa formal.

---

## Tela 2 — `/convenios/glosas/{id}` (a glosa individual)

Página cheia, em duas colunas. Foi escolhida em vez de painel lateral porque permite link direto (enviável para o responsável, abrível pelo alerta de prazo), abrir várias em abas e ter espaço para o editor do recurso conviver com o dossiê.

### Navegação de fila (obrigatória)

A página herda a lógica de fila da lista:

- Link **"Voltar para a fila"** que **preserva filtro, ordenação e posição de rolagem**. Se o usuário volta e cai no topo da lista sem filtro, ele desiste na terceira glosa.
- Contador e navegação no topo: "glosa 3 de 14 · ← anterior · próxima →"
- **Depois de agir** (enviar recurso, aceitar perda, corrigir), o sistema abre automaticamente a próxima glosa da fila, em vez de voltar para a lista. É o que transforma 14 pendências numa sessão de 20 minutos.

### Cabeçalho fixo ao rolar

Nome do paciente + procedimento · convênio, data do atendimento e lote em texto menor · à direita, valor da glosa em destaque e **prazo restante em vermelho**. Fica fixo porque é o que mantém o senso de urgência enquanto a pessoa escreve o recurso.

### Coluna principal (≈60%) — a decisão

**Bloco 1 — O que o convênio disse**
Motivo da negativa em texto claro, com código, data da negativa e categoria (administrativa / técnica / linear).

**Bloco 2 — Recomendação**
Faixa em destaque com a taxa histórica de reversão para aquele motivo naquele convênio (ex.: "71% das glosas por esse motivo na Unimed foram revertidas"). É só estatística do próprio histórico do sistema — sem previsão nem modelo. Serve para priorizar esforço: nem toda glosa compensa recorrer.

**Bloco 3 — Texto do recurso**
Editor **já preenchido por modelo**, escolhido conforme o motivo da glosa, com os dados do caso interpolados. A pessoa revisa e envia, em vez de escrever do zero. Modelos editáveis em configuração.

**Bloco 4 — Ações (três decisões explícitas)**
- **Enviar recurso** — contesta a negativa
- **Corrigir e reenviar** — quando o erro foi da clínica e cabe refazer a guia
- **Aceitar perda** — um clique fácil, mas **registrado**: quem, quando e por quê. Alimenta a prevenção e cria rastro de auditoria para uma decisão que joga dinheiro fora.

### Coluna lateral (≈40%) — o contexto

**Dossiê** — documentos anexados **automaticamente** pelo sistema: guia enviada, autorização (com o número, quando existir), evolução do prontuário do atendimento, laudos. Este é o coração do módulo: hoje montar um recurso significa caçar documento em três lugares; aqui o dossiê se monta sozinho porque o ClinicOS já tem tudo. Cada item abre em visualização e entra como anexo do recurso.

Quando um documento contradiz o motivo da glosa (ex.: negada por falta de autorização, mas a autorização existe), destacar isso visualmente — é o caso de reversão mais fácil e o momento em que o usuário percebe o valor do sistema.

**Histórico** — linha do tempo da glosa: guia enviada no lote, glosa registrada, atribuída a alguém, recurso enviado, desfecho.

**Responsável** — quem cuida desta glosa, editável.

---

## Tela 3 — `/convenios/{id}` (o convênio)

Cabeçalho com nome do convênio, tipo de contrato e status. Conteúdo:

- Indicadores do convênio no período: atendimentos, faturado, recebido, % de glosa, prazo médio real × contratado, taxa de reversão
- Evolução mensal do % de glosa (gráfico de linha)
- Motivos de glosa mais frequentes deste convênio (ranking) — direciona a prevenção
- Tabela de preços vigente, com data do último reajuste e alerta se vencido
- Dados de contrato: prazo de pagamento acordado, prazo de recurso, contatos
- Lista das glosas abertas deste convênio

---

## Alertas e automação

- Notificação automática ao responsável quando o prazo de um recurso chegar a 3 dias. Usa o canal de WhatsApp que o sistema já tem, em vez de depender de alguém abrir a tela.
- Alerta de tabela de preços desatualizada.
- **Verificação pré-envio (fase posterior):** a glosa quase sempre nasce antes do faturamento — cadastro incompleto, autorização faltando, guia mal preenchida. Um aviso antes de enviar o lote ("3 guias com pendência") fecha o ciclo com o KPI "cadastros incompletos" da tela de Pacientes. Prevenir vale mais que recorrer, mas recorrer dá resultado visível no primeiro mês — por isso a fila de glosas vem primeiro.

---

## Estilo visual

Design system ClinicOS: tema escuro, canvas quase preto, elevação por luz, cantos de 12px em cartões, Space Grotesk nos números, accent verde restrito a ação primária e dado principal. Cores semânticas exclusivamente para status real — nesta tela, o vermelho pertence ao prazo e ao valor em risco, e nada mais deve competir com ele. Números tabulares, formatação pt-BR.

## Estados

- Skeleton por bloco; nunca spinner de tela cheia
- Fila vazia é uma boa notícia, não um erro: "Nenhuma glosa pendente. Tudo em dia."
- Estado de glosa já resolvida: página em modo leitura, com o desfecho e a data em destaque

## Dados (mock)

A receber R$ 84.300 em 12 lotes (média 47 dias); glosado no mês R$ 19.700 (4,2%); prazo vencendo R$ 12.400 em 3 recursos; recuperado R$ 8.100 (61% de reversão). Fila com 14 glosas, valores entre R$ 120 e R$ 1.800, motivos variados (sem autorização, código TUSS incorreto, fora da carência, documentação insuficiente), prazos de 2 a 40 dias, e pelo menos uma de cada status. Convênios: Unimed, Bradesco Saúde, SulAmérica e Amil, com percentuais de glosa e prazos de pagamento distintos entre si.
