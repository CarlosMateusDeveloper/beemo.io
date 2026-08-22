# ClinicOS — Roadmap e arquitetura

Versão 2. Consolida todas as decisões tomadas até aqui.

---

## 1. Público-alvo

**Clínica de consultas que solicita exames** — o paciente realiza fora e traz o resultado. É o caso mais comum e o mais simples de atender.

Clínica que **realiza** exames (imagem, laboratório) é outro segmento, com necessidades próprias (sala, equipamento, laudo, faturamento por procedimento). Fica fora do escopo por ora.

> Escolher o caso mais comum é bom para começar; tentar servir todos os casos é o que dilui produto. Manter essa escolha explícita.

**Ainda em aberto:** porte da clínica (3–8 médicos vs. 20+) e modelo de preço (fixo por clínica vs. por profissional).

---

## 2. Arquitetura de módulos

Três módulos, papéis separados por permissão, **dados compartilhados**:

**Módulo Operacional** (em construção) — recepção e administrativo
**Módulo Clínico** (depois) — médico e enfermagem, cada um com seu perfil
**Módulo Financeiro** (por último) — gestão de caixa, repasse, conciliação

Princípio que sustenta a divisão: **um dado, uma fonte; cada perfil vê a fatia dele.** Nunca duplicar informação entre módulos.

### Menu do módulo Operacional

```
Dashboard · Agenda · Pacientes · Retorno · Caixa · WhatsApp · Médicos · Convênios
```

Fora do menu: **Configurações** (página, acessível pelo perfil Dono).

### Decisões de estrutura já tomadas

| Decisão | Motivo |
|---|---|
| **Financeiro → Caixa** no operacional | Caixa é operação de balcão (baixa, recibo, fechamento de turno). Gestão financeira nasce depois como módulo próprio. |
| **Exames deixa de ser tela**, vira aba de Pacientes | Para clínica que só solicita, o volume é baixo e o trabalho é "anexar e avisar". Os alertas ("resultados sem aviso ao paciente") viram card no Dashboard. |
| **Convênios permanece**, com permissão de página | Clínica pequena libera para recepção; clínica grande restringe ao faturamento e o item some do menu. |
| **Retorno vira tela própria** | É a única fila proativa que não existe em nenhum outro lugar do sistema. |
| **Recuperação/Resgate foi descartado** | Vaga vazia resolve-se na Agenda, glosa em Convênios. O placar consolidado vira bloco do Dashboard, sem tela própria. |
| **Portal do paciente = WhatsApp** | Não haverá app nem portal web separado. |
| **Enfermagem é perfil, não módulo** | O trabalho dela acontece dentro do atendimento, não em tela separada. |

### Modelagem que precede a interface

**Exame deve ser modelado como esteira** mesmo aparecendo só como aba: solicitado → agendado → realizado → resultado recebido → paciente avisado → analisado pelo médico. Se um dia o produto atender clínica que realiza exames, a diferença é interface, não migração de dados.

---

## 3. O diferencial: reduzir perdas

**Posição a ocupar:** todos os concorrentes prometem organização e gestão. Nenhum promete devolver dinheiro.

Três fontes de perda, **cada uma no seu módulo de origem** (sem tela consolidada):

| Perda | Onde vive | Mecanismo |
|---|---|---|
| **Vaga vazia** | Agenda | Cancelamento dispara oferta da vaga pelo WhatsApp para a fila de espera; o primeiro que responder fica com o horário |
| **Paciente que não voltou** | `/retorno` | Réguas automáticas por motivo (tratamento interrompido, retorno vencido, exame não feito, ritmo quebrado) |
| **Glosa não recorrida** | Convênios | Fila ordenada por prazo, com dossiê montado automaticamente |

**O placar consolidado** — "R$ 8.400 resgatados este mês", dividido nas três origens — vira bloco do Dashboard. É o argumento comercial: na hora de cortar custos, o dono vê o sistema pagando a si mesmo.

**Sobre mostrar as perdas:** usar sempre período fechado (mês anterior), nunca contador em tempo real. Fato consumado é medição, não acusação — e quando a equipe melhora, o número cai, o que é recompensa visível pelo trabalho dela.

**Análise de sentimento foi descartada:** não há texto para analisar (o bot recebe "SIM" e "2"), quem sai da clínica sai calado, e é dado sensível. Sinais de comportamento observado (espera, atraso, cancelamentos, intervalo crescente) são mais preditivos e já estão no banco.

---

## 4. Ordem de construção

**Fase 0 — Fundação (antes de qualquer tela)**
- **Multiempresa (multi-tenant)** — cada clínica enxerga só os próprios dados. Se o banco não nascer preparado, refazer depois é reescrever tudo. **Prioridade máxima.**
- **Login, recuperação de senha e onboarding** — criar clínica, cadastrar médicos, horários, especialidades. Sem isso, nenhum cliente entra.
- **Perfis e permissões** — ver issue própria. Cinco perfis (Recepção, Faturamento, Médico, Gestor, Dono) + Enfermagem quando o módulo clínico existir.
- **Configurações** — usuários, horários, especialidades, tabelas de preço, modelos de mensagem.

**Fase 1 — Fechar o módulo Operacional**
Telas especificadas: `/medicos`, `/pacientes` (tabela ↔ kanban da fila do dia), `/convenios` + `/convenios/glosas/{id}`, `/retorno`. A construir: `/agenda`, `/caixa`, `/dashboard`, `/whatsapp`, aba de exames em Pacientes.

**Fase 2 — Lista de espera com preenchimento automático (Agenda)**
Primeira do diferencial: usa agenda, base de pacientes e WhatsApp, que já existem. Gera o primeiro número de recuperação.

**Fase 3 — Réguas automáticas de retorno**

**Fase 4 — Painel de glosas com prazo**

**Fase 5 — Bloco de resultado no Dashboard**
Consolida as três origens. É o argumento de venda inteiro.

**Fase 6 — Módulo Clínico**
Tela única de atendimento: histórico ao lado, evolução com modelos por especialidade, e três botões que fecham o ciclo — receita, pedido de exame, retorno. Perfil de enfermagem incluído.

**Fase 7 — Módulo Financeiro**

---

## 5. O ciclo que só existe num sistema único

O argumento do módulo clínico não é "temos módulo do médico também" — é **"o médico anota uma vez, e a clínica inteira já sabe"**:

| O médico registra | O sistema age sozinho |
|---|---|
| Retorno em 30 dias | Régua de `/retorno` dispara no prazo; WhatsApp oferece horários; cai na agenda |
| Pedido de exame | Esteira acompanha; se não for feito, entra no grupo correspondente em `/retorno` |
| Receita | Paciente recebe no celular, sem passar na recepção |

---

## 6. Table stakes (requisito, não diferencial)

Não ganham cliente — evitam perder venda:

- **Prescrição digital** (integração tipo MEMED) — sem isso o médico usa outro programa e a clínica paga duas assinaturas
- **Agendamento online pelo paciente** — via WhatsApp, que já existe
- **Assinatura digital de documentos**

---

## 7. Pontos de atenção

**Regulatório:** prontuário eletrônico é área regulada — regras do CFM sobre guarda e assinatura, LGPD, e a certificação SBIS, pedida por clínicas maiores. Entra no radar antes da Fase 6.

**Auditoria obrigatória:** acesso a prontuário, aceitar perda de glosa, estorno de pagamento, alteração de permissão e de repasse.

**Experiência dividida:** recepção faz muitas ações rápidas; médico faz uma ação longa e concentrada. Mesmo sistema, telas com lógicas diferentes.

**Limite de mensagens:** teto global por paciente (padrão 1 a cada 30 dias) acima de todas as réguas, mais a marcação "não contatar". Canal de WhatsApp queimado é dano difícil de reverter.

**Foco:** a maior ameaça não é o concorrente — é espalhar esforço. Uma fase por vez.

---

## 8. Design system

Tema escuro derivado do template Atomic e recalibrado para produto operacional. Tokens em `tokens.css`, documentação viva em página própria.

Princípios: elevação por luz (não sombra) · glow só em ação primária e foco · cor semântica apenas para status real, nunca decorativa · números tabulares · densidade alta.

**Pendente:** definir o front-end para gerar a biblioteca de componentes no stack certo.
