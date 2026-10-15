# Métricas do pós-venda

## Recorte da primeira entrega

O painel usa uma coorte de **casos cuja falta ocorreu entre `inicio` e `fim`, inclusive**, no fuso configurado em `app.clinic.time-zone`. O limite superior enviado ao PostgreSQL é o início do dia seguinte. Contatos, reagendamentos e desfechos podem ocorrer depois do período; o painel mostra o estado atual dos casos da coorte.

Uma falta corrigida (`registro_corrigido`) sai da coorte. Cancelamentos e correções posteriores alteram o estado atual, mas não apagam o histórico em `pos_venda_evento`. O campo “última atualização” é o maior instante entre a atualização do caso e seus eventos.

## Definições

| Indicador | Unidade | Fórmula | Fonte de verdade |
|---|---|---|---|
| Casos de falta | caso | quantidade de casos não corrigidos iniciados no período | `pos_venda_caso.data_falta` e `status` |
| Pacientes únicos | paciente | `count(distinct consulta.id_paciente)` entre os casos | consulta de origem |
| Casos contatados | caso | casos com pelo menos um evento `contato` | `pos_venda_evento` |
| Tentativas | evento | quantidade de eventos `contato`; reprocessar o resumo não cria eventos | `pos_venda_evento` |
| Casos reagendados | caso | casos com outra consulta em `pos_venda_consulta` | vínculo de consulta |
| Aguardando comparecimento | caso | estado atual `reagendado` | `pos_venda_caso.status` |
| Casos recuperados | caso | consulta reagendada registrada como `Realizada` pela agenda | gatilho de `consulta.status_consulta` |
| Pacientes recuperados | paciente | pacientes distintos com ao menos um caso recuperado na coorte | caso + consulta de origem |
| Taxa de recuperação de casos | percentual | casos recuperados ÷ casos de falta | coorte atual |
| Taxa de recuperação de pacientes | percentual | pacientes recuperados ÷ pacientes únicos | coorte atual |
| Nova falta | caso | consulta reagendada registrada como `Faltou` | agenda |
| Pendência vencida | caso | caso aberto cuja `proxima_acao` é anterior ao instante atual | `pos_venda_caso` |
| Sem responsável | caso | caso aberto sem `id_responsavel` atual | `pos_venda_caso` |
| Tempo até primeiro contato | horas | média entre a falta e o primeiro evento `contato`, no fuso da clínica | caso + evento |

Reagendamento, confirmação, autorização de convênio e faturamento isolado não contam como comparecimento. Uma pessoa com várias faltas pode representar vários casos e apenas um paciente único.

## Receita

Receita recuperada não faz parte deste recorte. Ela só poderá ser atribuída quando houver comparecimento e um evento financeiro real e verificável ligado à consulta recuperada. O frontend não estima receita a partir de reagendamentos nem de valores demonstrativos.

## Rastreabilidade e exportação

`GET /api/pos-venda/indicadores/casos` recebe o mesmo período, uma métrica permitida, `pagina` e `tamanho`. Cada total rastreável abre os casos que o compõem. `GET /api/pos-venda/indicadores/exportar` exporta até 10.000 casos em CSV; períodos maiores devem ser reduzidos. Células que poderiam ser interpretadas como fórmulas por planilhas são neutralizadas.

A migration `017_pos_venda_metricas_indices.sql` adiciona índices idempotentes para coorte, eventos e vínculos de consulta.

## Próximas fontes

Envio, entrega, leitura e resposta de mensagens dependem de eventos idempotentes do provedor de mensageria. Casos e atendimentos externos dependem das issues de importação CSV e API de integração. Esses eventos devem preservar origem e identificador externo para que reenvios, correções e chegada fora de ordem não dupliquem resultados.
