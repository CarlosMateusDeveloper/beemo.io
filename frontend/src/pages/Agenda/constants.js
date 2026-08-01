export const MONTHS = [
  'janeiro', 'fevereiro', 'março', 'abril', 'maio', 'junho',
  'julho', 'agosto', 'setembro', 'outubro', 'novembro', 'dezembro',
]

export const DOW_SHORT = ['Dom', 'Seg', 'Ter', 'Qua', 'Qui', 'Sex', 'Sáb']
export const DOW_FULL = [
  'Domingo', 'Segunda-feira', 'Terça-feira', 'Quarta-feira',
  'Quinta-feira', 'Sexta-feira', 'Sábado',
]
export const MONTH_HEADER_DOWS = ['Seg', 'Ter', 'Qua', 'Qui', 'Sex', 'Sáb', 'Dom']

export const PROFESSIONALS = [
  { id: 'p1', name: 'Dra. Camila Rocha', specialty: 'Clínica geral' },
  { id: 'p2', name: 'Dr. André Lima', specialty: 'Cardiologia' },
  { id: 'p3', name: 'Dra. Paula Mendes', specialty: 'Dermatologia' },
]

export const APPOINTMENT_TYPES = ['Consulta', 'Retorno', 'Exame', 'Avaliação']

export const STATUS = {
  confirmado: {
    label: 'Confirmada',
    bg: 'var(--acc-soft)', fg: 'var(--acc-deep)', border: 'var(--acc-line)', dot: 'var(--acc)',
  },
  pendente: {
    label: 'Pendente',
    bg: '#FCF4DF', fg: '#8A6410', border: '#F0DFAE', dot: '#D9A514', dashed: true,
  },
  concluido: {
    label: 'Concluída',
    bg: '#E7F4EC', fg: '#207146', border: '#CDE8D8', dot: '#2E9E6B',
  },
  faltou: {
    label: 'Faltou',
    bg: '#FCEBE8', fg: '#AC3B2A', border: '#F3CFC8', dot: '#D2543F',
  },
  cancelado: {
    label: 'Cancelada',
    bg: '#F2F4F7', fg: '#8A94A6', border: '#E2E6EC', dot: '#9AA4B5', strike: true,
  },
}

export const START_MINUTES = 8 * 60 // 08:00
export const END_MINUTES = 19 * 60 // 19:00
export const DEFAULT_SLOT_MINUTES = 30
export const DEFAULT_ACCENT = '#3D6EDC'
