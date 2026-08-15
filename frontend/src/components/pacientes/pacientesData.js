// Dataset mock da tela /pacientes. Sem endpoint de backend ainda — a tabela
// e os KPIs devem futuramente consumir GET /pacientes?busca=&periodo=&convenios=&status=
// e o kanban GET /pacientes/fila?data=, já agregados pelo servidor.

export const PERIODOS = ['Hoje', '7 dias', 'Mês', 'Personalizado']
export const CONVENIOS = ['Unimed', 'Bradesco Saúde', 'SulAmérica', 'Particular']

export const KPI_BASE = {
  baseAtiva: 1284,
  totalCadastros: 2017,
  novos: 47,
  novosDeltaPct: 12,
  novosCanalPct: 60,
  risco: 89,
  incompletos: 34,
  incompletosSemDocumento: 23,
}

const STATUS_LABEL = { ok: 'Ativo', risk: 'Em risco', off: 'Inativo', inc: 'Cadastro incompleto' }

export const PACIENTES = [
  { id: 1, nome: 'Maria Souza', iniciais: 'MS', idade: 42, sexo: 'F', telefone: '(84) 99812-4477', whatsapp: true,
    convenio: 'Unimed', ultimaData: '2026-07-18', ultimaTxt: '18/07/2026', ultimaEspecialidade: 'Cardiologia',
    proximaData: '2026-08-15', proximaTxt: 'Hoje · 14:00', status: 'ok' },
  { id: 2, nome: 'João Alves', iniciais: 'JA', idade: 35, sexo: 'M', telefone: '(84) 99640-1120', whatsapp: true,
    convenio: 'Bradesco Saúde', ultimaData: '2026-08-02', ultimaTxt: '02/08/2026', ultimaEspecialidade: 'Ortopedia',
    proximaData: '2026-08-15', proximaTxt: 'Hoje · 14:30', status: 'ok' },
  { id: 3, nome: 'Paula Dias', iniciais: 'PD', idade: 28, sexo: 'F', telefone: '(84) 98871-0093', whatsapp: true,
    convenio: 'Particular', ultimaData: '2026-06-11', ultimaTxt: '11/06/2026', ultimaEspecialidade: 'Dermatologia',
    proximaData: '2026-08-15', proximaTxt: 'Hoje · 13:30', status: 'ok' },
  { id: 4, nome: 'Lucas Rocha', iniciais: 'LR', idade: 51, sexo: 'M', telefone: '(84) 99125-7788', whatsapp: false,
    convenio: 'SulAmérica', ultimaData: '2026-08-09', ultimaTxt: '09/08/2026', ultimaEspecialidade: 'Cardiologia',
    proximaData: '2026-08-15', proximaTxt: 'Hoje · 13:00', status: 'ok' },
  { id: 5, nome: 'Rita Nunes', iniciais: 'RN', idade: 63, sexo: 'F', telefone: '(84) 99333-2091', whatsapp: true,
    convenio: 'Unimed', ultimaData: '2026-08-14', ultimaTxt: '14/08/2026', ultimaEspecialidade: 'Pediatria',
    proximaData: null, proximaTxt: '—', status: 'inc' },
  { id: 6, nome: 'Carlos Bento', iniciais: 'CB', idade: 47, sexo: 'M', telefone: '(84) 98450-6612', whatsapp: true,
    convenio: 'Particular', ultimaData: '2025-12-03', ultimaTxt: '03/12/2025', ultimaEspecialidade: 'Ortopedia',
    proximaData: null, proximaTxt: '—', status: 'risk' },
  { id: 7, nome: 'Helena Prado', iniciais: 'HP', idade: 39, sexo: 'F', telefone: '(84) 99771-3388', whatsapp: true,
    convenio: 'Unimed', ultimaData: '2025-11-27', ultimaTxt: '27/11/2025', ultimaEspecialidade: 'Dermatologia',
    proximaData: null, proximaTxt: '—', status: 'risk' },
  { id: 8, nome: 'Bruno Tavares', iniciais: 'BT', idade: 31, sexo: 'M', telefone: '(84) 99002-4415', whatsapp: false,
    convenio: 'Bradesco Saúde', ultimaData: '2026-07-21', ultimaTxt: '21/07/2026', ultimaEspecialidade: 'Clínico geral',
    proximaData: '2026-08-22', proximaTxt: '22/08 · 09:15', status: 'ok' },
  { id: 9, nome: 'Sofia Andrade', iniciais: 'SA', idade: 8, sexo: 'F', telefone: '(84) 99518-7734', whatsapp: true,
    convenio: 'SulAmérica', ultimaData: '2026-08-05', ultimaTxt: '05/08/2026', ultimaEspecialidade: 'Pediatria',
    proximaData: '2026-08-29', proximaTxt: '29/08 · 10:00', status: 'ok' },
  { id: 10, nome: 'Vitor Câmara', iniciais: 'VC', idade: 56, sexo: 'M', telefone: '—', whatsapp: false,
    convenio: 'Particular', ultimaData: '2026-07-30', ultimaTxt: '30/07/2026', ultimaEspecialidade: 'Clínico geral',
    proximaData: null, proximaTxt: '—', status: 'inc' },
  { id: 11, nome: 'Teresa Mota', iniciais: 'TM', idade: 45, sexo: 'F', telefone: '(84) 99120-8834', whatsapp: true,
    convenio: 'Particular', ultimaData: '2026-08-01', ultimaTxt: '01/08/2026', ultimaEspecialidade: 'Dermatologia',
    proximaData: '2026-08-15', proximaTxt: 'Hoje · 14:45', status: 'ok' },
  { id: 12, nome: 'Marcos Vieira', iniciais: 'MV', idade: 60, sexo: 'M', telefone: '(84) 99244-1187', whatsapp: true,
    convenio: 'Bradesco Saúde', ultimaData: '2026-07-28', ultimaTxt: '28/07/2026', ultimaEspecialidade: 'Clínico geral',
    proximaData: '2026-08-15', proximaTxt: 'Hoje · 13:45', status: 'ok' },
]

export function statusLabel(status) {
  return STATUS_LABEL[status]
}

// Fila do dia (visão Kanban) — cinco etapas do fluxo real de atendimento.
// esperaMin só é usado (e mostrado) na coluna "recepcao".
export const COLUNAS_KANBAN = [
  { id: 'agendado', nome: 'Agendado', cards: [
    { nome: 'Bruno Tavares', hora: '15:00', especialidade: 'Clínico geral · Dr. Paulo', convenio: 'Bradesco' },
    { nome: 'Sofia Andrade', hora: '15:30', especialidade: 'Pediatria · Dra. Beatriz', convenio: 'SulAmérica' },
    { nome: 'Igor Bastos', hora: '16:00', especialidade: 'Ortopedia · Dr. Carlos', convenio: null },
    { nome: 'Nara Lopes', hora: '16:30', especialidade: 'Cardiologia · Dra. Ana', convenio: 'Unimed' },
  ] },
  { id: 'confirmado', nome: 'Confirmado', cards: [
    { nome: 'Maria Souza', hora: '14:00', especialidade: 'Cardiologia · Dra. Ana', convenio: 'Unimed' },
    { nome: 'João Alves', hora: '14:30', especialidade: 'Ortopedia · Dr. Carlos', convenio: null },
    { nome: 'Teresa Mota', hora: '14:45', especialidade: 'Dermatologia · Dra. Iris', convenio: 'Particular' },
  ] },
  { id: 'recepcao', nome: 'Recepção', cards: [
    { nome: 'Paula Dias', hora: '13:30', especialidade: 'Dermatologia · Dra. Iris', esperaMin: 34 },
    { nome: 'Marcos Vieira', hora: '13:45', especialidade: 'Clínico geral · Dr. Paulo', esperaMin: 22 },
    { nome: 'Elis Ferraz', hora: '14:00', especialidade: 'Pediatria · Dra. Beatriz', esperaMin: 6 },
  ] },
  { id: 'atendimento', nome: 'Em atendimento', cards: [
    { nome: 'Lucas Rocha', hora: '13:00', especialidade: 'Cardiologia · Dra. Ana' },
    { nome: 'Diego Sales', hora: '13:15', especialidade: 'Ortopedia · Dr. Carlos' },
  ] },
  { id: 'concluido', nome: 'Concluído', cards: [
    { nome: 'Rita Nunes', hora: '11:30', especialidade: 'Pediatria · Dra. Beatriz', pendencia: 'pend. laudo' },
    { nome: 'Ana Clara Reis', hora: '11:00', especialidade: 'Clínico geral · Dr. Paulo' },
    { nome: 'Otávio Lins', hora: '10:30', especialidade: 'Cardiologia · Dra. Ana' },
  ] },
]

export const EVENTO_POR_COLUNA = {
  confirmado: 'Presença confirmada',
  recepcao: 'Check-in registrado',
  atendimento: 'Atendimento iniciado',
  concluido: 'Atendimento encerrado',
  agendado: 'Voltou para agendado',
}
