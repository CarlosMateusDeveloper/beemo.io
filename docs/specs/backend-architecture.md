# Spec: Arquitetura MVC e estrutura base da API

> Movido da issue #5 para spec de arquitetura — decisão estrutural, não uma tarefa isolada. Todas as 6 APIs de página (dashboard, pacientes, medicos, exames, financeiro, convenios) e a API de autenticação (issue #7) devem seguir o que for decidido aqui.

## Contexto

A pasta `backend/` do repositório está vazia. Antes de qualquer endpoint ser escrito, o grupo precisa decidir a stack e deixar uma estrutura MVC funcional, para que todo mundo contribua seguindo o mesmo padrão.

## Escopo

- Decidir a stack (linguagem/framework) e registrar a decisão por escrito neste documento (um ADR simples: o que foi escolhido, alternativas consideradas, motivo).
- Estruturar as camadas MVC:
  - **Model**: entidades mapeando as tabelas de `database/schema_clinica.sql` (paciente, medico, agenda, consulta, usuario, prontuario, mensagem, fila_atendimento etc.).
  - **Controller**: rotas HTTP, uma responsabilidade por endpoint, sem regra de negócio pesada dentro do controller.
  - **View/Serialização**: formato de resposta JSON consistente (DTOs de saída, não expor a entidade de banco crua — ex: nunca devolver `senha` no JSON de `usuario`).
- Definir estrutura de pastas do projeto.
- Padronizar formato de erro (ex: `{ "error": { "code": "...", "message": "..." } }`) usado em toda a API.
- Versionar a API (prefixo `/api/v1`).
- Configuração de ambiente via variáveis (`.env`), incluindo string de conexão com o PostgreSQL — nunca hardcoded.
- Endpoint de health-check (`GET /api/v1/health`) que valida a conexão com o banco.
- README com passo a passo de como rodar o backend localmente.

## Fora de escopo

- Implementação de endpoints de negócio (login, dashboard, pacientes, medicos, exames, financeiro, convenios) — cada uma tem sua própria issue e depende desta spec estar decidida primeiro.

## Critérios de aceite

- [ ] Stack escolhida e justificada neste documento.
- [ ] Projeto roda localmente com um comando documentado no README.
- [ ] `GET /api/v1/health` responde 200 e confirma conexão com o Postgres.
- [ ] Estrutura de pastas reflete a separação Model/Controller/View commitada.
- [ ] Formato de erro padronizado documentado e aplicado no health-check.
- [ ] Nenhuma credencial (senha de banco, chave secreta) commitada no código — tudo via `.env` (com `.env.example` versionado, sem valores reais).

## Referências

- `database/schema_clinica.sql` (schema já migrado para PostgreSQL, serve de base para os Models).
- Issue #7 (Autenticação) e as 6 issues de API de página — todas dependem desta spec.
