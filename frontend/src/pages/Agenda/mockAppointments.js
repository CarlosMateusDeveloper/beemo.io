import { PROFESSIONALS, APPOINTMENT_TYPES, START_MINUTES, END_MINUTES } from './constants'
import { addDays, formatDateKey, minutesToTime, startOfDay } from './dateUtils'

const PATIENT_POOL = [
  'Ana Beatriz Souza', 'Carlos Eduardo Ramos', 'Fernanda Oliveira', 'João Pedro Martins',
  'Mariana Costa', 'Rafael Almeida', 'Beatriz Nogueira', 'Lucas Ferreira',
  'Patrícia Gomes', 'Gustavo Henrique', 'Juliana Castro', 'Roberto Dias',
  'Camila Santana', 'Diego Moreira',
]

const STATUS_PAST = ['concluido', 'concluido', 'faltou', 'cancelado', 'concluido']
const STATUS_FUTURE = ['confirmado', 'pendente', 'confirmado', 'confirmado', 'pendente']

/**
 * Gera uma agenda de exemplo (sem backend ainda). Determinístico por
 * profissional/dia via um hash simples, só para variar a distribuição.
 */
export function seedAppointments() {
  const today = startOfDay(new Date())
  const nowMinutes = new Date().getHours() * 60 + new Date().getMinutes()
  const appointments = []
  let uid = 1

  PROFESSIONALS.forEach((prof, profIndex) => {
    for (let offset = -14; offset <= 16; offset++) {
      const day = addDays(today, offset)
      const dateKey = formatDateKey(day)
      const hash = (((offset + 30) * (profIndex + 3)) * 2654435761 >>> 0) % 1000
      const count = offset >= -3 && offset <= 5 ? 2 + (hash % 3) : hash % 3
      const usedSlots = []

      for (let j = 0; j < count; j++) {
        let slot = (hash * 7 + j * 5 + profIndex * 3) % 21
        let tries = 0
        while (usedSlots.some((s) => Math.abs(s - slot) < 2) && tries < 21) {
          slot = (slot + 3) % 21
          tries++
        }
        usedSlots.push(slot)

        const start = START_MINUTES + slot * 30
        const dur = (hash + j) % 5 === 0 && start + 60 <= END_MINUTES ? 60 : 30

        let status
        if (offset < 0) status = STATUS_PAST[(hash + j) % 5]
        else if (offset === 0) {
          status = start + dur <= nowMinutes ? 'concluido' : (hash + j) % 3 === 0 ? 'pendente' : 'confirmado'
        } else status = STATUS_FUTURE[(hash + j) % 5]

        appointments.push({
          id: 'a' + uid++,
          profId: prof.id,
          dateKey,
          start: minutesToTime(start),
          dur,
          patient: PATIENT_POOL[(hash + j * 3) % PATIENT_POOL.length],
          type: APPOINTMENT_TYPES[(hash + j) % APPOINTMENT_TYPES.length],
          status,
        })
      }
    }
  })

  return appointments
}
