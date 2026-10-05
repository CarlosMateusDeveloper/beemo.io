# Ativar magic link, Google e Microsoft no clinicOS

A tela e os fluxos já estão implementados. Os cadastros externos e as credenciais precisam ser criados por você. Este guia usa localhost no desenvolvimento.

## 1. Preparar o arquivo local

Na pasta backend, copie .env.example para .env. O backend agora carrega .env automaticamente quando iniciado nessa pasta. Não coloque aspas em volta dos valores. O Git ignora esse arquivo. Variáveis definidas no ambiente do processo têm precedência.

Use inicialmente:

```properties
APP_FRONTEND_URL=http://localhost:5173
APP_API_URL=http://localhost:8080
CORS_ALLOWED_ORIGINS=http://localhost:5173
AUTH_SECURE_COOKIES=false
JWT_EXPIRATION_MINUTES=30
```

Gere JWT_SECRET com pelo menos 32 bytes aleatórios. Por exemplo, use o resultado de `node -e "console.log(require('node:crypto').randomBytes(32).toString('hex'))"` apenas no seu .env. Não publique o arquivo nem envie os segredos em conversas.

Mantenha localhost em todos os endereços durante esses testes. Em produção, troque por HTTPS e use AUTH_SECURE_COOKIES=true. Os serviços devem compartilhar o host por proxy, conforme login-magic-link.md.

## 2. Cadastrar o Resend

1. Crie sua conta em https://resend.com.
2. Em Domains, adicione um domínio ou subdomínio que você controla, por exemplo acesso.seudominio.com.br.
3. No provedor de DNS, publique os registros solicitados pelo Resend e aguarde a verificação.
4. Em API Keys, crie uma chave para envio, restrita ao domínio quando disponível.
5. Defina um remetente desse domínio e preencha:

```properties
RESEND_API_KEY=VALOR_DA_CHAVE
RESEND_FROM=ClinicOS <acesso@acesso.seudominio.com.br>
```

O Gmail do usuário pode receber o link. Ele não precisa ser o remetente: o domínio de envio deve ser seu. Deixe o rastreamento de cliques desativado nos e-mails de autenticação.

Fontes: [domínios](https://resend.com/docs/dashboard/domains/introduction), [chaves de API](https://resend.com/docs/dashboard/api-keys/introduction).

## 3. Cadastrar o Google

1. No Google Cloud Console, crie ou selecione um projeto.
2. Configure o Google Auth Platform / tela de consentimento: nome clinicOS, e-mail de suporte e público. Para testar contas Gmail, use público externo e inclua seu e-mail como usuário de teste se estiver em modo de testes.
3. Em Clients / Credentials, crie um cliente OAuth do tipo Web application.
4. Cadastre exatamente este Authorized redirect URI:

```text
http://localhost:8080/login/oauth2/code/google
```

5. Salve o Client ID e o Client secret no backend:

```properties
GOOGLE_CLIENT_ID=CLIENT_ID
GOOGLE_CLIENT_SECRET=CLIENT_SECRET
```

O callback é do backend na porta 8080; não é a página React na 5173. O código solicita somente openid, profile e email.

Fontes: [OpenID Connect](https://developers.google.com/identity/openid-connect/openid-connect), [cliente web OAuth](https://developers.google.com/identity/protocols/oauth2/web-server).

## 4. Cadastrar a Microsoft

Esta implementação utiliza um tenant específico do Microsoft Entra: usuários corporativos/escolares e convidados desse tenant. Não configure common ou organizations no lugar do UUID do tenant.

1. No Microsoft Entra admin center, entre em Entra ID → App registrations → New registration.
2. Nomeie clinicOS e selecione contas apenas do seu diretório (single tenant).
3. Em Authentication, adicione a plataforma Web e o callback:

```text
http://localhost:8080/login/oauth2/code/microsoft
```

4. Em Overview, copie Application (client) ID e Directory (tenant) ID.
5. Em Certificates & secrets, crie um Client secret. Copie o valor do segredo, não o identificador; registre sua data de expiração.
6. Preencha:

```properties
MICROSOFT_CLIENT_ID=APPLICATION_CLIENT_ID
MICROSOFT_CLIENT_SECRET=VALOR_DO_SEGREDO
MICROSOFT_TENANT_ID=DIRECTORY_TENANT_ID
MICROSOFT_USER_MAPPINGS=OBJECT_ID_DO_USUARIO:ID_DO_USUARIO_NO_CLINICOS
```

Para autorizar o primeiro acesso Microsoft, copie o Object ID da pessoa em Entra ID → Users e associe ao ID da conta local em MICROSOFT_USER_MAPPINGS. Separe várias associações por vírgula. O ID local pode ser consultado com `SELECT id, nome, email FROM usuario;`. Essa configuração é administrativa: o usuário entra diretamente pelo SSO, sem confirmar e-mail. Não use o e-mail ou preferred_username do token Microsoft como identificador de autorização.

Use o fluxo Web com código de autorização. Não habilite implicit grant para esta integração. A política do seu tenant pode exigir consentimento administrativo.

Fontes: [registro do aplicativo](https://learn.microsoft.com/en-us/entra/identity-platform/quickstart-register-app?tabs=client-secret), [credenciais](https://learn.microsoft.com/en-us/entra/identity-platform/how-to-add-credentials).

## 5. Definir o e-mail autorizado do clinicOS

Cadastrar os aplicativos nos provedores não cria uma conta administrativa no clinicOS. O administrador local atualmente usa admin@clinicos.local, que não recebe mensagens reais.

Quando escolher seu e-mail, atualize essa conta no banco local. Exemplo para executar no PostgreSQL após substituir o endereço:

```sql
UPDATE usuario
SET email = 'SEU_EMAIL_REAL'
WHERE email = 'admin@clinicos.local'
  AND perfil = 'administrador'
RETURNING id, email;
```

Confirme que retornou exatamente a conta pretendida. Esse comando não foi executado automaticamente. Não habilite cadastro público de administradores.

## 6. Iniciar e testar

Em um terminal dentro de backend:

```powershell
.\mvnw.cmd spring-boot:run
```

Em outro terminal dentro de frontend:

```powershell
npm.cmd run dev
```

Abra http://localhost:5173/login. Reinicie o backend sempre que editar o .env.

1. Digite o e-mail autorizado e clique em Enviar link de acesso.
2. Confira o e-mail e o registro de envio no Resend. Abra o link e confirme a entrada. O link expira em 15 minutos e só funciona uma vez.
3. Para Google ou Microsoft, clique no respectivo botão e conclua a autenticação do provedor.
4. O SSO retorna diretamente ao sistema, inclusive na primeira vez. Não depende de Resend nem exige magic link. Para Google, use o Gmail ou Google Workspace que corresponde ao e-mail autorizado da conta local. Para Microsoft, configure previamente o Object ID conforme a seção 4.
5. Uma conta sem autorização recebe uma mensagem de acesso não autorizado; nenhum administrador é criado automaticamente.

Botão indisponível significa que o provedor ainda não foi configurado no backend. E-mail não recebido pode significar domínio/remetente não verificado, conta clinicOS com outro endereço ou bloqueio do provedor. Confira os logs do Resend sem expor chaves.

## Produção

Cadastre callbacks equivalentes no domínio HTTPS da API. Atualize APP_API_URL, APP_FRONTEND_URL, CORS_ALLOWED_ORIGINS e AUTH_SECURE_COOKIES=true. Aplique as migrações 010_magic_link.sql e 011_auth_sessions_oauth.sql se ainda não existirem.

A aprovação/publicação do aplicativo Google e as políticas de consentimento Microsoft dependem da configuração escolhida. Cadastre as URLs definitivas antes de liberar o acesso aos usuários.

O envio real e a autenticação nos provedores somente podem ser validados após esses cadastros. Os testes automatizados usam Resend simulado e identidades OAuth de teste.
