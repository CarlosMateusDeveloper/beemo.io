# Painel interno do Clinisis

O painel fica em `/operacao` e só aceita funcionários registrados em `auth_saas_funcionario`. Uma pessoa da clínica, mesmo administradora do tenant, não recebe acesso interno automaticamente.

O primeiro administrador interno deve ser provisionado no deploy, depois de entrar no produto uma vez:

```sql
INSERT INTO auth_saas_funcionario(id_usuario,papel)
SELECT id,'administrador' FROM usuario WHERE lower(email)=lower('fundador@empresa.com');
```

Depois disso, administradores internos podem gerenciar os demais funcionários pela API. A sessão precisa ter sido autenticada novamente nas últimas oito horas para contas que exigem reautenticação.

O painel usa apenas projeções agregadas: etapa de onboarding, estado de integração, chamados abertos, última atividade auditada e número de ações no período. Não há acesso a prontuários, pacientes, payloads de integração ou conteúdo de conversas. Plano, pagamento e consumo aparecem como indisponíveis até a implementação das issues #31 e #32.
