// Constantes e formatação da tela /prontuario. Dados em si vêm de
// GET /api/prontuarios/* (ver ./api.js) — nada aqui é mock.

export const PERIODOS = ['Todos', 'Hoje', '7 dias', 'Mês']
export const TIPOS_ATENDIMENTO = ['Consulta', 'Retorno', 'Exame', 'Avaliação']

const STATUS_LABEL = { finalizado: 'Finalizado', pendente: 'Pendente', sem_registro: 'Sem registro' }

export function statusLabel(status) {
  return STATUS_LABEL[status] ?? status
}

export function iniciaisDe(nome) {
  return nome.split(' ').map((p) => p[0]).slice(0, 2).join('').toUpperCase()
}

// Compara ultimaData (ISO "yyyy-MM-dd") contra o filtro de período.
export function dentroDoPeriodo(dataIso, periodo) {
  if (periodo === 'Todos' || !dataIso) return true
  const data = new Date(`${dataIso}T00:00:00`)
  const hoje = new Date()
  hoje.setHours(0, 0, 0, 0)
  if (periodo === 'Hoje') return data.getTime() === hoje.getTime()
  if (periodo === '7 dias') {
    const limite = new Date(hoje)
    limite.setDate(limite.getDate() - 6)
    return data >= limite && data <= hoje
  }
  if (periodo === 'Mês') return data.getMonth() === hoje.getMonth() && data.getFullYear() === hoje.getFullYear()
  return true
}

// yyyy-MM-ddTHH:mm (valor de <input type="datetime-local">) -> { dataSlot, horaSlot }
export function separarDataHora(valorDatetimeLocal) {
  const [dataSlot, horaSlot] = valorDatetimeLocal.split('T')
  return { dataSlot, horaSlot: horaSlot?.length === 5 ? `${horaSlot}:00` : horaSlot }
}

// Agora, no formato aceito por <input type="datetime-local">.
export function agoraDatetimeLocal() {
  const agora = new Date()
  agora.setMinutes(agora.getMinutes() - agora.getTimezoneOffset())
  return agora.toISOString().slice(0, 16)
}
