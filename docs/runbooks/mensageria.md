# Runbook de mensageria

O chatbot grava cada envio na `mensageria_outbox` antes de responder ao navegador. O worker processa itens `pendente` e `retentativa`, sem manter locks do banco durante a chamada ao provedor.

## Estados

- `pendente`: aguardando a primeira tentativa.
- `processando`: adquirido por um worker. Locks com mais de cinco minutos são recuperados automaticamente.
- `retentativa`: falha transitória ou limite atingido; `disponivel_em` indica a próxima execução.
- `enviada`: aceita pelo provedor e ligada ao ID externo.
- `morta`: erro permanente ou máximo de tentativas; exige análise humana.
- `cancelada`: reservado para cancelamento operacional.

`mensageria_evento` é o histórico imutável de cada transição. A chave única `(id_clinica, idempotency_key)` impede que uma repetição da mesma requisição crie outro envio.

## Diagnóstico

Abra **WhatsApp > Operação**. Confira, nesta ordem:

1. alertas de fila antiga ou mensagens mortas;
2. estado e falhas consecutivas do provedor Meta;
3. código do último erro e número de tentativas;
4. horário de `disponivel_em` quando o item estiver em retentativa.

Os endpoints equivalentes são `GET /whatsapp/operacao/mensageria/resumo` e `GET /whatsapp/operacao/mensageria?status=morta`. Requerem `mensageria.visualizar`.

## Recuperação

Falhas de rede, timeout, HTTP 408, 425, 429 e 5xx usam backoff exponencial de 15 segundos até uma hora. Respostas 4xx e configuração ausente vão direto para intervenção. Cinco falhas transitórias consecutivas abrem o circuito por cinco minutos; um envio bem-sucedido fecha o circuito.

Depois de corrigir a causa, use **Reprocessar** no item morto. A ação requer `mensageria.operar`, zera as tentativas e registra o usuário em `mensageria_evento`. Nunca altere o status diretamente no banco, pois isso elimina a trilha de auditoria.

## Limites

`mensageria_limite` aceita escopos `clinica`, `numero`, `campanha` e `provedor`. A chave `*` aplica o limite a todas as chaves do escopo. Os padrões instalados são 120 tentativas por minuto por clínica, 20 por dia por número e 80 por minuto para a Meta. A reserva é serializada entre workers antes da chamada externa. Um item limitado permanece na fila e não consome tentativa.

Antes de elevar um limite, confirme a quota do provedor, a qualidade do número e o volume da campanha. Mudanças devem preservar uma margem abaixo da quota externa.

## Alertas recomendados

- `mortas > 0`: atendimento operacional.
- `idadeMaisAntigaSeg > 300`: worker parado ou provedor degradado.
- circuito diferente de `fechado`: incidente do provedor ou credencial.
- crescimento contínuo de `retentativas`: verificar rate limit e latência externa.

Em indisponibilidade prolongada da Meta, mantenha o worker ativo: o circuito evita chamadas em cascata e a fila preserva os envios. Se a fila crescer além da janela útil da comunicação, suspenda novas campanhas e decida quais itens cancelar antes da retomada.
