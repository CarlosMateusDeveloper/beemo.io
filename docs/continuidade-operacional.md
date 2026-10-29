# Continuidade operacional e exportação segura

O [runbook de produção](runbooks/operacao-producao.md) vincula deploy, rollback, incidente e rotação aos testes desta página. O [inventário privado](runbooks/inventario-producao.template.md) deve conter responsável e próxima data de restauração; o [registro operacional](runbooks/registro-operacional.template.md) guarda a evidência. Ainda não há VPS nem ensaio real, então esses campos permanecem pendentes.

Esta é a referência operacional da issue #35. Ela separa o que o aplicativo controla do que precisa ser configurado na infraestrutura de produção.

## Objetivos iniciais

| Medida | Objetivo inicial | Como verificar |
| --- | --- | --- |
| Disponibilidade da API | 99,5% mensal, após medição real | monitor sintético autenticado e alerta para o responsável de plantão |
| RPO do banco | até 24 horas | último backup íntegro e registrado |
| RTO do serviço | até 8 horas | restauração isolada cronometrada |
| Exportação solicitada no produto | até 24 horas | status da solicitação e trilha de auditoria |

Esses valores são objetivos operacionais iniciais, não promessas comerciais. Devem ser revisados após a primeira restauração completa em produção.

## Exportação por clínica

Somente quem possui a permissão `dados.exportar` pode solicitar uma exportação em **Minha conta**. No catálogo padrão, essa permissão pertence ao administrador da clínica.

O sistema cria uma tarefa em segundo plano. A exportação inclui apenas tabelas clínicas protegidas por RLS e que possuem `id_clinica`; tabelas globais de autenticação, sessões, tokens e o próprio arquivo de exportação não entram no pacote. Cada tabela é entregue em `dados/<tabela>.ndjson` e o `manifest.json` preserva o formato, a data e as relações por identificadores.

Antes de ser armazenado, o ZIP é cifrado com AES-256-GCM. Configure uma chave aleatória de 32 bytes, em Base64, apenas no cofre de segredos:

```dotenv
EXPORT_ENCRYPTION_KEY=<base64-de-32-bytes>
```

Sem essa variável, a API recusa criar ou abrir exportações. O download exige sessão autenticada, permissão de exportação e um token aleatório de uso temporário; no banco permanece apenas o hash desse token. O link expira em 24 horas e pode ser revogado, removendo o conteúdo cifrado. Solicitação, download e revogação entram no log de auditoria.

Arquivos acima de 50 MB falham de modo explícito para evitar esgotar recursos. Para volume maior, a equipe deve realizar exportação assistida com armazenamento temporário cifrado, limite de recursos e o mesmo registro de auditoria.

## Backup e restauração em produção

O provedor de infraestrutura deve executar backup cifrado do PostgreSQL e dos anexos privados fora do servidor principal. A rotina deve ter retenção documentada, conta com privilégio mínimo e alerta quando o backup não ocorrer no prazo. Não copie backup produtivo para desenvolvimento ou homologação sem anonimização e autorização.

Uma restauração deve ser testada em ambiente isolado pelo menos trimestralmente:

1. Registrar o início, o conjunto de backup e o responsável.
2. Restaurar banco e anexos em rede isolada, usando credenciais sem `SUPERUSER` e sem `BYPASSRLS`.
3. Rodar as migrações e iniciar a API; a verificação `TenantDatabaseSafety` deve aceitar o banco.
4. Conferir amostras de relacionamentos, anexos, auditoria, isolamento entre duas clínicas e a capacidade de gerar uma exportação.
5. Medir tempo de recuperação e instante efetivo do último dado restaurado; comparar com RTO e RPO.
6. Destruir o ambiente temporário, registrar resultado, falhas e a data da próxima simulação.

Em incidente, interrompa gravações inseguras, preserve os logs, comunique a clínica afetada pelo canal de suporte, restaure em ambiente isolado antes de apontar produção e somente retorne após validação funcional e de isolamento. Rollback de implantação deve seguir o mesmo registro, sem apagar dados criados depois do último backup.
