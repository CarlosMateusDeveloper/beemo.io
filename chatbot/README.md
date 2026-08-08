# Guia de Desenvolvimento — Módulo ChatBot

Este documento é o passo a passo para quem for implementar as regras de negócio
em cima da estrutura que já existe neste módulo. A estrutura (controllers,
models, services, views, `main.py`) já está pronta e **não deve ser
reorganizada** — o trabalho aqui é preencher o que está marcado como
`NotImplementedError`, na ordem abaixo.

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
4. Confirme em `http://127.0.0.1:8000/health` → deve responder
   `{"status": "ok"}`.
5. Abra `http://127.0.0.1:8000/docs` — o FastAPI gera essa tela sozinho, é
   ali que você testa cada endpoint manualmente enquanto desenvolve, sem
   precisar de Postman.

Se algum desses 5 passos não funcionar, pare e resolva antes de escrever
qualquer código — significa que o ambiente não está certo, não que há um bug
no que você vai construir.

## Tour rápido pela estrutura

| Pasta/arquivo | Responsabilidade | Regra |
|---|---|---|
| `controllers/` | Recebe a requisição HTTP, valida via `views/`, chama **um** service, devolve a resposta | Nunca tem `if` de regra de negócio aqui |
| `services/` | Toda regra de negócio do chatbot mora aqui | É o único lugar que pode decidir "o que fazer" |
| `models/` | Classes SQLAlchemy que **mapeiam tabelas que já existem** (`paciente`, `mensagem`) | Nunca criar tabela/coluna nova aqui — isso é uma mudança de schema, e schema não é do chatbot |
| `views/` | Schemas Pydantic (o que a API aceita/devolve) | Não confundir com "view" de HTML — aqui é só validação de entrada/saída |
| `db.py` | Engine/sessão do SQLAlchemy | Só conexão, não schema |
| `config.py` | Leitura de variáveis de ambiente | Nunca hardcode senha/token aqui — sempre via `.env` |

## O que não fazer (vale relembrar, porque é fácil escorregar aqui)

- **Não** criar camada de Repository, DDD, Clean Architecture, CQRS ou Unit
  of Work. É MVC simples, de propósito — se parecer que "falta uma camada",
  provavelmente não falta.
- **Não** criar pasta `database/` dentro do chatbot, nem alterar
  `database/schema_clinica.sql` por conta própria. Se o chatbot precisar de
  uma coluna ou tabela nova, isso é uma conversa com quem mantém o banco, não
  uma decisão unilateral dentro deste módulo.
- **Não** implementar tudo de uma vez. Siga a ordem dos passos abaixo — cada
  um é testável isoladamente antes de ir para o próximo.
- **Não** commitar `chatbot/.env` (já está no `.gitignore`, mas confira antes
  de dar `git add`).

## Ordem recomendada de implementação

### Passo 1 — Confirmar que os models conversam com o banco de verdade

Antes de implementar qualquer endpoint, abra um shell Python dentro do venv e
confirme que consegue consultar um paciente existente:

```python
from chatbot.db import SessionLocal
from chatbot.models import Contato

db = SessionLocal()
db.query(Contato).first()
```

**Critério de pronto:** isso roda sem erro e retorna um registro (ou `None`
se a tabela `paciente` estiver vazia — nesse caso, insira um paciente de
teste direto no banco antes de seguir).

Se der erro de conexão, o problema é o `DATABASE_URL` no `.env`, não o
código.

### Passo 2 — `contato_controller`: buscar contato por telefone

Arquivo: `controllers/contato_controller.py`, endpoint `GET /contatos/{telefone}`.

- Hoje ele só levanta `NotImplementedError`. Implemente a busca: usar
  `Depends(get_db)` (de `chatbot/db.py`) para pegar uma sessão, consultar
  `Contato` filtrando por `ddd`+`numero` (o telefone chega como uma string só,
  ex: `84999999999` — você vai precisar separar em ddd/numero ou ajustar o
  filtro para concatenar).
- A resposta deve usar `ContatoOut` (já existe em `views/contato.py`).
- Se não encontrar, devolver 404, não uma lista vazia silenciosa.

**Critério de pronto:** `GET /contatos/{telefone}` no `/docs`, com um
telefone que existe na tabela `paciente`, devolve os dados corretos; com um
telefone que não existe, devolve 404.

Este é o passo mais simples de propósito — serve para você validar o
caminho inteiro (controller → model → banco → view de resposta) antes de
mexer em algo mais complexo.

### Passo 3 — `mensagem_controller`: listar mensagens de um contato

Arquivo: `controllers/mensagem_controller.py`, endpoint `GET /mensagens/{telefone}`.

- Mesma lógica do passo 2, mas consultando `Mensagem` filtrando por
  `telefone`, ordenado por `criado_em`.
- Resposta como lista de `MensagemOut`.

**Critério de pronto:** insira 2-3 mensagens de teste na tabela `mensagem`
(via SQL direto) e confirme que o endpoint devolve todas, na ordem certa.

### Passo 4 — `whatsapp_service.send_message`: enviar mensagem de verdade

Arquivo: `services/whatsapp_service.py`.

**Pare antes de começar este passo e alinhe com o grupo:** qual vai ser o
provedor de WhatsApp (Meta WhatsApp Business Cloud API diretamente, Twilio,
Z-API, etc.)? Isso muda completamente o formato da chamada HTTP e das
credenciais em `WHATSAPP_API_URL`/`WHATSAPP_API_TOKEN`. Não escolha sozinho
sem confirmar — é uma decisão que afeta custo e prazo de aprovação de conta
comercial no WhatsApp.

Depois de decidido:
- Implementar a chamada HTTP real usando `httpx.AsyncClient` (o parâmetro já
  está na assinatura da função, pronto para receber um client injetado).
- Tratar erro de rede/resposta não-2xx — não deixar uma falha de envio
  quebrar o processamento da mensagem inteira sem log.

**Critério de pronto:** enviar uma mensagem de teste para o seu próprio
WhatsApp e ela chegar.

### Passo 5 — `mensagem_service.process_incoming_message`: o fluxo real

Arquivo: `services/mensagem_service.py`. Este é o coração do módulo.

O que essa função precisa fazer, nesta ordem:
1. Salvar a mensagem recebida na tabela `mensagem` (`direcao='entrada'`),
   vinculando a `id_paciente` se o telefone já corresponder a um `Contato`
   existente (pode ficar `NULL` se não corresponder — isso já é esperado
   pelo schema).
2. Decidir se é a primeira mensagem desse telefone (ex: contar quantas
   mensagens existem com aquele `telefone` — se for a primeira, é o gatilho
   da boas-vindas).
3. Se for a primeira mensagem, chamar `send_welcome_message` (já existe,
   passo 4 precisa estar pronto antes).
4. Se não for a primeira, **não implementar o resto do fluxo de decisão
   ainda** (interpretar as opções 1-5) — isso é other issue/tarefa, fora do
   escopo desta etapa. Pode deixar um `# TODO` explícito indicando que a
   interpretação de opção ainda não existe.

**Critério de pronto:** mandar uma mensagem de um número que nunca falou com
o sistema e receber a mensagem de boas-vindas de volta. Mandar uma segunda
mensagem do mesmo número e **não** receber a boas-vindas de novo.

### Passo 6 — Testar o fluxo completo do webhook

Endpoint: `POST /webhook`, já registrado em `main.py`.

- Simular manualmente o payload que o provedor de WhatsApp mandaria (formato
  decidido no passo 4) e confirmar que o fluxo do passo 5 dispara
  corretamente a partir do webhook, não só chamando a função direto em
  Python.

**Critério de pronto:** `POST /webhook` pelo `/docs`, com um payload de
mensagem nova, resulta na mensagem salva no banco e na resposta de
boas-vindas enviada.

## Como testar cada passo

Prefira sempre o `/docs` (Swagger) pra testar manualmente durante o
desenvolvimento — não precisa escrever script de teste pra cada passo. Só
formalize um teste automatizado (se o grupo decidir usar `pytest`) depois
que o fluxo estiver validado manualmente.

## Se travar

Se em algum passo parecer que falta uma camada, uma tabela nova, ou um
padrão diferente de MVC — provavelmente a resposta certa é perguntar antes
de implementar, não decidir sozinho e seguir. O objetivo deste módulo é
ficar simples de propósito.
