# WhatsApp Business no Clinisis

O Clinisis usa a API oficial WhatsApp Cloud da Meta. Cada clínica possui sua própria configuração, número e URL de webhook. Tokens e App Secrets são criptografados antes de entrar no banco e nunca são devolvidos à interface.

## Preparação

1. Aplique `database/migrations/024_whatsapp_bidirecional.sql` depois da migração 023.
2. Gere a chave mestra usada pelo chatbot:

   ```powershell
   python -c "import base64,secrets; print(base64.urlsafe_b64encode(secrets.token_bytes(32)).decode())"
   ```

3. Salve o resultado como `WHATSAPP_CREDENTIAL_ENCRYPTION_KEY` no ambiente do chatbot. Guarde a chave no gerenciador de segredos da infraestrutura; perder a chave impede a leitura das credenciais já armazenadas.
4. Reinicie o chatbot.

## Configuração na Meta

1. No Clinisis, abra **WhatsApp > Configuração**.
2. Informe o ID do número, o ID da conta empresarial, o token de acesso permanente, o App Secret e um token de verificação criado pela clínica.
3. Salve e copie a URL de webhook exibida. Em produção ela deve usar a origem HTTPS pública do chatbot; `localhost` serve apenas para desenvolvimento com um túnel HTTPS.
4. No painel do app da Meta, cadastre essa URL e o mesmo token de verificação.
5. Assine o campo `messages` da conta WhatsApp Business.

O endpoint valida a assinatura `X-Hub-Signature-256`, o número receptor e a chave aleatória da URL. Eventos repetidos são idempotentes.

## Sandbox e número de teste

No painel **WhatsApp > Configuração da API** da Meta, use o número de teste e adicione os destinatários permitidos. Envie primeiro uma mensagem do telefone de teste ao número da Meta para abrir a janela de 24 horas. Depois:

- abra uma oportunidade no Clinisis;
- selecione um modelo aprovado e envie;
- confira a fila em **WhatsApp > Operação**;
- responda pelo telefone e confirme que o caso entra em **Respostas**, com tarefa para o responsável;
- confira os estados enviada, entregue e lida na conversa.

Fora da janela de 24 horas, o Clinisis envia o identificador do template aprovado na Meta. O cadastro de modelos e o consentimento do paciente continuam obrigatórios para comunicação de pós-venda.

## Diagnóstico

- `403 Assinatura inválida`: confirme o App Secret e se o proxy preserva o corpo bruto.
- `403 Número receptor não autorizado`: o ID do número do payload difere do configurado para a clínica.
- mensagem na fila de falhas: abra **WhatsApp > Operação** para ver código, erro e reprocessar.
- resposta sem vínculo: há mais de um caso aberto para o mesmo telefone; o evento fica em `whatsapp_associacao_pendente` para revisão, sem escolher o caso incorreto.
