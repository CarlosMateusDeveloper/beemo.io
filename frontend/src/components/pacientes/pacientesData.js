// Dataset mock da tela /pacientes. Sem endpoint de backend ainda — a tabela
// e os KPIs devem futuramente consumir GET /pacientes?busca=&periodo=&convenios=&status=
// e o kanban GET /pacientes/fila?data=, já agregados pelo servidor.

export const PERIODOS = ['Hoje', '7 dias', 'Mês', 'Personalizado']
export const CONVENIOS = ['Unimed', 'Bradesco Saúde', 'SulAmérica', 'Particular']

export const KPI_BASE = {
  baseAtiva: 0,
  totalCadastros: 0,
  novos: 0,
  novosDeltaPct: 0,
  novosCanalPct: 0,
  risco: 0,
  incompletos: 0,
  incompletosSemDocumento: 0,
}

const STATUS_LABEL = { ok: 'Ativo', risk: 'Em risco', off: 'Inativo', inc: 'Cadastro incompleto' }

// Sem pacientes cadastrados ainda — GET /pacientes deve popular esta lista.
export const PACIENTES = []

export function statusLabel(status) {
  return STATUS_LABEL[status]
}

// Fila do dia (visão Kanban) — cinco etapas do fluxo real de atendimento.
// esperaMin só é usado (e mostrado) na coluna "recepcao". Sem agendamentos
// hoje ainda — GET /pacientes/fila?data= deve popular os cards de cada coluna.
export const COLUNAS_KANBAN = [
  { id: 'agendado', nome: 'Agendado', cards: [] },
  { id: 'confirmado', nome: 'Confirmado', cards: [] },
  { id: 'recepcao', nome: 'Recepção', cards: [] },
  { id: 'atendimento', nome: 'Em atendimento', cards: [] },
  { id: 'concluido', nome: 'Concluído', cards: [] },
]

export const EVENTO_POR_COLUNA = {
  confirmado: 'Presença confirmada',
  recepcao: 'Check-in registrado',
  atendimento: 'Atendimento iniciado',
  concluido: 'Atendimento encerrado',
  agendado: 'Voltou para agendado',
}
