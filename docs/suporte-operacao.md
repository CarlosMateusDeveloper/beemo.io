# Operação de suporte do Clinisis

## Canal e horário

O canal oficial é a Central de Ajuda dentro do produto. Cada solicitação recebe protocolo, responsável, histórico e estado visível para a clínica. O atendimento regular funciona em dias úteis, das 8h às 18h no horário de Brasília. Incidentes críticos de indisponibilidade entram na fila de plantão.

## Prioridades e primeiro retorno

| Prioridade | Quando usar | Meta de primeiro retorno |
| --- | --- | --- |
| Crítica | Sistema indisponível ou operação inteira impedida | 30 minutos |
| Alta | Processo essencial sem alternativa viável | 2 horas |
| Normal | Falha parcial com alternativa ou dúvida operacional | 8 horas úteis |
| Baixa | Melhoria, orientação ou questão sem bloqueio | 24 horas úteis |

O responsável pelo plantão faz a triagem, remove dados sensíveis do diagnóstico e atribui o especialista. Incidentes recorrentes devem gerar análise de causa, ação preventiva e artigo na Central de Ajuda.

## Diagnóstico e escalonamento

1. Confirmar clínica, tela, horário, ação executada e mensagem de erro.
2. Reproduzir sem dados reais sempre que possível.
3. Registrar hipótese, evidência e ação no histórico do chamado.
4. Escalonar para engenharia quando houver erro reproduzível, indisponibilidade, perda de dados ou risco de segurança.
5. Comunicar resolução, impacto e orientação preventiva antes de encerrar.

Chamados não aceitam arquivos nesta versão. Imagens, prontuários, documentos, senhas, dados de cartão e informações clínicas não devem ser enviados. Um futuro fluxo de anexos deverá usar criptografia, expiração e verificação automática de conteúdo antes de ser habilitado.

## Acesso temporário

O acesso de suporte exige solicitação da clínica e aprovação de outra pessoa com permissão administrativa. O solicitante, o agente e o autorizador devem ser pessoas diferentes nos papéis que executam a concessão. A duração fica entre 15 minutos e 8 horas.

Cada concessão define escopos explícitos. O padrão é leitura operacional. Leitura financeira, alteração operacional e exportação são escopos separados. Toda requisição feita pelo agente registra usuário, clínica, rota, método, resultado e horário. A clínica pode revogar o acesso imediatamente; a expiração também o encerra automaticamente.

## Indicadores

A Central mede volume aberto, tempo de primeira resposta, tempo de resolução, reaberturas, satisfação e esforço. Esses indicadores serão consumidos pelo painel interno da issue #33.
