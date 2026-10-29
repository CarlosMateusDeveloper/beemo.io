# Observabilidade de produção

A [sonda de produção](../scripts/production_monitor.py) executa verificações **sem autenticar usuários e sem ler conteúdo clínico**. A verificação pública deve rodar em uma máquina independente da VPS, a cada minuto; a opção `--host` roda na VPS com acesso restrito ao Docker. Ainda não existe VPS nem responsável/canal de plantão configurado: a implementação pode ser testada localmente, mas os alertas reais e a resposta operacional da #64 continuam pendentes.

## Configuração dos dois monitores

Use Python 3.10 ou superior. Guarde variáveis em arquivo privado, com permissão `0600`, fora do Git. URLs de webhook podem conter token no caminho/query e nunca devem aparecer em logs ou tickets. Defina canais de **pessoas distintas** para o plantão e para o escalonamento; configure o webhook para aceitar o JSON com campo `text`.

| Variável | Exemplo ou finalidade |
| --- | --- |
| `MONITOR_FRONTEND_URL` | `https://app.exemplo.com.br`, origem sem caminho/query |
| `MONITOR_API_URL` | `https://api.exemplo.com.br`, origem sem caminho/query |
| `MONITOR_WEBHOOK_URL` | webhook HTTPS privado do plantão |
| `MONITOR_ESCALATION_WEBHOOK_URL` | webhook HTTPS privado do substituto/escalonamento |
| `MONITOR_OWNER` | nome ou função do responsável que recebe o alerta |
| `MONITOR_STATE_FILE` | arquivo de estado privado; **um caminho diferente por monitor** |
| `MONITOR_LATENCY_LIMIT_MS` | `1500` por padrão |
| `MONITOR_TLS_WARN_DAYS` | `14` por padrão |
| `MONITOR_TIMEOUT_SECONDS` | `5` por padrão |

Na máquina independente, execute `python3 scripts/production_monitor.py` a cada minuto por agendador supervisionado. A sonda consulta `/` no frontend e `/api/auth/providers` na API, exige HTTPS, HTTP 200 e lista JSON na API, mede latência e validade do TLS. Ela não segue como sucesso um redirecionamento para outra origem. Saída JSON contém apenas códigos de condição; falha retorna código 1, erro do monitor/entrega retorna 2. Nunca inclua cookies, credenciais ou parâmetros de paciente nas URLs.

Na VPS, use **outro** `MONITOR_STATE_FILE` e acrescente `MONITOR_DISK_PATH`, `MONITOR_BACKUP_MARKER`, `MONITOR_COMPOSE_FILE` e `MONITOR_COMPOSE_ENV_FILE` com caminhos absolutos. Execute `python3 scripts/production_monitor.py --host` a cada minuto como conta operacional autorizada a consultar o Docker. Acesso ao socket Docker equivale a privilégio elevado; limite essa conta e não publique o socket. O monitor lê somente saúde/uso dos contêineres e três contagens agregadas do PostgreSQL (conexões, itens de fila e circuitos de integração abertos). Não extrai pacientes, telefones, mensagens ou linhas da fila.

`MONITOR_BACKUP_MARKER` deve ser atualizado **somente depois** de um backup cifrado ser verificado por `scripts/clinical_backup.py verify` e copiado para armazenamento separado. Um arquivo existente sem verificação não serve de marcador. A restauração isolada continua sendo o teste definitivo da [continuidade operacional](continuidade-operacional.md).

## Limiares e resposta

| Condição | Limiar | Prioridade e primeira ação |
| --- | --- | --- |
| Frontend/API indisponível, API fora do formato ou TLS inválido | Uma execução | Crítica; conferir DNS, proxy, `docker compose ps` e saúde da API/banco |
| Latência pública | Acima de 1500 ms | Aviso; correlacionar com CPU, memória e banco |
| Certificado TLS | Menos de 14 dias | Aviso; conferir DNS, ACME e portas 80/443 |
| Disco | 85% aviso; 95% crítico | Conferir volumes, backups e logs; não apagar dados/volumes sem plano |
| Backup verificado | 20 h aviso; 26 h crítico | Acionar operador de backup; validar execução, cópia externa e restauração |
| Serviço/ PostgreSQL | Contêiner ausente, parado ou sem health `healthy` | Crítico; conferir health check e dependências |
| CPU/memória de contêiner | 90% ou mais | Aviso; verificar degradação e limites do Compose |
| Fila de mensageria | 100 itens ou mais | Aviso; seguir [runbook de mensageria](runbooks/mensageria.md) |
| Integração Meta | Um ou mais circuitos abertos | Aviso; seguir [runbook WhatsApp](runbooks/whatsapp-meta.md) |

O primeiro achado envia alerta ao plantão. A mesma condição não repete alertas a cada minuto; após cinco minutos sem recuperação, a sonda envia uma vez ao canal de escalonamento. A recuperação avisa os canais acionados. Se a entrega do webhook falhar, o estado não avança e a tentativa se repete na próxima execução. O agendador deve alertar também para falha repetida do próprio processo (código 2), usando canal independente.

## Teste de alerta e retenção

Execute `python3 -m unittest scripts.test_production_monitor` no checkout: os testes usam servidor HTTP local e simulam falha, deduplicação, escalada, recuperação e métricas, sem contactar fornecedores. Com canais reais definidos, execute `python3 scripts/production_monitor.py --test-alert` e `--test-escalation` e registre horário, destinatário, confirmação humana e tempo de resposta em evidência privada. Depois simule uma URL de API inacessível em ambiente de teste e confirme a recuperação. Não force falha em produção só para demonstrar o alerta.

O Compose limita logs locais a 10 MB × 5 arquivos por serviço usando o driver `local`; `docker compose logs` continua disponível ao operador. A API Java já escreve JSON estruturado. O chatbot omite access logs do Uvicorn e identificadores/telefones dos logs de aplicação. **Centralização externa de logs, retenção fora da VPS e auditoria completa de mascaramento ainda precisam de um destino de observabilidade e teste operacional.** Não envie logs brutos para webhook ou canais de suporte.

## Evidência necessária para fechar a #64

Registre o plantonista e substituto, URLs reais, resultado de uma falha simulada e do alerta recebido, confirmação de resposta, medição de disco/banco/fila, certificado e backup na VPS, além do destino/retenção dos logs centralizados. Até existir infraestrutura, os testes locais não comprovam detecção antes da clínica nem funcionamento do escalonamento real.
