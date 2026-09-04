# Spec — Convênios e gestão de glosas (ClinicOS)

Módulo `/convenios`: centro de controle operacional e financeiro dos atendimentos por convênio. Transforma o módulo de cadastro em cobrança ativa — fila de glosas com prazo, prevenção via auditoria automática, faturamento em lote e recuperação de glosas.

**Premissa do módulo:** glosa é quando o convênio se recusa a pagar um atendimento já realizado — historicamente 3% a 5% do faturamento bruto. O problema principal não é técnico, é **prazo**: o recurso precisa ser enviado em 30 a 60 dias, e o que fica "para depois" quase sempre vira nunca. Toda decisão de design existe para que o recurso seja feito antes de vencer — e, quando possível, para que a glosa nunca aconteça.

**Fluxo central:** Auditar → Corrigir → Faturar → Recuperar → Medir

**Princípio de funcionamento:** automatizar → tentar resolver → solicitar intervenção humana somente quando necessário. O sistema não deve apenas informar que existe um problema — sempre que possível deve identificar o problema, localizar os dados necessários, tentar resolver automaticamente, preparar a ação necessária, e só então solicitar aprovação humana para a decisão que restar.

O objetivo é transformar o trabalho humano de "conferir, cadastrar, procurar, montar, acompanhar e cobrar" em "revisar exceções e tomar decisões". O ClinicOS executa o fluxo normal; o humano intervém quando o sistema não consegue resolver sozinho.

---

## Rotas

| Rota | Tela |
|---|---|
| `/convenios` | Painel do módulo: KPIs + abas Glosas (padrão) / Auditoria / Convênios / Lotes |
| `/convenios/glosas/{id}` | Página de uma glosa individual |
| `/convenios/{id}` | Página de um convênio (desempenho, contrato, tabela) |

---

## Tela 1 — `/convenios` (painel do módulo)

### Barra de filtros

Título "Convênios" à esquerda. À direita: **Período**, **Convênio**, **Status**, **Responsável**.

Período padrão: **últimos 30 dias**. Opções: Hoje, Últimos 7 dias, Últimos 30 dias, Últimos 90 dias, Personalizado. Os filtros atualizam KPIs e dados de todas as abas.

### Os 4 KPIs — dinheiro em quatro estados

Cada card: ícone outline, label, número grande, linha de apoio.

**KPI 1 — A receber**
Total faturado e enviado ao convênio, ainda não pago. Apoio: número de lotes em aberto e prazo médio de recebimento (ex.: "12 lotes · média 47 dias").

**KPI 2 — Em risco**
Valor associado a atendimentos com pendências capazes de gerar glosa (saída da Auditoria). Apoio: número de atendimentos pendentes de auditoria. É o principal indicador de **prevenção** — antecede o KPI de glosado.

**KPI 3 — Glosado**
Valor efetivamente recusado pelos convênios no período filtrado. Apoio: número de glosas no período e percentual sobre o faturado (cor de alerta acima de 5%). Não usar "no mês" no rótulo — o período já vem do filtro.

**KPI 4 — Recuperado**
Valor revertido por recursos de glosa no período. Apoio: número de recursos revertidos e taxa de reversão (ex.: "61% dos recursos revertidos"). Em verde — é o número que prova o valor do sistema.

Adicional (fila): valor em glosas cujo prazo de recurso vence nos próximos 7 dias deve aparecer com destaque vermelho na aba Glosas (ver abaixo), mesmo não sendo um dos 4 cards fixos.

### Abas da área principal

#### Aba 1 — Glosas (padrão)

Gerencia as glosas que a Auditoria não conseguiu evitar.

Fluxo: `Glosa recebida → Análise → Classificação → Verificar recorribilidade → Recurso → Acompanhamento → Recuperada / Negada`

Tabela densa, **ordenada por prazo crescente** (quem vence primeiro aparece primeiro). Essa ordenação é o ponto central da tela: não é uma tabela para consultar, é uma fila para zerar.

Colunas: Paciente / procedimento (com data do atendimento em texto menor) · Convênio · Motivo da glosa · Valor · **Prazo** (contagem regressiva) · Status · Responsável.

Regras:
- Prazo em vermelho abaixo de 5 dias, âmbar abaixo de 15, neutro acima
- Status como badge: A analisar / Em recurso / Recorrida / Revertida / Perdida
- Linha inteira clicável → `/convenios/glosas/{id}`
- Seleção múltipla habilitando **recurso em lote**: várias glosas do mesmo convênio e mesmo motivo viram um recurso só (é assim que o faturamento trabalha na prática)
- Atribuir responsável em lote

Dados de cada glosa: ID, lote, atendimento, paciente, convênio, plano, procedimento, valor faturado, valor glosado, data, código do motivo, descrição, prazo, status, responsável.

**Análise automática ao receber a glosa** — o sistema tenta identificar sozinho: motivo, procedimento e atendimento relacionado, documentos existentes, autorização, evidências, e se há possibilidade de recurso. Deve responder na tela: *Por que ocorreu? É possível recorrer? Quais evidências temos? O que está faltando? Qual é o prazo?*

**Recuperação (quando recorrível)** — botão "Criar recurso" monta automaticamente: dados do atendimento, motivo da glosa, documentos disponíveis, autorização, evidências e uma justificativa sugerida (ver Bloco 3 na Tela 2). O usuário só revisa e aprova; o recurso **nunca é enviado automaticamente**.

**Controle de prazo** — cada recurso tem prazo, data limite, dias restantes e responsável, classificados como:
- 🟢 mais de 7 dias
- 🟡 3–7 dias
- 🔴 menos de 3 dias
- ⚫ expirado

Alertas automáticos quando o prazo se aproxima (ex.: "Recurso vence em 2 dias — Glosa #1829 — R$ 450"), via WhatsApp (canal que o sistema já tem).

Status de acompanhamento do recurso: Em preparação / Enviado / Aguardando retorno / Em análise pelo convênio / Recuperado / Recuperado parcialmente / Negado / Prazo expirado. Registrar: data de envio, protocolo, canal, documentos, responsável, respostas do convênio.

Resultado final registrado: valor originalmente glosado, valor recuperado, valor perdido, resultado, motivo da negativa (se houver), data da resposta, protocolo, documento de resposta.

#### Aba 2 — Auditoria

**Objetivo: impedir que a glosa aconteça.** Quando um atendimento é finalizado, o sistema executa automaticamente uma auditoria usando as regras cadastradas para o convênio/plano do paciente.

Fluxo:
```
Atendimento finalizado → identificar convênio/plano → carregar regras → executar auditoria
  → sem problemas → Aprovado → segue para Lote
  → problemas      → Em risco → Corrigir
```

Verificações conforme regras do convênio: elegibilidade do paciente, cobertura do procedimento, necessidade e existência de autorização, código do procedimento, quantidade permitida, profissional habilitado, documentos obrigatórios, assinatura, informações do atendimento, prazo de faturamento, inconsistências entre prontuário e faturamento.

**A auditoria não deve apenas detectar — deve tentar resolver.** Exemplo: se falta autorização, o sistema procura no cadastro do paciente, no prontuário e em documentos relacionados antes de marcar como pendência humana. Se encontrar, resolve sozinho; se não encontrar, gera a ação "adicionar autorização".

Cada atendimento auditado recebe um status:
- **Aprovado** — nenhum problema identificado, segue para lote
- **Atenção** (🟡) — inconsistência que precisa de revisão, mas não bloqueia
- **Bloqueado** (🔴) — condição que impede o faturamento seguro

Resumo da aba (ex.: "300 atendimentos analisados — 🟢 281 aprovados · 🟡 12 em atenção · 🔴 7 bloqueados — R$ 2.840 em risco").

**Detalhe de um atendimento auditado** (`Auditoria — Atendimento #1829`): lista de checagens com ✓/✕ (paciente elegível, procedimento coberto, autorização, código do procedimento, documento obrigatório etc.), valor em risco, botão "Corrigir pendência". Cada pendência tem: descrição, severidade (Crítica / Alta / Média / Baixa), regra responsável, evidência encontrada, ação recomendada.

#### Aba 3 — Convênios

Uma linha por convênio, transformando cadastro em comparação: Convênio · Atendimentos no período · Faturado · Recebido · **% de glosa** · Prazo médio real de pagamento (com o contratado ao lado) · Taxa de reversão dos recursos.

Objetivo: virar arma de negociação. "A Unimed glosa 8% e paga em 62 dias; o Bradesco glosa 3% e paga em 45" é a informação que hoje não existe e que embasa renegociar contrato ou deixar de aceitar um convênio.

Alerta obrigatório na linha: **tabela de preços desatualizada** ou reajuste vencido — a clínica continua cobrando valor antigo e perde dinheiro em silêncio.

Linha clicável → `/convenios/{id}`.

**Cadastro do convênio (fonte de dados da Auditoria)** — cada convênio armazena:
- Dados gerais: nome, identificação, status, contato, observações
- Planos: nome, código, status
- Procedimentos: código, descrição, valor, cobertura, necessidade de autorização
- Regras: autorizações, documentos, prazos, limites, restrições, faturamento
- Documentos obrigatórios por procedimento

#### Aba 4 — Lotes

**Objetivo: automatizar o faturamento.** O usuário não deve precisar selecionar atendimento por atendimento.

Fluxo: `Atendimentos aprovados → ClinicOS agrupa → sugere/cria lote → revisão humana → envio → processamento → pagamento / glosa`

Exemplo de sugestão: "Novo lote recomendado — Unimed — 281 atendimentos — R$ 42.150 — [Revisar lote]".

Status do lote: Rascunho / Pronto para envio / Enviado / Processando / Pago parcialmente / Pago / Com glosas.

Inclui **conciliação**: comparação entre valor enviado e valor pago por lote, para pegar pagamento a menor que não veio acompanhado de glosa formal.

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

Geração de justificativa por IA (quando disponível): usa apenas informações existentes no caso, indica quando falta evidência, não inventa informações, e permanece editável pelo usuário. O recurso nunca é enviado automaticamente sem aprovação humana.

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

## Inteligência de glosas (indicadores)

Sem aprendizado automático nesta etapa — apenas leitura estatística dos dados já existentes, disponível na aba Glosas e reforçada na Tela 3 por convênio.

**Motivos das glosas** (percentual do total, ex.: ausência de autorização 42% · documentação incompleta 28% · código incorreto 16% · elegibilidade 9% · outros 5%)

**Glosas por convênio** — quantidade, valor, percentual.

**Glosas por procedimento** — quantidade, valor, percentual.

**Indicadores financeiros** — valor glosado, valor recuperável, valor recuperado, valor perdido, taxa de recuperação, recursos pendentes.

Esses indicadores devem permitir que a clínica responda: onde estou perdendo dinheiro? Por que estou perdendo dinheiro? Quanto consigo recuperar? Quanto já recuperei? Qual convênio/procedimento gera mais problemas? — e, no médio prazo, orientar a configuração das regras da Auditoria.

---

## Alertas e automação

- Notificação automática ao responsável quando o prazo de um recurso chegar a 3 dias. Usa o canal de WhatsApp que o sistema já tem, em vez de depender de alguém abrir a tela.
- Alerta de tabela de preços desatualizada.
- **Verificação pré-envio (fase posterior):** a glosa quase sempre nasce antes do faturamento — cadastro incompleto, autorização faltando, guia mal preenchida. Um aviso antes de enviar o lote ("3 guias com pendência") fecha o ciclo com o KPI "cadastros incompletos" da tela de Pacientes. Prevenir vale mais que recorrer, mas recorrer dá resultado visível no primeiro mês — por isso a fila de glosas foi a primeira aba a ser construída.

---

## Fluxo completo do módulo

```
                    ATENDIMENTO
                         ↓
                     AUDITORIA
                         ↓
                ┌────────┴────────┐
                ↓                 ↓
            APROVADO            RISCO
                ↓                 ↓
              LOTE             CORREÇÃO
                ↓                 ↓
             FATURAMENTO ←────────┘
                ↓
          ┌─────┴─────┐
          ↓           ↓
        PAGO        GLOSA
                      ↓
                    ANÁLISE
                      ↓
                RECORRÍVEL?
                 ↙       ↘
               NÃO       SIM
                ↓         ↓
             PERDIDA    RECURSO
                          ↓
                       ENVIO
                          ↓
                       RETORNO
                          ↓
                  ┌───────┴───────┐
                  ↓               ↓
             RECUPERADA         NEGADA
                  ↓               ↓
                  └───────┬───────┘
                          ↓
                    INDICADORES
```

---

## Estilo visual

Design system ClinicOS: tema escuro, canvas quase preto, elevação por luz, cantos de 12px em cartões, Space Grotesk nos números, accent verde restrito a ação primária e dado principal. Cores semânticas exclusivamente para status real — nesta tela, o vermelho pertence ao prazo e ao valor em risco, e nada mais deve competir com ele. Números tabulares, formatação pt-BR.

## Estados

- Skeleton por bloco; nunca spinner de tela cheia
- Fila vazia é uma boa notícia, não um erro: "Nenhuma glosa pendente. Tudo em dia."
- Estado de glosa já resolvida: página em modo leitura, com o desfecho e a data em destaque

## Dados (mock)

A receber R$ 84.300 em 12 lotes (média 47 dias); em risco R$ 2.840 (7 atendimentos bloqueados na Auditoria); glosado R$ 19.700 no período (4,2%); recuperado R$ 8.100 (61% de reversão). Fila com 14 glosas, valores entre R$ 120 e R$ 1.800, motivos variados (sem autorização, código TUSS incorreto, fora da carência, documentação insuficiente), prazos de 2 a 40 dias, e pelo menos uma de cada status. Auditoria com 300 atendimentos analisados (281 aprovados, 12 em atenção, 7 bloqueados). Convênios: Unimed, Bradesco Saúde, SulAmérica e Amil, com percentuais de glosa e prazos de pagamento distintos entre si.

## Status de implementação

- ✅ Cadastro de convênio, planos, procedimentos, documentos obrigatórios e autorizações (models `Convenio`, `ConvenioPlano`, `ConvenioProcedimento`, `DocumentoObrigatorioConvenio`, `AutorizacaoConvenio`)
- ✅ Aba Convênios (config) e KPIs do painel (`ConveniosKpiService`, `ConvenioConfigService`, `ConvenioListagemService`) — `emRisco`/`glosado`/`recuperado` dos 4 KPIs do topo continuam zerados de propósito (`ConveniosKpiService` não agrega `auditoria_atendimento`/`glosa` ainda); os indicadores reais de glosa aparecem na tira dentro da aba Glosas (`GlosaIndicadoresService`), não nesses 4 cards
- ✅ Backend de recuperação de glosas (fluxo de recurso completo) — commit `4365019`
- ✅ **Frontend da aba Glosas** — fila com filtro por status/responsável/convênio (persistido na URL), atribuição de responsável em lote, página `/convenios/glosas/{id}` com navegação de fila (contador, anterior/próxima, auto-avanço após ação, volta preservando filtro e rolagem), fluxo completo do recurso (classificar → criar → editar/anexar documento → checklist → enviar → registrar resultado), aceitar perda. `/convenios/{id}` também virou rota real. Verificado ponta a ponta num navegador real contra o backend.
- ⬜ Aba Auditoria (motor de regras, execução automática pós-atendimento, status Aprovado/Atenção/Bloqueado) — não iniciada
- ⬜ Aba Lotes (agrupamento automático, conciliação) — não iniciada
- ⬜ Geração de justificativa por IA no editor de recurso — não iniciada
- ⬜ Recurso em lote (várias glosas → 1 recurso só) — `recurso_glosa` é 1:1 com `glosa` no schema atual, precisa mudar o modelo
- ⬜ Bloco "Recomendação" (taxa histórica de reversão por motivo+convênio) na página da glosa — precisa de agregação nova no backend

**Nota de ambiente:** a migração `006_recuperacao_glosas.sql` não tinha sido aplicada neste banco de dev até esta sessão — `recurso_glosa` existia com colunas erradas (criado por `ddl-auto` do Hibernate antes da migração rodar, sem `DEFAULT FALSE` em `evidencias_conferidas`). Corrigido com `ALTER TABLE recurso_glosa ALTER COLUMN evidencias_conferidas SET DEFAULT FALSE` além de rodar a migração. Vale conferir se outros ambientes (produção, outros devs) têm o mesmo desalinhamento antes de assumir que a migração já rodou em todo lugar.
