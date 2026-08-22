# Spec — Tela /whatsapp (ClinicOS)

## Premissa

O assistente de WhatsApp é o **coração do produto**, não um módulo acessório.

A tese comercial do ClinicOS é que a clínica opera com menos gente no balcão — e não porque o trabalho total diminui, mas porque o **pico** desaparece. A clínica tem quatro pessoas na recepção por causa de duas horas do dia (manhã e pós-almoço). Se metade de quem ligaria às 8h resolve tudo pelo WhatsApp na véspera, a clínica passa a operar com o time da média.

Esta tela é onde esse assistente é operado, configurado e medido.

**Princípio de design que atravessa tudo:** o usuário configura **textos e regras**, nunca a lógica da conversa. Não existe construtor de fluxo visual — a recepcionista não quer desenhar diálogo, e um cliente que edite a lógica quebra o bot e gera suporte. A lógica de agendar, remarcar e confirmar é do produto.

---

## Rota e permissão

`/whatsapp` — módulo Operacional.

| Aba | Recepção | Gestor | Dono |
|---|:---:|:---:|:---:|
| Conversas | ✓ | ✓ | ✓ |
| Assistente | — | ✓ | ✓ |
| Desempenho | — | ✓ | ✓ |

A recepção atende conversas; configurar o assistente é decisão de gestão.

---

## Aba 1 — Conversas (padrão)

**A função principal não é ler chat — é atender quem o bot não resolveu.** O desenho deve deixar isso óbvio: a fila de "aguardando atendente" é o que abre.

### Filtros (topo)

- **Aguardando atendente** (padrão) — com contador
- Bot resolveu
- Todas
- Busca por nome ou telefone

### Lista de conversas (coluna esquerda)

Cada item: nome do paciente (ou telefone, se desconhecido), última mensagem truncada, horário, e badge de estado — "aguardando", "com Camila", "bot". Ordenação: aguardando primeiro, mais antigo no topo (quem espera há mais tempo é atendido antes).

### Conversa aberta (centro)

Histórico completo, com as mensagens do bot visualmente distintas das do atendente humano — a recepcionista precisa saber o que o bot já disse antes de responder.

Ações: **Assumir conversa** (bot para de responder), **Devolver ao assistente**, e campo de resposta.

### Painel de contexto (direita)

Esta é a diferença entre a tela e um WhatsApp Web comum. Mostra:

- Identificação do paciente com link para `/pacientes/{id}`
- Próxima consulta agendada
- Últimas consultas
- Pendências (pagamento em aberto, exame sem resultado)
- **O que o bot estava fazendo**: em que ponto do fluxo a conversa parou e o que já foi perguntado

Sem esse painel, a atendente precisa abrir outra tela para responder qualquer coisa — e aí a economia de tempo evapora.

---

## Aba 2 — Assistente

### Coluna esquerda — capacidades

Lista das capacidades, cada uma com liga/desliga. Selecionar uma abre seus textos abaixo.

Ordem por valor (também a ordem de construção recomendada):

1. **Confirmar presença** — régua na véspera; só recebe resposta, não precisa interpretar nada
2. **Consultar meu horário** — trivial de implementar e é o que mais lota o WhatsApp
3. **Marcar consulta** — fecha o agendamento no sistema, não manda link externo
4. **Remarcar e cancelar** — onde nasce a vaga vazia que alimenta a fila de espera
5. **Oferecer vaga aberta** — dispara para a fila de espera quando alguém cancela; é a única que **gera receita**, não só economiza tempo
6. **Cadastro do paciente novo** — o paciente preenche, ninguém digita no balcão
7. **Avisar resultado de exame**

### Coluna esquerda — editor de textos

Para a capacidade selecionada, os textos de cada etapa (saudação, opções, confirmação, erro).

- Variáveis como **etiquetas clicáveis** que inserem no cursor: `{nome}`, `{clinica}`, `{data}`, `{hora}`, `{medico}`, `{especialidade}`. Digitar à mão gera erro de maiúscula e o bot manda a chave crua ao paciente.
- Salvamento automático
- **Histórico de versões com "restaurar"** — alguém muda a mensagem de confirmação, a taxa de resposta cai, e ninguém lembra o texto anterior

### Coluna esquerda — regras

**Escalonamento para humano** (o bloco que mais gera arrependimento se ficar errado — fica visível aqui, não escondido em Configurações):

- Paciente pediu atendente → sempre
- Bot não entendeu N vezes seguidas (padrão: 2)
- Palavras-chave que tiram o bot da frente imediatamente: **urgente, dor, emergência**, e a lista é editável

**Horário e expectativa:**
- Horário de atendimento humano
- Mensagem do bot fora desse horário, deixando explícito que o assistente responde 24h mas encaixes e exceções só no horário comercial. Sem isso o paciente acha que foi ignorado.

**Réguas de disparo automático:**
- Confirmação de presença: quantas horas antes
- Aviso de resultado de exame
- Sujeitas ao **teto global de mensagens por paciente** (padrão: 1 a cada 30 dias) e à marcação "não contatar", compartilhados com as réguas de `/retorno`

### Coluna direita — prévia

Simulação da conversa **atualizando em tempo real** enquanto se edita, com dados de exemplo. É o que dá segurança para mexer nos textos: sem prévia, a pessoa muda algo e só descobre o resultado quando um paciente reclama.

---

## Aba 3 — Desempenho

É a aba que sustenta o argumento de venda e a conversa de renovação.

**Métricas do período:**

- **Conversas resolvidas sem humano** — o número principal, com percentual do total
- Conversas que foram para atendente, e por qual motivo (não entendeu, pediu humano, palavra-chave)
- Tempo médio até a primeira resposta
- **Volume por horário do dia** — o gráfico mais importante: é onde o dono vê o pico da manhã achatado
- Ações concluídas pelo bot: agendamentos, remarcações, cancelamentos, confirmações
- Evolução mensal

**Instrumentar desde a primeira versão.** O contador só existe se contar desde o começo, e é ele que transforma "o bot ajuda" em "o assistente resolveu 612 conversas este mês".

---

## Decisões de arquitetura da conversa

**Menu numerado como base, com atalhos por texto livre.** Menu é previsível, barato e o paciente idoso consegue usar. Se a pessoa escrever "quero remarcar", o bot entende e pula direto para o fluxo — mas o menu continua ali. Assim você atende os dois públicos sem depender de IA para funcionar.

**Identificação do paciente pelo número** vai gerar casos reais no primeiro dia:
- Número não cadastrado → fluxo de cadastro
- Número compartilhado (família) → perguntar para quem é
- Mãe marcando para o filho → suportar dependentes

**Transbordo existe desde o primeiro dia.** Bot sem saída para humano é o que faz paciente odiar bot.

---

## Estilo visual

Design system ClinicOS: tema escuro, elevação por luz, cantos de 12px, accent verde para ação primária.

Mensagens do bot e do atendente humano visualmente distintas. Estado "aguardando atendente" com destaque, mas **sem alarme** — âmbar, nunca vermelho: paciente esperando resposta não é emergência.

---

## Restrições

- Nenhuma informação clínica nas conversas ou no painel de contexto além de especialidade e horário
- O bot nunca dá orientação médica; qualquer menção a sintoma escala para humano
- Não construir editor de fluxo visual

---

## Estados

- Fila vazia: "Nenhuma conversa aguardando" — é boa notícia
- Capacidade desligada: mostrar o que deixa de ser feito quando desligada
- Assistente desconectado do WhatsApp: alerta persistente no topo da tela, porque sem conexão a clínica volta ao trabalho manual sem perceber

---

## Dados (mock)

Clínica de médio porte. Desempenho do mês: 612 conversas resolvidas sem humano (78%), 173 escaladas, tempo médio de primeira resposta 8 segundos, pico entre 8h e 10h. Ações: 240 agendamentos, 88 remarcações, 51 cancelamentos, 310 confirmações. Na aba Conversas, 4 aguardando atendente e 12 resolvidas pelo bot no dia.
