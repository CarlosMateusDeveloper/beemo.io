import { apiRequest, authenticatedRequest, serviceBase } from '../../lib/apiClient'

const AGENDA_BASE = serviceBase(import.meta.env.VITE_AGENDA_SERVICE_URL, 8081)

export function fetchProntuarioListagem() {
  return apiRequest('/api/prontuarios/listagem')
}

export function fetchProntuarioPaciente(idPaciente) {
  return apiRequest(`/api/prontuarios/pacientes/${idPaciente}`)
}

export function fetchProntuarioDocumentos(idPaciente) {
  return apiRequest(`/api/prontuarios/pacientes/${idPaciente}/documentos`)
}

export function fetchProntuarioDetalhe(idProntuario) {
  return apiRequest(`/api/prontuarios/${idProntuario}/detalhe`)
}

export function criarProntuario(payload) {
  return apiRequest('/api/prontuarios', { method: 'POST', body: JSON.stringify(payload) })
}

export function atualizarProntuario(id, payload) {
  return apiRequest(`/api/prontuarios/${id}`, { method: 'PUT', body: JSON.stringify(payload) })
}

// Reaproveita a listagem já pronta de /pacientes pro passo "selecionar
// paciente" do fluxo de Novo atendimento.
export function fetchPacientesParaSelecao() {
  return apiRequest('/api/pacientes/listagem')
}

export function fetchMedicos() {
  return apiRequest('/api/medicos')
}

export function fetchPaciente(idPaciente) {
  return apiRequest(`/api/pacientes/${idPaciente}`)
}

export function atualizarPaciente(idPaciente, dados) {
  return apiRequest(`/api/pacientes/${idPaciente}`, { method: 'PUT', body: JSON.stringify(dados) })
}

export function fetchConvenios() {
  return apiRequest('/api/convenios')
}

// Cria o slot de agenda + consulta no agenda-service (Go) — prontuario só
// pode existir depois que a consulta existe (FK id_consulta NOT NULL UNIQUE).
// Compartilha a sessão com o agenda-service.
export async function criarConsultaAgenda(payload) {
  return authenticatedRequest(`${AGENDA_BASE}/consultas`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}
