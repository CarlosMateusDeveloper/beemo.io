# Segredos e configuração de produção

Use o arquivo [`.env.production.example`](../.env.production.example) apenas como referência. Copie os valores para o cofre de segredos da VPS ou para um arquivo `.env.production` com permissão de leitura exclusiva do usuário de deploy. Esse arquivo é ignorado pelo Git.

Ative a API com `SPRING_PROFILES_ACTIVE=production`. Nesse perfil, o schema é somente validado: migrations devem ser aplicadas antes do deploy, nunca pelo Hibernate.

Gere os segredos localmente, sem enviá-los por chat ou commit:

```bash
openssl rand -base64 48  # JWT_SECRET
openssl rand -base64 32  # EXPORT_ENCRYPTION_KEY
```

Defina `APP_FRONTEND_URL`, `APP_API_URL` e `CORS_ALLOWED_ORIGINS` como origens HTTPS finais. Registre os callbacks OAuth com `https://api.seudominio.com/login/oauth2/code/google` e `https://api.seudominio.com/login/oauth2/code/microsoft`.

A API recusa iniciar em produção se faltar segredo, se `JWT_SECRET` tiver menos de 32 bytes, se a chave de exportação não tiver 32 bytes Base64, se cookies não forem seguros ou se URLs públicas aceitarem localhost/HTTP.
