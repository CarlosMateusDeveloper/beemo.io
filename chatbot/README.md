# Módulo ChatBot — WhatsApp Business Cloud API

Este módulo conversa com o paciente pelo WhatsApp usando a **API oficial da
Meta** (WhatsApp Business Cloud API), documentada em
<https://developers.facebook.com/docs/whatsapp/cloud-api>.

A estrutura (controllers, models, services, views, `main.py`) **não deve ser
reorganizada** — é MVC simples de propósito.

## Antes de começar

1. Ative o ambiente virtual (já criado em `chatbot/.venv`):
   ```
   chatbot\.venv\Scripts\activate
   ```
2. Copie `chatbot/.env.example` para `chatbot/.env` e preencha `DATABASE_URL`
   apontando para o mesmo Postgres onde `database/schema_clinica.sql` já foi
   aplicado (é o mesmo banco que o backend Java usa — não é um banco separado
   do chatbot).
3. Suba o servidor a partir da **raiz do repositório** (`clinica/`, não de
   dentro de `chatbot/`):
   ```
   uvicorn chatbot.main:app --reload
   ```
   (ou `npm run dev` na raiz, que sobe backend + agenda + chatbot + frontend
   juntos, o chatbot na porta 8082)
4. Confirme em `http://127.0.0.1:8000/health` → deve responder
   `{"status": "ok"}`.
5. Abra `http://127.0.0.1:8000/docs` — o FastAPI gera essa tela sozinho, é
   ali que você testa cada endpoint manualmente.

Sem credenciais do WhatsApp o módulo **sobe normalmente**: o painel mostra
"desconectado", o webhook continua respondendo, e tentar enviar mensagem
devolve 503 com a explicação. Ninguém vê uma mensagem "enviada" que na
verdade não saiu.

## Configurar o WhatsApp de verdade

Tudo em `chatbot/.env` (ver `.env.example`). Os valores saem de
[developers.facebook.com](https://developers.facebook.com) → seu app →
WhatsApp:

| Variável | Onde encontrar |
|---|---|
| `WHATSAPP_PHONE_NUMBER_ID` | WhatsApp > Configuração da API. É um **ID**, não o número. |
| `WHATSAPP_API_TOKEN` | Mesma tela. O token de teste expira em 24h — para uso real, gere um token permanente de *System User* no Business Manager. |
| `WHATSAPP_VERIFY_TOKEN` | Você inventa. A mesma string vai no campo "Verificar token" ao cadastrar o webhook. |
| `WHATSAPP_APP_SECRET` | Configurações > Básico > Chave secreta do app. |
| `WHATSAPP_API_VERSION` | Opcional (padrão `v23.0`). A Meta descontinua versões antigas — dá pra subir só trocando isto. |

**Cadastrando o webhook:** a Meta precisa alcançar `POST /webhook` por
HTTPS público. Em desenvolvimento, exponha o serviço (`ngrok http 8082`) e
cadastre `https://<sua-url>/webhook` em WhatsApp > Configuração > Webhook,
assinando o campo **messages**. A Meta chama primeiro um `GET /webhook` com
um desafio; o módulo responde sozinho se o `WHATSAPP_VERIFY_TOKEN` bater.

**Janela de 24 horas:** a Meta só aceita texto livre nas 24h seguintes à
última mensagem do paciente. Fora disso o envio precisa ser por *template*
aprovado — é o caso de lembrete de consulta e aviso de exame pronto. Use
`whatsapp_service.send_template(...)`; o template tem que estar aprovado no
Gerenciador do WhatsApp antes, não dá pra criar na hora.

## Tour rápido pela estrutura

| Pasta/arquivo | Responsabilidade | Regra |
|---|---|---|
| `controllers/` | Recebe a requisição HTTP, valida via `views/`, chama **um** service, devolve a resposta | Nunca tem `if` de regra de negócio aqui |
| `services/` | Toda regra de negócio do chatbot mora aqui | É o único lugar que pode decidir "o que fazer" |
| `models/` | Classes SQLAlchemy que **mapeiam tabelas que já existem** (`paciente`, `mensagem`, `conversa`) | Nunca criar tabela/coluna nova aqui — isso é uma mudança de schema, e schema não é do chatbot |
| `views/` | Schemas Pydantic (o que a API aceita/devolve), incluindo o payload da Meta | Não confundir com "view" de HTML — aqui é só validação de entrada/saída |
| `db.py` | Engine/sessão do SQLAlchemy | Só conexão, não schema |
| `config.py` | Leitura de variáveis de ambiente | Nunca hardcode senha/token aqui — sempre via `.env` |

Os serviços, em uma linha cada:

- `whatsapp_service` — fala com a Cloud API: envio de texto, envio de
  template, validação da assinatura do webhook, consulta do número.
- `mensagem_service` — decide o que fazer quando chega mensagem de paciente.
- `contato_service` — descobre qual `paciente` é aquele telefone.
- `whatsapp_painel_service` — alimenta a tela `/whatsapp` do frontend.

## O que já está implementado

- `GET /webhook` — handshake de verificação da Meta.
- `POST /webhook` — recebe mensagens, **valida a assinatura
  `X-Hub-Signature-256`**, ignora eventos de status de entrega, e sempre
  responde 200 quando a origem é legítima (sem 200 a Meta reenvia o evento
  em loop e acaba desativando o webhook).
- Texto, botão/lista interativa, imagem, áudio, documento e localização
  entram no histórico (mídia sem legenda vira um rótulo legível).
- Primeira mensagem de um número → boas-vindas automática; mensagens
  seguintes são gravadas e encaminhadas à fila de atendente enquanto o motor de opções não está implementado.
- Conversa assumida por um atendente (`estado = com_agente`) → o bot fica
  calado e não responde por cima dele.
- Envio pelo painel do atendente vai pelo WhatsApp de verdade; se a Meta
  recusar, o atendente vê o erro em vez de uma bolha que nunca chegou.
- `GET /contatos/{telefone}` e `GET /mensagens/{telefone}`.
- Telefone é casado com o cadastro tentando as duas formas do nono dígito
  (`8488887777` e `84988887777`), porque cadastro antigo e WhatsApp
  discordam nisso o tempo todo.

## O que ainda falta

1. **Interpretar as opções 1–5** da mensagem de boas-vindas e conduzir o
   fluxo (agendar, remarcar, confirmar). Até esse motor existir, `mensagem_service._processar` grava a mensagem
   seguinte e coloca a conversa em `aguardando` para a recepção assumir.
2. **Ligar as capacidades do painel ao comportamento do bot.** A tela
   "Assistente" já salva textos e liga/desliga capacidades no banco
   (`capacidade_bot_config`, `mensagem_template_bot`), mas o bot ainda não
   lê essas configurações — a boas-vindas é a constante `WELCOME_MESSAGE`.
3. **Idempotência de verdade.** A Meta reenvia eventos; hoje os `wamid` já
   processados ficam num cache em memória (some ao reiniciar). O certo é
   uma coluna com o `wamid` em `mensagem` e um `UNIQUE` — mas mudança de
   schema não é decisão deste módulo.
4. **Disparos proativos** (lembrete de véspera, aviso de exame pronto) via
   `send_template`. O envio já existe; falta quem o agende.
5. **Multiempresa.** `ID_CLINICA_ATUAL` (em `config.py`) ainda é uma
   clínica só — depende da Fase 0 do roadmap.

## O que não fazer

- **Não** criar camada de Repository, DDD, Clean Architecture, CQRS ou Unit
  of Work.
- **Não** criar pasta `database/` dentro do chatbot, nem alterar
  `database/schema_clinica.sql` por conta própria.
- **Não** commitar `chatbot/.env` — ele tem o token do WhatsApp e a senha do
  banco (já coberto por `chatbot/.gitignore`, mas confira antes do
  `git add`).
- **Não** desligar a validação de assinatura em produção. Sem
  `WHATSAPP_APP_SECRET` a verificação é pulada (com aviso no log), o que só
  se justifica em desenvolvimento.

## Verificar as correções do painel

- `chatbot/.venv/Scripts/python.exe -m unittest discover -s chatbot/tests -v`
- `node --test frontend/src/components/whatsapp/autosave.test.js`
- `cd frontend` e `npm run build`

Reinicie o serviço Python após alterações no chatbot quando ele estiver sem
`--reload`. O painel consulta novas conversas/mensagens a cada 5 segundos.
As configurações são salvas por campo, em sequência; falhas ficam visíveis e
podem ser reenviadas pelo botão de nova tentativa. Isso não implementa o motor
de automação listado acima.
