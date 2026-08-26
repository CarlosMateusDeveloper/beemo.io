const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080'
const AGENDA_BASE = import.meta.env.VITE_AGENDA_SERVICE_URL || 'http://localhost:8081'

async function request(base, path, options) {
  const res = await fetch(`${base}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  })
  if (!res.ok) {
    const body = await res.json().catch(() => ({}))
    throw new Error(body.message || body.erro || `Erro ${res.status} em ${path}`)
  }
  if (res.status === 204) return null
  return res.json()
}

export function fetchProntuarioListagem() {
  return request(API_BASE, '/api/prontuarios/listagem')
}

export function fetchProntuarioPaciente(idPaciente) {
  return request(API_BASE, `/api/prontuarios/pacientes/${idPaciente}`)
}

export function fetchProntuarioDocumentos(idPaciente) {
  return request(API_BASE, `/api/prontuarios/pacientes/${idPaciente}/documentos`)
}

export function fetchProntuarioDetalhe(idProntuario) {
  return request(API_BASE, `/api/prontuarios/${idProntuario}/detalhe`)
}

export function criarProntuario(payload) {
  return request(API_BASE, '/api/prontuarios', { method: 'POST', body: JSON.stringify(payload) })
}

export function atualizarProntuario(id, payload) {
  return request(API_BASE, `/api/prontuarios/${id}`, { method: 'PUT', body: JSON.stringify(payload) })
}

// Reaproveita a listagem já pronta de /pacientes pro passo "selecionar
// paciente" do fluxo de Novo atendimento.
export function fetchPacientesParaSelecao() {
  return request(API_BASE, '/api/pacientes/listagem')
}

export function fetchMedicos() {
  return request(API_BASE, '/api/medicos')
}

export function fetchPaciente(idPaciente) {
  return request(API_BASE, `/api/pacientes/${idPaciente}`)
}

export function atualizarPaciente(idPaciente, dados) {
  return request(API_BASE, `/api/pacientes/${idPaciente}`, { method: 'PUT', body: JSON.stringify(dados) })
}

export function fetchConvenios() {
  return request(API_BASE, '/api/convenios')
}

// Cria o slot de agenda + consulta no agenda-service (Go) — prontuario só
// pode existir depois que a consulta existe (FK id_consulta NOT NULL UNIQUE).
export function criarConsultaAgenda(payload) {
  return request(AGENDA_BASE, '/consultas', { method: 'POST', body: JSON.stringify(payload) })
}
