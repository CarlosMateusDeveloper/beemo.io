# Verificação de segurança da aplicação

Os testes Java de segurança e o teste do build frontend podem ser executados localmente. A publicação do workflow de CI, que também executará os testes com PostgreSQL descartável e a varredura de dependências, está pendente da permissão `workflow` na credencial GitHub. Quando publicado, deve rodar em pull requests, pushes em `main` e semanalmente. Vulnerabilidade **crítica** deverá fazer a execução falhar; qualquer exceção precisa de justificativa em revisão, prazo e acompanhamento.

## O que os testes verificam

- Autenticação, CSRF, CORS, cookies e cabeçalhos de segurança nas respostas da API.
- Permissões por perfil e recusa de acesso sem vínculo com a clínica.
- RLS com duas clínicas em PostgreSQL real: leitura, atualização, inserção explícita em outro tenant e ausência de contexto.
- Recusa no boot de uma role de banco com `SUPERUSER` ou `BYPASSRLS`.
- Importação CSV limitada a 5 MB e 20 mil linhas, com tipo, extensão, UTF-8 e conteúdo textual verificados.
- Erros de importação e falhas de auditoria sem repetição de valores sensíveis em mensagem ou log.
- Build de produção sem a rota de pacientes de demonstração nem seus dados fictícios no pacote JavaScript.

## Checklist antes de ativar um ambiente de produção

1. Confirmar que o workflow de segurança foi publicado e passou no commit implantado, sem exceção de vulnerabilidade crítica sem justificativa registrada.
2. Iniciar com `SPRING_PROFILES_ACTIVE=production` e confirmar que a configuração obrigatória passa pela validação de boot. Confirmar `AUTH_SECURE_COOKIES=true`, origens HTTPS explícitas em `CORS_ALLOWED_ORIGINS` e nenhuma origem curinga.
3. Fazer smoke test de login, logout, cookie `Secure`/`HttpOnly`/`SameSite`, CSRF, CORS permitido e CORS recusado. Confirmar `X-Content-Type-Options`, `X-Frame-Options` e `Referrer-Policy` nas respostas.
4. Confirmar que nenhuma rota, flag ou dado de demonstração está habilitado no frontend ou na API publicada.
5. Validar com duas clínicas de teste que um usuário de cada perfil só acessa seus recursos e que as tentativas proibidas são auditadas. Usar contas e dados fictícios.
6. Enviar CSV válido e arquivos com extensão, tipo, tamanho, codificação ou conteúdo inválidos; confirmar recusa e ausência do conteúdo enviado nos logs e erros.
7. Registrar commit, ambiente, data, operador e resultado de cada verificação. Guardar evidências sem dados de pacientes ou segredos.

Os testes automatizados não substituem o smoke test no ambiente implantado, pois proxy, TLS, cookies e configuração do frontend dependem desse ambiente.
