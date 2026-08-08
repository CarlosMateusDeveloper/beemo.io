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

export const SPECIALTIES = [...new Set(PROFESSIONALS.map((p) => p.specialty))]

export const APPOINTMENT_TYPES = ['Consulta', 'Retorno', 'Exame', 'Avaliação']

// Chaves e rótulos batem exatamente com status_consulta_enum em
// database/schema_clinica.sql. bg/fg/border/dot apontam para custom
// properties definidas em src/index.css (com variantes claras e escuras),
// exceto 'Confirmada', que usa as variáveis --acc-* (accent do tema).
export const STATUS = {
  Agendada: {
    label: 'Agendada',
    bg: 'var(--status-agendada-bg)', fg: 'var(--status-agendada-fg)', border: 'var(--status-agendada-border)', dot: 'var(--status-agendada-dot)', dashed: true,
  },
  Confirmada: {
    label: 'Confirmada',
    bg: 'var(--acc-soft)', fg: 'var(--acc-deep)', border: 'var(--acc-line)', dot: 'var(--acc)',
  },
  'Em Espera': {
    label: 'Em Espera',
    bg: 'var(--status-em-espera-bg)', fg: 'var(--status-em-espera-fg)', border: 'var(--status-em-espera-border)', dot: 'var(--status-em-espera-dot)',
  },
  'Em Atendimento': {
    label: 'Em Atendimento',
    bg: 'var(--status-em-atendimento-bg)', fg: 'var(--status-em-atendimento-fg)', border: 'var(--status-em-atendimento-border)', dot: 'var(--status-em-atendimento-dot)',
  },
  Realizada: {
    label: 'Realizada',
    bg: 'var(--status-realizada-bg)', fg: 'var(--status-realizada-fg)', border: 'var(--status-realizada-border)', dot: 'var(--status-realizada-dot)',
  },
  Cancelada: {
    label: 'Cancelada',
    bg: 'var(--status-cancelada-bg)', fg: 'var(--status-cancelada-fg)', border: 'var(--status-cancelada-border)', dot: 'var(--status-cancelada-dot)', strike: true,
  },
  Faltou: {
    label: 'Faltou',
    bg: 'var(--status-faltou-bg)', fg: 'var(--status-faltou-fg)', border: 'var(--status-faltou-border)', dot: 'var(--status-faltou-dot)',
  },
}

// Transições manuais válidas a partir de cada status, exibidas como botões
// no modal de detalhes. Estados terminais (Realizada/Cancelada/Faltou) não
// têm transição — a consulta já encerrou seu ciclo.
export const STATUS_TRANSITIONS = {
  Agendada: [
    { status: 'Confirmada', label: 'Confirmar', kind: 'positive' },
    { status: 'Cancelada', label: 'Cancelar', kind: 'neutral' },
  ],
  Confirmada: [
    { status: 'Em Espera', label: 'Check-in', kind: 'positive' },
    { status: 'Faltou', label: 'Marcar falta', kind: 'negative' },
    { status: 'Cancelada', label: 'Cancelar', kind: 'neutral' },
  ],
  'Em Espera': [
    { status: 'Em Atendimento', label: 'Iniciar atendimento', kind: 'positive' },
    { status: 'Faltou', label: 'Marcar falta', kind: 'negative' },
  ],
  'Em Atendimento': [
    { status: 'Realizada', label: 'Concluir', kind: 'positive' },
  ],
  Realizada: [],
  Cancelada: [],
  Faltou: [],
}

export const START_MINUTES = 8 * 60 // 08:00
export const END_MINUTES = 19 * 60 // 19:00
export const DEFAULT_SLOT_MINUTES = 30
