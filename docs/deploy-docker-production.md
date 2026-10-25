# Deploy Docker de produção

Este procedimento sobe frontend, API Java, agenda, chatbot, PostgreSQL e Caddy na mesma VPS. Somente o proxy publica `80` e `443`. A rede `data` é interna; nenhum serviço publica a porta do banco, da API ou das interfaces administrativas diretamente.

## Pré-requisitos

- Docker Engine e Compose v2 na VPS; DNS de `APP_FRONTEND_URL` e `APP_API_URL` apontando para ela.
- Portas públicas 80 e 443 disponíveis para os desafios ACME do Caddy. A restrição do firewall e do SSH pertence à #62.
- Backup PostgreSQL `pg_dump -Fc` confiável, com as migrações exigidas pela versão do backend. **Não use `database/schema_clinica.sql` para iniciar produção:** ele contém dados de demonstração.
- Segredos fora do repositório. Use [`.env.production.example`](../.env.production.example) como referência e mantenha `.env.production` com permissão `0600` sob o usuário de deploy. `DB_ADMIN_PASSWORD` e `DB_PASSWORD` devem ser distintos; a role da aplicação não pode ter `SUPERUSER` nem `BYPASSRLS`.

## Primeiro servidor

1. Faça checkout da revisão aprovada. Copie `.env.production.example` para `.env.production`, substitua todos os placeholders e defina `APP_VERSION` com uma versão única da aplicação. Use hosts HTTPS distintos para `APP_FRONTEND_URL` e `APP_API_URL`; o frontend recebe a URL da API no build.
2. Execute `docker compose --env-file .env.production -f docker-compose.production.yml config -q` para validar a configuração sem imprimir os segredos.
3. Suba só o banco: `docker compose --env-file .env.production -f docker-compose.production.yml up -d postgres`. O script de inicialização cria a role limitada da aplicação apenas na primeira criação do volume.
4. Restaure o backup validado no banco vazio. Em um shell Bash na VPS, com o arquivo `backup.dump` fora do repositório:

   ```bash
   docker compose --env-file .env.production -f docker-compose.production.yml \
     exec -T postgres sh -ec 'pg_restore --exit-on-error --no-owner --no-acl -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < backup.dump
   docker compose --env-file .env.production -f docker-compose.production.yml \
     exec -T postgres sh -ec 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -v app_user="$DB_USER" -f /run/grant-app-role.sql'
   ```

5. Confirme que o backup inclui as migrações até a versão exigida por `TenantDatabaseSafety`. Aplique qualquer migração pendente com o dono das tabelas **antes** de iniciar o backend; o perfil `production` usa `ddl-auto=validate` e não modifica o schema no boot.
6. Construa e inicie os serviços:

   ```bash
   docker compose --env-file .env.production -f docker-compose.production.yml build
   docker compose --env-file .env.production -f docker-compose.production.yml up -d --no-build
   docker compose --env-file .env.production -f docker-compose.production.yml ps
   ```

7. Verifique login, API, agenda, WebSocket e WhatsApp pelos domínios públicos. Teste também uma requisição sem autenticação e uma com clínica diferente. A rota interna `/actuator/health` é usada pelo contêiner da API e bloqueada no proxy público.

## Atualização e rollback

Antes de atualizar, registre a revisão Git, `APP_VERSION`, resultado dos health checks e um backup restaurável. Faça checkout da revisão nova, altere `APP_VERSION`, execute `build` e `up -d --no-build`. O Compose mantém os volumes de PostgreSQL e certificados do Caddy; as imagens anteriores permanecem identificadas pela versão.

Se a nova revisão falhar e o schema continuar compatível, volte à revisão Git anterior, restaure seu `APP_VERSION` e execute `up -d --no-build`. Não edite imagens ou dados manualmente. Se houve migração de banco incompatível, siga o procedimento de restauração da #35 antes de voltar o código; não prometa rollback apenas por troca de imagem nesse caso.

As migrações, o teste completo em VPS nova e o rollback medido ainda precisam de evidência operacional para encerrar a #61. A #62 cobre TLS, cabeçalhos e firewall; a #64 cobre monitoramento externo e alertas.
