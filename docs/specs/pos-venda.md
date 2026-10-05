# Pós-venda de faltas

Implementação na primeira aba de `/retorno`, preservando Pendentes, Réguas e Resultados.

## Fluxo operacional

1. A recepção registra **Faltou** na Agenda. Cada consulta perdida gera um caso, inclusive faltas anteriores à migração.
2. O administrativo define responsável e próxima ação, registra motivo, canal e resultado de cada contato.
3. Após combinar um novo horário, cria uma **nova consulta** na Agenda e vincula esse agendamento no caso. A consulta original deve permanecer como falta.
4. A nova consulta deve ser do mesmo paciente, tipo e especialidade, futura e ainda não vinculada a outro caso. Sessões recorrentes preservam o controle da vaga pela equipe.
5. O caso fica em **Aguardando comparecimento**. Confirmar o agendamento não conclui o caso.
6. A Agenda registra **Realizada**: o caso passa a **Comparecimento confirmado**. Nova falta reabre o mesmo caso; cancelamento devolve à fila. Cada alteração fica no histórico.

Os gatilhos PostgreSQL acompanham alterações originadas tanto no serviço de agenda Go como no backend Java. Funcionam com a tela fechada. Uma consulta só pode pertencer a um caso; novas tentativas preservam seus vínculos anteriores. Correção da falta original antes de haver reagendamento encerra o caso como registro corrigido. Correção de uma presença vinculada atualiza a situação novamente.

## Regras e limites

- Registro manual de contatos: salvar não envia WhatsApp nem faz ligações. O provedor de mensagens não está integrado neste projeto.
- Contatos exigem responsável administrativo, resultado, observação e próxima ação futura.
- A preferência **não contatar** é compartilhada com Retorno e encerra todos os casos abertos do paciente. Novas faltas desse paciente entram encerradas; não há envio automático.
- **Não deseja reagendar** encerra somente o caso escolhido, com motivo obrigatório.
- A versão do caso protege contra edições simultâneas e reenvios com dados desatualizados.
- O autor vem da sessão autenticada quando disponível. O projeto está configurado com autenticação desabilitada; nesse modo o histórico informa operação sem usuário identificado, sem inventar um autor. A atribuição do responsável continua obrigatória.
- O vínculo é escolhido pela equipe: outra consulta futura, sem vínculo explícito, não conta como recuperação.
- Nenhuma vaga é liberada ou substituída automaticamente.
- Os indicadores mostram todo o histórico, por caso: próximos contatos, reagendados, recuperados e novas faltas. Não representam receita nem pacientes únicos. A fila tem filtros e paginação de 50 casos.
- Presença depende da atualização correta na Agenda; o sistema não presume comparecimento pelo horário decorrido.
- Horários da Agenda são interpretados no fuso da clínica: `app.clinic.time-zone`, padrão `America/Fortaleza`. Pode ser configurado por `APP_CLINIC_TIME_ZONE` no ambiente do backend. Prazos de contato usam instantes com fuso.

## Banco e API

### Fila diária e indicadores por período

Atalhos da fila: toda a fila; contatos para agora (prazo vencido ou caso sem prazo que ainda não aguarda consulta); sem responsável; conferir comparecimento (horário reagendado passado, sem resultado registrado). A conferência exige a baixa pela equipe na Agenda, sem presumir presença. Os atalhos reiniciam situação e responsável; a busca textual permanece aplicada.

A aba **Indicadores de pós-venda** aceita um intervalo inclusivo de até 366 dias, pela data da falta original. Mostra a situação atual dos casos iniciados nesse período, mesmo que os contatos e comparecimentos sejam posteriores. Exclui registros corrigidos. Conta casos, não pacientes únicos.

Métricas: casos, casos com tentativa de contato, casos que tiveram algum reagendamento, recuperados, taxa de recuperação (recuperados/casos), média de horas do horário perdido até o primeiro contato registrado, número de tentativas e pendências. Reagendamentos não representam recuperação. Casos sem contato não entram na média; ausência de base é exibida como traço. Os dez motivos mais frequentes são agrupados pelo texto informado; a distribuição por equipe usa o responsável atual, não a autoria histórica das ações.

`GET /api/pos-venda?fila=todos|hoje|sem_responsavel|conferir_agenda` mantém os filtros existentes. `GET /api/pos-venda/indicadores?inicio=YYYY-MM-DD&fim=YYYY-MM-DD` retorna resumo, motivos e equipe; padrão mês atual até hoje, no fuso da clínica.

Aplicar `database/migrations/009_pos_venda.sql` após a estrutura de Retorno (007). É uma migração aditiva e idempotente, com importação das faltas anteriores. Não altera agendamentos existentes.

Endpoints: `GET /api/pos-venda`, `GET /api/pos-venda/{id}`, `GET /api/pos-venda/responsaveis`, `POST /api/pos-venda/{id}/acoes`.

Ações: `organizar`, `contato`, `reagendar`, `nao_deseja`, `nao_contatar`. Todas exigem `versao`. Recuperação não é uma ação manual da API: vem do status Realizada na consulta vinculada.

## Validação

- `database/tests/test_pos_venda.py`: usa schema isolado, dentro de transação revertida, para testar gatilhos sem alterar dados da clínica.
- `PosVendaServiceTest`: valida conflitos de edição, encerramentos, responsável e vínculo obrigatório.
- `PosVendaIndicadoresServiceTest`: valida limites de período. `database/tests/test_pos_venda_indicadores.py` executa o SQL real dos indicadores em schema temporário revertido; verifica datas, contagens, fuso, motivos, responsáveis e ausência de dados.
- Build e lint da interface; revisão visual depende de uma instância disponível dos serviços.
