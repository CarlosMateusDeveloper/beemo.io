# Login seguro do clinicOS

A tela oferece magic link por e-mail e SSO Google/Microsoft, sem campo de senha. O acesso operacional exige uma conta cadastrada pela administração com um endereço de e-mail real e autorizado. Senhas novas são protegidas com Argon2id (19 MiB, duas iterações, paralelismo 1 e salt aleatório). Uma senha BCrypt existente é migrada somente após um login válido. Novas contas exigem senha de 12 a 200 caracteres. Não existe cadastro público nem bypass de autenticação.

## Sessão, CSRF e permissões

A autenticação emite JWT assinado com identificador único, emissor clinicOS, audiência clinicos-api e validade padrão de 30 minutos. Não contém e-mail nem perfil. A sessão fica no cookie clinicos_session: HttpOnly, SameSite=Lax, caminho / e Secure em produção. O frontend não recebe nem armazena o JWT em localStorage. GET /api/auth/me restaura a conta; cada requisição confere a sessão no banco e usa o perfil atual do usuário.

Sair executa POST /api/auth/logout, revoga a sessão no banco e apaga o cookie. Uma cópia do mesmo JWT deixa de funcionar. Outras sessões independentes permanecem ativas. Alterar a chave de assinatura encerra todas as sessões existentes.

Antes de POST/PUT/PATCH/DELETE, o cliente obtém o token em GET /api/auth/csrf e envia X-CSRF-TOKEN junto com os cookies. Login, magic link e logout também exigem CSRF. O cookie CSRF é HttpOnly; o token é lido pela resposta JSON, acessível somente às origens permitidas por CORS. O frontend renova o token e tenta uma vez novamente apenas quando a rejeição é explicitamente csrf_invalid.

## Configuração do backend

Copie backend/.env.example para backend/.env e preencha os valores sem aspas. O backend carrega esse arquivo ao iniciar dentro da pasta backend; variáveis do ambiente continuam tendo precedência. O arquivo .env é ignorado pelo Git. Configure:

- JWT_SECRET: segredo aleatório com pelo menos 32 bytes. Sem ele, somente no desenvolvimento HTTP local, é gerada chave temporária em memória.
- JWT_EXPIRATION_MINUTES: 30 por padrão.
- APP_FRONTEND_URL: origem pública do frontend, por exemplo https://clinica.example.
- APP_API_URL: origem pública da API, sem caminho; pode ser a mesma origem do frontend.
- AUTH_SECURE_COOKIES=true em produção HTTPS.
- CORS_ALLOWED_ORIGINS: origens exatas permitidas, separadas por vírgula.

Use HTTPS em produção e publique frontend, Java, agenda e chatbot no mesmo host por proxy reverso. O cookie é host-only. Rotas Java /api, /oauth2 e /login/oauth2 devem chegar ao backend; as demais rotas /login pertencem ao frontend. Os padrões de produção do frontend são /agenda-api e /chatbot-api; remova esses prefixos ao encaminhar ao Go e Python. Evite hospedar conteúdo não confiável nesse host.

No desenvolvimento, use sempre o mesmo nome de host (localhost ou 127.0.0.1), inclusive nas URLs OAuth. Java usa 8080, Go 8081, Python 8082 e Vite 5173. Cookies não são isolados por porta. Configure CORS nos três serviços. Reinicie os serviços após mudar o ambiente.

Aplique database/migrations/010_magic_link.sql e 011_auth_sessions_oauth.sql em produção. Em desenvolvimento o ddl-auto=update existente também cria as entidades; as migrações incluem as chaves estrangeiras.

## Resend e magic link

Configure RESEND_API_KEY e RESEND_FROM (remetente de domínio verificado). APP_FRONTEND_URL define o destino dos links. Desative rastreamento de cliques nos e-mails de autenticação.

POST /api/auth/magic-link recebe e-mail. Contas cadastradas recebem um token aleatório de 256 bits, guardado apenas como SHA-256, com validade de 15 minutos e consumo atômico de uso único. Contas desconhecidas recebem a mesma mensagem genérica, sem cadastro ou envio. Falha de configuração ou envio não simula sucesso.

O destino /login/magic#token=... remove o fragmento do endereço e exige confirmação antes de POST /api/auth/magic-link/verify. Isso evita consumo pela simples visita de scanners de e-mail. Recarregar exige reabrir o link. A resposta de login contém somente usuario; a credencial é entregue por Set-Cookie.

## Google e Microsoft (OAuth 2.0 / OpenID Connect)

Configure GOOGLE_CLIENT_ID e GOOGLE_CLIENT_SECRET para Google. Para Microsoft, configure MICROSOFT_CLIENT_ID, MICROSOFT_CLIENT_SECRET e MICROSOFT_TENANT_ID (UUID do tenant autorizado). Provedores incompletos impedem a inicialização; sem credenciais os respectivos botões aparecem como indisponíveis e explicam que a ativação está pendente. As credenciais são exclusivas do backend, nunca variáveis VITE.

Cadastre exatamente os callbacks:

- APP_API_URL/login/oauth2/code/google
- APP_API_URL/login/oauth2/code/microsoft

O Spring Security executa Authorization Code com PKCE S256, estado, nonce e validação do ID token do provedor. O callback público não é uma sessão operacional: a API aceita apenas a sessão própria validada no banco. O estado OAuth usa uma sessão temporária de cinco minutos; tokens de acesso e renovação do provedor não são persistidos.

O callback do SSO conclui o login imediatamente, inclusive no primeiro acesso autorizado, sem magic link, Resend ou confirmação adicional do ClinicOS. A identidade persistida é issuer + subject e só pode pertencer a um usuário. As permissões continuam vindo da conta local; o login não cria administradores nem libera os dados da clínica para qualquer conta pública.

No primeiro Google, o e-mail atestado deve corresponder à conta previamente autorizada no ClinicOS. É necessário email_verified=true e endereço Gmail ou domínio hd correspondente do Google Workspace. Contas Google que usam e-mail de terceiros sem hd não são associadas automaticamente; podem ser vinculadas pelo Perfil de uma sessão já autenticada. Depois de vincular, mudanças no e-mail do provedor não trocam a identidade local.

Na Microsoft, a administração configura MICROSOFT_USER_MAPPINGS com Object-ID:ID-local, separados por vírgula, no tenant indicado por MICROSOFT_TENANT_ID. O callback valida issuer e tid e usa o oid autorizado para a associação inicial. E-mail e preferred_username não concedem acesso, pois são mutáveis. Exemplo: MICROSOFT_USER_MAPPINGS=aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee:1. O vínculo explícito pelo Perfil de uma sessão válida também permanece disponível. Não há confirmação de e-mail durante o SSO.

Uma conta sem autorização retorna ao login com uma mensagem específica. Nenhum cookie operacional é emitido nessa situação. A sessão temporária OAuth é encerrada em sucesso ou falha.

Referências de identidade: [Google e autoridade sobre e-mail](https://developers.google.com/identity/sign-in/web/backend-auth), [claims da Microsoft](https://learn.microsoft.com/en-us/entra/identity-platform/id-token-claims-reference).

## Agenda e WhatsApp

Go e Python encaminham os cookies necessários à validação em AUTH_API_URL/api/auth/session/check (Java interno; padrão http://localhost:8080). Leituras validam com GET; alterações validam com POST e X-CSRF-TOKEN. Falha de autenticação ou indisponibilidade bloqueia a operação. Configure AUTH_API_URL somente para um endereço interno confiável.

O WebSocket recebe o cookie automaticamente, negocia apenas clinicos, verifica a origem e revalida a sessão a cada 30 segundos. O webhook da Meta mantém sua própria validação de assinatura e não usa a sessão do operador.

## Limites e verificação

Há limitação de tentativas por e-mail e IP; magic links permitem três solicitações por e-mail em 15 minutos. A limitação é em memória, por instância, e reinicia com o processo. Para várias instâncias use limites compartilhados no gateway. O IP vem da conexão; cabeçalhos de encaminhamento arbitrários não são confiáveis.

Testes Java cobrem senha, migração BCrypt, JWT inválido/expirado, sessão revogada, CSRF, permissões, vínculos OAuth e magic link concorrente. AuthIntegrationTest requer RUN_AUTH_INTEGRATION=true, usa banco de desenvolvimento e remove contas temporárias. Não execute contra produção. Resend é simulado; testes não enviam e-mails reais. OAuthProtocolTest verifica estado, nonce, PKCE e callback fixo sem acessar um provedor externo.

Validações complementares: node --test src/lib/apiClient.test.js no frontend, go test ./... em agenda-service e python -m unittest chatbot.test_auth na raiz. O fluxo completo nos provedores e a entrega real de e-mail exigem as configurações externas.

Referências: [Spring Security OAuth](https://docs.spring.io/spring-security/reference/servlet/oauth2/client/authorization-grants.html), [armazenamento de senhas](https://docs.spring.io/spring-security/reference/7.0/features/authentication/password-storage.html), [Resend](https://resend.com/docs/api-reference/emails/send-email).
