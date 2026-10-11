# Conciliação de reagendamentos externos

O ClinicOS acompanha um reagendamento vindo de uma fonte externa até receber o desfecho clínico do atendimento. O vínculo precisa pertencer à mesma clínica, fonte, paciente, tipo e especialidade do atendimento que originou a falta. O horário deve ser posterior à falta, estar no futuro e ainda não pode estar vinculado a outro caso.

## Estados aceitos

| Situação normalizada recebida | Efeito no caso |
| --- | --- |
| `AGENDADO`, `CONFIRMADO` ou `DESCONHECIDO` | Mantém `reagendado`; confirmação, autorização do convênio e faturamento não comprovam presença. |
| `REALIZADO` | Marca `recuperado`. Esta é a única evidência externa que conta como recuperação. |
| `FALTOU` | Reabre como `nova_falta`, preserva contatos e tentativas, marca recorrência e cria revisão administrativa. |
| `CANCELADO` | Reabre como `pendente` para nova ação da equipe. |

Se o horário passa sem um desfecho, o caso recebe o alerta `conferir_presenca`. O sistema não cancela nem libera horários automaticamente. Uma nova falta recebe o alerta `revisar_recorrencia`, que precisa ser tratado pela recepção ou administração.

## Auditoria e idempotência

Cada mudança efetiva de situação gera um evento com o identificador do atendimento e a fonte que enviou o dado. Repetir a mesma situação não gera outro evento nem uma segunda transição. Retificações posteriores são aplicadas ao caso atual e ficam no histórico. Atualizações de vínculos antigos continuam auditáveis, mas não substituem o desfecho do reagendamento atualmente acompanhado.

O histórico de vínculos é mantido em `pos_venda_atendimento_externo`. A restrição única dessa tabela impede que o mesmo atendimento seja usado para recuperar dois casos.

## API e interface

Na ação `reagendar`, casos de origem externa enviam `idAtendimentoExterno`; casos da agenda interna continuam enviando `idConsulta`. A tela de oportunidades oferece apenas candidatos elegíveis e mostra fonte, identificador, horário e situação informada. O detalhe e a exportação de indicadores exibem o reagendamento externo e seu último estado normalizado.
