# Runbook de produção

Este roteiro reúne [deploy Docker](../deploy-docker-production.md), [rede e HTTPS](../network-hardening.md), [segredos](../producao-segredos.md) e [continuidade](../continuidade-operacional.md). Ainda não existe servidor de produção. Os comandos estão preparados para uma VPS Linux, mas deploy, rollback e restauração só estarão validados depois de ensaio real.

Antes do primeiro deploy, preencha uma cópia **privada, fora do Git**, do [inventário](inventario-producao.template.md): operador titular e substituto, contatos, domínio, conta do provedor, cofre, backup e próxima restauração. Use uma cópia privada do [registro operacional](registro-operacional.template.md) para cada mudança ou incidente. Não grave valores de segredo, dados de pacientes, conteúdo de mensagens ou logs brutos em issues e PRs.

## Responsáveis

| Função | Decisão e ação |
| --- | --- |
| Operador de plantão | Recebe alertas, coordena mudança/incidente e aciona substituto. |
| Operador de banco/backup | Confere backup, executa migrations e ensaia restauração isolada. |
| Responsável pelo produto | Aprova janela, rollback incompatível e comunicação à clínica. |
| Responsável por privacidade | Avalia suspeita de acesso ou exposição de dados. |

Uma pessoa pode acumular funções, mas o inventário precisa nomear titular e substituto. O monitor da #64 definirá os sinais e a escalada quando estiver instalado. Sem plantão e contato testados, não declare o ambiente pronto para operação.

## Checklist antes do deploy

1. Registre motivo, revisão Git aprovada, `APP_VERSION` atual e nova, operador, aprovador, janela e plano de retorno. Confira o inventário de DNS, VPS, OAuth, e-mail/Resend, WhatsApp e backup.
2. Confirme duas sessões independentes por chave SSH, uma do titular e outra do substituto. Verifique espaço em disco, certificados, contêineres, alertas e último backup verificado. Suspenda a mudança se banco, backup ou isolamento estiverem degradados.
3. Produza backup cifrado de banco **e anexos**, registre identificador, checksum e instante do último dado. Confirme que existe procedimento de restauração e chave recuperável em cofre separado. Criar arquivo não comprova recuperação.
4. Revise migrations com o responsável de banco: ordem, dono das tabelas, bloqueios, duração e compatibilidade com a versão anterior. O perfil `production` usa `ddl-auto=validate`; migrations não rodam no boot. Não use `database/schema_clinica.sql`, que contém dados de demonstração.
5. Configure `.env.production` fora do Git com permissão `0600`. Confira HTTPS, callbacks OAuth, `APP_WEBSOCKET_URL`, `ACME_EMAIL`, chaves de cifragem/exportação e `APP_VERSION`. Não imprima a configuração expandida em tickets ou logs.

Na raiz do checkout aprovado, em Bash na VPS:

```bash
dc() { docker compose --env-file .env.production -f docker-compose.production.yml "$@"; }
dc config -q
dc ps
dc build
dc run --rm --no-deps proxy caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
```

Para servidor novo, siga primeiro a [restauração e concessão de permissões](../deploy-docker-production.md). Para atualização, aplique migrations aprovadas com a identidade de manutenção, confirme o schema e registre horários/resultado. A role da aplicação não deve ter `SUPERUSER` nem `BYPASSRLS`.

## Aplicar e conferir

Execute `dc up -d --no-build` e `dc ps`. Não encerre a mudança se algum serviço não alcançar health `healthy`. Use apenas contas de teste autorizadas e, para isolamento, duas clínicas de teste. Registre hora, operador e resultado de cada item:

| Smoke test | Resultado esperado |
| --- | --- |
| HTTP, HTTPS, TLS e cabeçalhos | HTTP redireciona; certificado válido; HSTS/CSP presentes. |
| Superfície de rede | Só proxy em 80/443; banco/API sem porta direta; `/actuator/health` no domínio público da API retorna 404. |
| Autenticação | Login, logout, magic link e OAuth configurados funcionam sem erro de callback. |
| Permissões | Usuário sem permissão recebe 403; sessão de outra clínica não lê nem altera o dado de teste. |
| Agenda e comunicação | Agenda, WebSocket, chatbot e WhatsApp de teste autorizado funcionam; fila sem acúmulo inesperado. |
| Exportação | Responsável autorizado solicita/baixa exportação de teste; pessoa sem permissão não; auditoria e link temporário conferidos. |
| Operação | API, banco, disco, backup e alertas saudáveis; nenhuma falha nova recorrente. |

Marque integrações não configuradas como **não testadas**, não como aprovadas. Acompanhe erros e latência por pelo menos 30 minutos após a mudança e registre revisão e versão efetivas. O responsável pelo produto encerra a janela após conferir evidências.

## Diagnóstico e rollback

Registre sintomas, horário, versão e evidência redigida. Consulte `dc ps` e, em sessão restrita, `dc logs --tail=100 <servico>`; não copie logs com conteúdo sensível para canais públicos.

Se o schema ainda for compatível, volte ao commit e `APP_VERSION` anteriores e execute `dc up -d --no-build`; preserve volumes. Repita os smoke tests e registre indisponibilidade. Se a migration for incompatível, **não** troque só a imagem: interrompa gravações inseguras, avalie perda de dados com o responsável pelo produto e siga [restauração isolada](../continuidade-operacional.md) de banco e anexos do mesmo ponto. Só retorne tráfego após integridade, isolamento e funções críticas aprovados. Não prometa RTO/RPO sem medição real.

## Incidente e comunicação

Classifique **P1** quando atendimento, integridade, isolamento ou acesso a dados estiverem afetados; **P2** para degradação sem risco imediato. O plantonista registra fatos separados de hipóteses, horário de detecção, impacto e próximos passos. Para P1, acione imediatamente responsável pelo produto e operador de banco; acione privacidade se houver suspeita de exposição. Para P2, atribua dono e prazo.

Contenha apenas o fluxo inseguro, preserve logs e backups e identifique o último dado confiável. Acesso emergencial usa console do provedor ou SSH por chave de usuário não root com `sudo`; registre pessoa, motivo, horário, comandos relevantes e revogação. Não habilite SSH por senha/root como atalho.

O responsável pelo produto aprova a mensagem externa. Modelo inicial: “Detectamos indisponibilidade em [função] desde [hora/fuso]. O impacto confirmado é [impacto]. Estamos trabalhando na recuperação e atualizaremos até [hora].” Atualize só com fatos verificados, alternativa operacional e próximo horário. Não inclua pacientes nem causa ainda não confirmada. Após recuperar, registre linha do tempo, causa, impacto, RTO/RPO observados, evidências redigidas e ações preventivas no [registro](registro-operacional.template.md).

## Rotação de segredos

Registre a mudança, dependências, janela, aprovador e rollback. Gere novo valor no cofre, atualize serviços, teste e só então revogue o antigo. Nunca inclua valores em Git, chat ou tickets.

- **Banco, Resend, OAuth e WhatsApp:** coordene fornecedor e `.env.production`; reinicie serviços necessários, teste conexão/login/envio e revogue a credencial antiga depois.
- **JWT:** planeje reautenticação e confirme login/logout; não presuma compatibilidade de sessões antigas.
- **Cifragem clínica/exportação:** siga [cifragem clínica](../clinical-encryption.md). Mantenha versões de chave necessárias para leitura e backups até migração ou expiração comprovada.
- **Backup:** retenha a chave antiga enquanto houver arquivos cifrados com ela. Produza, verifique e restaure um backup com a nova chave antes de descartá-la.

No registro, anote apenas identificador/versão da chave, sistemas afetados, teste, aprovador e horário de revogação.

## Restauração recorrente

O operador de banco executa ensaio isolado ao menos trimestral conforme a [#35](../continuidade-operacional.md), medindo duração e instante do último dado restaurado. Confirme banco, anexos, relacionamentos, auditoria, isolamento de duas clínicas e exportação. Registre resultado, falhas, responsável e próxima data no inventário privado. Até existir servidor, backup real e pessoa nomeada, esta obrigação permanece pendente.
