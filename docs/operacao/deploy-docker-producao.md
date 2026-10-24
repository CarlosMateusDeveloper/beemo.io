# Deploy Docker em produção

Este guia executa a interface, API principal, agenda, WhatsApp e PostgreSQL numa única pilha Docker. Somente o Caddy publica portas na internet; os demais serviços ficam na rede `application`, que é interna ao Docker.

## Pré-requisitos

- VPS Linux com Docker Engine e Docker Compose Plugin;
- domínio apontado para o IP público da VPS;
- portas TCP 80 e 443 e UDP 443 liberadas no firewall;
- um cofre de segredos ou arquivo `.env.production` com permissão `600`;
- backup verificado antes da primeira subida.

Uma VPS com 2 vCPU e 4 GB de RAM atende a primeira clínica. O limite de memória declarado no compose soma cerca de 2,1 GB; deixe capacidade para o sistema operacional, build de imagens e picos de uso.

## Preparação

1. Copie o código para o servidor e entre na raiz do repositório.
2. Copie `.env.production.example` para `.env.production`; ele nunca deve entrar no Git.
3. Preencha todos os valores `SUBSTITUA...`, `APP_DOMAIN`, `ACME_EMAIL` e as URLs HTTPS públicas.
4. Para `CHATBOT_DATABASE_URL`, use a mesma conta do banco, com a senha codificada para URL. Exemplo de formato: `postgresql+psycopg://usuario:senha%40segura@postgres:5432/clinisis`.
5. Proteja o arquivo de segredos:

   ```bash
   chmod 600 .env.production
   ```

6. Aplique as migrations versionadas no banco restaurado ou vazio **antes** de subir a API em perfil `production`. O perfil usa `ddl-auto=validate` e interrompe a inicialização se o schema não estiver atualizado. Registre no change log qual migration foi aplicada e faça backup imediatamente antes de qualquer mudança de schema.

## Subida e verificação

```bash
export DEPLOY_IMAGE_TAG=$(git rev-parse --short HEAD)

docker compose --env-file .env.production -f docker-compose.production.yml config
docker compose --env-file .env.production -f docker-compose.production.yml build --pull
docker compose --env-file .env.production -f docker-compose.production.yml up -d
docker compose --env-file .env.production -f docker-compose.production.yml ps
docker compose --env-file .env.production -f docker-compose.production.yml logs --tail=100 api
```

Aguarde os serviços `api`, `frontend`, `agenda`, `chatbot` e `postgres` ficarem saudáveis. Verifique também:

- `https://<domínio>/api/auth/providers` responde pela API;
- login por magic link e SSO funcionam;
- `/agenda-api` e `/chatbot-api/health` são alcançados somente pelo mesmo domínio;
- não há porta 5432, 8080, 8081 ou 8082 exposta no host (`docker compose ... port` não deve listar essas portas).

Caddy obtém e renova automaticamente o certificado TLS usando `ACME_EMAIL`. Os volumes `caddy_data` e `caddy_config` guardam certificados; não os remova durante uma atualização.

## Atualização

```bash
git fetch origin
git switch main
git pull --ff-only
export DEPLOY_IMAGE_TAG=$(git rev-parse --short HEAD)
docker compose --env-file .env.production -f docker-compose.production.yml build --pull
docker compose --env-file .env.production -f docker-compose.production.yml up -d --remove-orphans
docker image prune -f
```

Faça a atualização numa janela de manutenção e registre a revisão Git implantada. Confirme o login, uma operação de agenda e o painel WhatsApp antes de encerrar a janela.

## Rollback

1. Preserve os logs e anote o commit/release que falhou.
2. Volte ao commit anterior que estava funcionando:

   ```bash
   git switch --detach <commit-anterior>
   export DEPLOY_IMAGE_TAG=$(git rev-parse --short HEAD)
   docker compose --env-file .env.production -f docker-compose.production.yml build
   docker compose --env-file .env.production -f docker-compose.production.yml up -d
   ```

3. Se uma migration incompatível já foi aplicada, restaure o backup verificado conforme o runbook de continuidade; não apague volumes nem use `down -v` como forma de rollback.
4. Depois da recuperação, valide as mesmas rotas da seção anterior e documente o incidente.

## Operação segura

- Não use `docker compose down -v` em produção: isso remove dados do PostgreSQL.
- Não publique `postgres`, `api`, `agenda` ou `chatbot` por `ports`.
- Rotacione segredos pelo cofre e reinicie a pilha com `up -d`; nunca os coloque em imagens, logs ou comandos compartilhados.
- Os limites declarados no compose são guardrails. Monitore RAM, disco do volume PostgreSQL, certificados e saúde dos containers.
