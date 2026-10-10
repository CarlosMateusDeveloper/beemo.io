# API ClinicOS

Java 21, Spring Boot 4.1, Spring MVC, Spring Security, JPA/Hibernate e PostgreSQL.
Controllers recebem DTOs, services aplicam regras/agregações, repositories e EntityManager acessam o banco.

## Execução

No diretório `backend`, copie [`.env.example`](.env.example) para `.env` (não versionado)
e substitua os valores de exemplo:

```dotenv
DB_HOST=localhost
DB_PORT=5432
DB_NAME=clinica
DB_USER=seu_usuario
DB_PASSWORD=sua_senha
JWT_SECRET=gere_um_segredo_aleatorio_com_pelo_menos_32_bytes
JWT_EXPIRATION_MINUTES=30
APP_TIME_ZONE=America/Fortaleza
CORS_ALLOWED_ORIGINS=http://localhost:5173,http://127.0.0.1:5173
```

Em produção use HTTPS e `AUTH_SECURE_COOKIES=true`. O pool Hikari tem limite padrão de 10 conexões,
configurável por `DB_POOL_MAX_SIZE`, com espera máxima de 30 segundos por conexão.
Aplique as migrações do banco na ordem; a 014 adiciona os índices do dashboard e depende da 012/013.
Confira o procedimento de provisionamento do banco em `database/` antes de usar dados existentes.

```powershell
.\mvnw.cmd spring-boot:run
```

As rotas de autenticação e dashboard aceitam `/api/v1`. Os aliases antigos `/api/auth`
e `/api/dashboard` continuam funcionando para os clientes atuais. As demais APIs ainda
usam seus caminhos existentes; esta mudança não migra todo o backend.

## Autenticação (#7)

- `GET /api/v1/auth/csrf`: obtenha o cookie CSRF e envie o campo `token` no cabeçalho
  `X-CSRF-TOKEN` nas requisições de escrita, inclusive login/logout.
- `POST /api/v1/auth/login`: JSON `{"email":"conta@example.com","senha":"..."}`.
  Compatibilidade com contas que possuem senha. A interface continua usando magic-link/SSO.
- Sucesso: HTTP 200 com `usuario` e cookie JWT `clinicos_session`, HttpOnly, SameSite=Lax,
  válido por 30 minutos (ou `JWT_EXPIRATION_MINUTES`). O token não é exposto no JSON.
  Clientes autenticados também podem enviar `Authorization: Bearer <token>`.
- Senha em Argon2id; hashes BCrypt legados migram após uma autenticação válida.
  E-mail ausente e senha errada recebem o mesmo HTTP 401 e mensagem genérica.
- JWT contém apenas `sub`, `jti`, `iss`, `aud`, `iat`, `exp`. Nenhuma senha, CPF ou e-mail.
  Além da assinatura/validade, cada acesso verifica a sessão ativa no banco.
- `POST /api/v1/auth/logout`: revoga a sessão no banco e remove o cookie; mesmo um token
  copiado antes do logout deixa de funcionar. `GET /api/v1/auth/me` retorna a identidade atual.
- Login: limite de 15 tentativas por e-mail e 50 por IP, em janelas de 15 minutos.
  Excesso retorna 429. O limitador atual é por instância; múltiplas réplicas exigem
  um limitador compartilhado no gateway ou Redis.
- Rotas privadas exigem sessão válida; falta/expiração/adulteração/revogação retornam 401.
  A autorização usa o perfil no tenant: `/api/usuarios/**` exige administrador,
  enquanto médico pode consultar o dashboard. Não há permissão global inferida pelo e-mail.
- Uma identidade nova recebe um tenant próprio; identidades que já possuem vínculo continuam
  no tenant existente. Assim, autenticar qualquer e-mail nunca concede acesso aos dados da clínica legada.
- Magic-link e Google/Microsoft continuam em funcionamento; detalhes em
  [configurar-acesso-sem-senha.md](../docs/specs/configurar-acesso-sem-senha.md).

Erros de autenticação e validação incluem `error.code` e `error.message`; `message`
no nível superior é mantido para compatibilidade. Rejeições do middleware usam o mesmo
envelope, preservando os códigos de segurança existentes. Todos incluem `X-Request-ID` na resposta.

## Dashboard (#10)

`POST /api/v1/dashboard`, autenticado, somente leitura:

```json
{
  "periodo": "Personalizado",
  "profissionalId": null,
  "dataInicio": "2026-10-01",
  "dataFim": "2026-10-08"
}
```

Períodos: `Hoje`, `7 dias` (hoje e os seis anteriores), `Mês` (mês calendário completo),
ou `Personalizado`. Corpo ausente usa Mês. Datas são inclusivas e seguem `APP_TIME_ZONE`.
Personalizado exige início/fim válidos e no máximo 366 dias; profissional deve ser positivo.
Filtro/JSON inválido retorna 400 com `error: {code, message}`, `message` e `requestId`.

| Campo | Regra |
| --- | --- |
| totalConsultas | Consultas com agenda no intervalo, incluindo canceladas/faltas |
| faturamento | Soma das faturas vinculadas a essas consultas; receita faturada, não pagamento recebido |
| ocupacao | Slots diferentes de Livre / todos os slots do intervalo |
| noShow | Faltou / (Realizada + Faltou), com percentual arredondado a uma casa |
| novosRetornos | Pacientes distintos; novos quando a primeira consulta agendada está no período |
| ranking | Até 5 profissionais por faturamento; desempate por ID e mesma regra de no-show do total |
| pagador | Receita por vínculo atual do paciente com convênio/particular e tipo de atendimento |
| serieTemporal | Receita, consultas não canceladas/faltosas, cancelamentos e faltas por dia; Mês agrupa dias 1–7, 8–14 etc. |
| hoje | Consultas de hoje exceto canceladas/faltas, fila Em Espera e até 5 próximas consultas |

O filtro de profissional se aplica a todos os blocos. O bloco `hoje` é independente do
intervalo histórico. Dias sem movimento recebem zeros. Sem consultas nem slots, `empty=true`.
Consultas sem fatura não geram receita. Histórico de convênio não é armazenado: a
classificação do pagador usa o cadastro atual, como no painel existente.

As agregações ocorrem no PostgreSQL sob as políticas RLS do tenant. O retorno não lista
todos os atendimentos/pacientes: ranking e próximas consultas têm LIMIT 5 no SQL,
a série tem no máximo 366 pontos e o mix tem os tipos enumerados de consulta.
A migração 014 cobre tenant + data + profissional e paciente + agenda.
O UNIQUE de fatura por consulta impede multiplicar contagens/receita nos joins.

## Observabilidade

Logs JSON (Logstash), com `requestId` no MDC. `X-Request-ID` aceita até 64 caracteres
alfanuméricos, hífen e underscore; valores inválidos são substituídos por UUID.
O registro HTTP contém método, padrão da rota, status e duração; não contém query string,
URL com IDs, corpo, cookies, tokens, senhas ou dados de paciente. SQL/bind logging desativado.

Spring Boot Actuator/Micrometer registra `http.server.requests` com método, rota normalizada,
status e resultado; COUNT e TOTAL_TIME permitem volume, média e taxa de erros por rota.
Histogramas estão habilitados. Métricas são internas, sem endpoints HTTP publicados.
Para inspeção local via JMX, use `spring.jmx.enabled=true` e
`management.endpoints.jmx.exposure.include=metrics`; conecte ao processo local.
Em múltiplas instâncias configure um registry/exporter privado conforme a infraestrutura.

Referências: [logs estruturados](https://docs.spring.io/spring-boot/4.0/reference/features/logging.html)
e [métricas HTTP](https://docs.spring.io/spring-boot/4.0/reference/actuator/metrics.html).

## Verificação

```powershell
.\mvnw.cmd "-Dtest=AuthSecurityTest,PasswordSecurityTest,ApiSecurityContractTest,DashboardServiceTest,RequestLoggingFilterTest" test
```

`DashboardDatabaseTest` executa as consultas reais em um schema descartável. Configure
`DASHBOARD_TEST_DB_URL`, `DASHBOARD_TEST_DB_USER` e `DASHBOARD_TEST_DB_PASSWORD` em um
PostgreSQL de teste; a role deve poder criar schemas, sem SUPERUSER ou BYPASSRLS.
O teste cobre agregações, filtros inclusivos, isolamento RLS, limites e consulta somente leitura.
Sem a variável de conexão ele fica explicitamente ignorado.

`AuthIntegrationTest` é a suíte existente com banco completo, habilitada por
`RUN_AUTH_INTEGRATION=true`. Ela usa as configurações da aplicação e requer todas as migrações.
