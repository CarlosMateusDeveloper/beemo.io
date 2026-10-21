# Pacotes de configuração por especialidade

O módulo `/configuracoes/especialidades` mantém pacotes versionados dentro do tenant. A primeira versão criada automaticamente contém apenas estrutura operacional de pós-atendimento e permanece `EM_REVISAO`; ela não representa protocolo clínico.

## Ciclo de vida

1. Uma versão nasce em revisão com modelos de atendimento, documentos, formulários e fluxos.
2. A publicação exige um membro ativo com papel `medico` e o conselho/registro informado.
3. A clínica seleciona componentes, compara as diferenças e aprova a aplicação.
4. Componentes reaplicados usam a chave estável e não são duplicados.
5. Uma atualização muda a configuração base. Se a clínica personalizou o componente, a configuração local permanece intacta e a prévia sinaliza a diferença.
6. Desativar ou reverter apenas retira os componentes de operação. Versões, aplicações e eventos permanecem no histórico.

Fluxos de acompanhamento exigem a confirmação da política de comunicação registrada no onboarding e aprovação explícita na aplicação. Modelos de atendimento são validados contra a janela de agenda da clínica antes de serem aplicados.

## Endpoints

- `GET /api/configuracoes/especialidades/pacotes`
- `GET /api/configuracoes/especialidades/revisores`
- `POST /api/configuracoes/especialidades/pacotes/{id}/versoes`
- `PUT /api/configuracoes/especialidades/versoes/{id}`
- `POST /api/configuracoes/especialidades/versoes/{id}/revisao`
- `POST /api/configuracoes/especialidades/versoes/{id}/publicar`
- `GET /api/configuracoes/especialidades/versoes/{id}/preview?componente=...`
- `POST /api/configuracoes/especialidades/versoes/{id}/aplicar`
- `PUT /api/configuracoes/especialidades/aplicacoes/{id}/componentes/{chave}`
- `POST /api/configuracoes/especialidades/aplicacoes/{id}/desativar`
- `POST /api/configuracoes/especialidades/aplicacoes/{id}/reverter`

Todas as tabelas operacionais usam RLS forçada por `app.tenant_id`, controle de concorrência na aplicação e trilha de eventos.
