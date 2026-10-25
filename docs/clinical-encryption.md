# Criptografia dos textos clínicos (issue #67)

O backend cifra os nove campos narrativos de `prontuario` e `documento_clinico.texto` com AES-256-GCM. O banco guarda um envelope `enc:<versão>:<nonce>:<cifra+tag>` no próprio campo `TEXT`. Cada operação autentica como AAD o ID da clínica, a tabela, o campo e o ID do registro. Trocar uma cifra de clínica, campo ou registro causa falha de leitura. Não há índice sobre estes textos, nem cópia para busca textual.

## Chaves

- Gere 32 bytes aleatórios com um gerador criptográfico, codifique em Base64 e guarde em um gestor de segredos fora do repositório, da imagem da aplicação e do backup do banco. Exemplo em PowerShell: `[Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32))`.
- Configure `DATA_ENCRYPTION_KEY_VERSION=v1` e `DATA_ENCRYPTION_KEY=<base64>`. A chave é distinta de `JWT_SECRET`, da senha do banco e das chaves do provedor de WhatsApp. Não use um valor de exemplo em produção.
- `DATA_ENCRYPTION_PREVIOUS_KEYS` aceita `v1:<base64>,v2:<base64>` para leitura durante rotação. Mantenha chaves antigas até que todos os valores e backups que precisam ser recuperados tenham sido recifrados ou expirado sua retenção. Nunca reutilize um número de versão com outra chave.
- A ausência da chave necessária causa falha fechada ao ler ou escrever texto cifrado. As exceções e o progresso da migração não incluem texto, chave, nonce ou conteúdo cifrado.
- Com o perfil `prod` ou `production`, a API recusa iniciar se a chave ativa estiver ausente ou se a leitura de valores legados estiver habilitada.
- Guarde cópia de recuperação das chaves em cofre separado com acesso restrito e teste a restauração de um backup em ambiente isolado. Perder a chave torna os textos irrecuperáveis.

## Implantação e migração legada

1. Pare todos os escritores antigos. Faça backup consistente **e cifrado** do PostgreSQL, incluindo a versão da aplicação, e teste uma restauração. O backup não deve incluir `.env` nem o cofre de chaves.
2. Aplique primeiro `database/migrations/031_prontuario_conteudo_cifrado.sql` e depois `database/migrations/032_clinical_encryption_progress.sql` com o procedimento usual de migração. Configure a chave nova no ambiente do backend.
3. Execute o backend novo sem listener HTTP, na raiz `backend`, por exemplo:

   ```powershell
   .\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--spring.profiles.active=clinical-migration --spring.main.web-application-type=none --spring.jpa.hibernate.ddl-auto=none --app.clinical-encryption.migrate=true"
   ```

   O processo percorre clínicas e registros em lotes de 50. Cada lote é uma transação; para cada valor legado, cifra e valida uma leitura autenticada **antes** de substituir o texto original. Uma falha reverte o lote. Reexecutar o comando continua de onde parou; campos já cifrados são ignorados. `clinical_encryption_migration` guarda somente contadores por clínica e tabela. O console imprime somente clínica, tabela, lote e quantidade.
4. Confira que não restam valores em claro, por clínica, para todos os nove campos e `documento_clinico.texto`. Confira também uma leitura autorizada de prontuário e documento, rascunho e registro finalizado, com caracteres acentuados. Não coloque texto clínico nos comandos de auditoria nem em tickets.
5. Inicie o backend para tráfego com `DATA_ENCRYPTION_ALLOW_LEGACY_READ=false` (padrão). A opção `true` é apenas uma janela de transição explicitamente aprovada quando a migração ainda está em curso; não é a configuração final.

O formato lateral introduzido pela migração 031 continua legível. O job copia seu conteúdo para envelopes por campo, autentica a leitura e mantém a cópia lateral cifrada para recuperação. Enquanto houver cópias laterais, mantenha a chave correspondente; em uma rotação, `DATA_ENCRYPTION_KEYS=1:<chave-v1>` permite que o leitor legado abra a versão 1 mesmo quando `DATA_ENCRYPTION_KEY` ativa já for v2. `DATA_ENCRYPTION_LEGACY_ACTIVE_VERSION` fica em 1 para o formato lateral. A remoção dessas cópias requer uma operação separada de retenção e recuperação.

Para voltar à versão antiga da aplicação, restaure o **backup completo anterior à migração** antes de subir o binário antigo. O código antigo não entende o envelope cifrado. Não tente remover o prefixo `enc:` ou descriptografar manualmente em SQL.

## Rotação

1. Gere uma nova chave, avance a versão (por exemplo, `v2`) e mantenha a chave anterior em `DATA_ENCRYPTION_PREVIOUS_KEYS`.
2. Implante o backend novo: ele grava com `v2` e ainda lê `v1`.
3. Faça backup cifrado, interrompa escritores e rode o mesmo comando de migração com `--app.clinical-encryption.rotate=true` acrescentado aos argumentos. O job recifra valores antigos em lotes e registra progresso. Pode ser reiniciado.
4. Verifique que não há envelopes com versões anteriores nos campos do escopo. Só remova a chave antiga depois de avaliar também os backups históricos que precisam ser restauráveis.

## Exportação e recuperação

Backups físicos e lógicos do banco contêm ciphertext, mas ainda exigem criptografia do próprio arquivo, controle de acesso e retenção. A chave não deve ir junto do backup. A exportação clínica da aplicação exige a permissão dados.exportar, decifra os textos narrativos para o arquivo autorizado e cifra o ZIP gerado com uma chave própria. O token de download expira em 24 horas e pode ser revogado. O ZIP baixado contém dados em claro e deve ser tratado como informação clínica sensível. Esta mudança não amplia o escopo para anexos, textos de outras tabelas nem conteúdo de mensagens.

### Backup lógico cifrado

O utilitário `scripts/clinical_backup.py` usa `pg_dump -Fc` e cifra o fluxo com AES-256-GCM. Não grava o dump em claro no disco. Requer Python com `cryptography` e os clientes PostgreSQL. Configure `PGHOST`, `PGPORT`, `PGUSER`, `PGDATABASE` e `PGPASSFILE` (ou `PGPASSWORD`) no ambiente; forneça `BACKUP_ENCRYPTION_KEY` com **outra** chave aleatória de 32 bytes em Base64, vinda do cofre. Não use `DATA_ENCRYPTION_KEY` para o arquivo de backup.

Na raiz do repositório:

```powershell
python scripts/clinical_backup.py backup C:\backups\clinisis.dump.aes --pg-dump pg_dump
python scripts/clinical_backup.py verify C:\backups\clinisis.dump.aes --pg-restore pg_restore
```

`verify` autentica o arquivo inteiro e confere a estrutura do arquivo PostgreSQL, sem criar dump em claro. Para validar recuperação operacional, restaure também em um banco isolado e confira contagens, relacionamentos e leitura com a versão correta de `DATA_ENCRYPTION_KEY`. Guarde backup e chaves em locais distintos, com retenção e cópia fora da máquina da aplicação. Não envie `.env` junto do backup.

O teste `ClinicalEncryptionDatabaseTest` exige um banco descartável restaurado e migrado. Ele só roda quando `CLINICAL_TEST_DB_NAME` tem o prefixo `clinisis_issue67_validation_` e `DB_NAME` aponta para esse mesmo banco. O teste confere leitura de prontuário finalizado, criação e edição de rascunho, documento clínico, texto acentuado, cifra no banco e isolamento entre clínicas; suas gravações são revertidas.

A exportação da issue #35 já está presente. Esta mudança faz os campos narrativos de prontuário e documento clínico saírem em claro dentro do ZIP autorizado, sem expor o envelope cifrado do banco. O teste ClinicalExportEncryptionTest confere a decifragem no contexto da clínica e a rejeição de um contexto incorreto. O conteúdo de outras tabelas permanece no formato anterior.
