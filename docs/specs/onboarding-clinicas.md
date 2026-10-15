# Onboarding e migração de clínicas

O assistente administrativo em `/onboarding` reúne seis verificações: responsável, agenda, importação, canais, política de consentimento e primeiro fluxo. O progresso pertence ao tenant e apenas quem possui `integracao.gerenciar` pode consultar ou alterar a implantação.

## Migração segura

O CSV representa a base mínima necessária para o pós-venda: paciente, contato, profissional, especialidade, procedimento, horário e situação do atendimento. A pré-validação não grava dados. A equipe pode corrigir o mapeamento de colunas e situações e revalidar o mesmo arquivo dentro do assistente. Linhas inválidas mostram campo e motivo.

Ao confirmar, o importador conserva o nome e o SHA-256 do arquivo, a origem, o responsável, o horário e o resultado de cada linha. O original é armazenado somente em forma criptografada. Identificadores externos tornam a reexecução idempotente.

Três formatos anonimizados ficam em `docs/examples/onboarding`. Eles cobrem separadores, cabeçalhos e nomenclaturas distintos.

## Consentimento e rollback

Ausência de evidência cria bloqueio técnico para comunicações não essenciais. Campanhas continuam impedidas até haver consentimento válido.

Cada importação guarda autoria, relatório, SHA-256 e o CSV original criptografado com AES-256-GCM. Configure `IMPORT_FILE_ENCRYPTION_KEY` com 32 bytes aleatórios em Base64; gere um valor com `node -e "console.log(require('node:crypto').randomBytes(32).toString('base64'))"`. A chave deve permanecer no gerenciador de segredos e nunca no banco ou no Git. A importação é recusada quando a chave não está configurada. A pré-validação continua disponível porque não grava o arquivo.

O rollback remove somente atendimentos criados pela importação que não receberam trabalho posterior. Casos acompanhados e registros preexistentes atualizados são preservados e aparecem no relatório de correção assistida. Toda execução registra autor, data, status e relatório; não é necessário alterar o banco manualmente.
