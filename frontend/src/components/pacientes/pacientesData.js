// Formatação e constantes de UI da tela /pacientes. Os dados em si vêm de
// GET /api/pacientes/kpis, /listagem e /fila (ver ./api.js) — nada aqui é mock.

export const PERIODOS = ['Hoje', '7 dias', 'Mês', 'Personalizado']

const STATUS_LABEL = { ok: 'Ativo', risk: 'Em risco', off: 'Inativo', inc: 'Cadastro incompleto' }

export function statusLabel(status) {
  return STATUS_LABEL[status]
}

// Definição das 5 colunas do kanban (fluxo real de atendimento) — os cards
// de cada uma vêm de GET /api/pacientes/fila, agrupados por `coluna`.
export const COLUNAS_KANBAN_DEF = [
  { id: 'agendado', nome: 'Agendado' },
  { id: 'confirmado', nome: 'Confirmado' },
  { id: 'recepcao', nome: 'Recepção' },
  { id: 'atendimento', nome: 'Em atendimento' },
  { id: 'concluido', nome: 'Concluído' },
]

export const EVENTO_POR_COLUNA = {
  confirmado: 'Presença confirmada',
  recepcao: 'Check-in registrado',
  atendimento: 'Atendimento iniciado',
  concluido: 'Atendimento encerrado',
  agendado: 'Voltou para agendado',
}

export function iniciaisDe(nome) {
  return nome.split(' ').map((p) => p[0]).slice(0, 2).join('').toUpperCase()
}
