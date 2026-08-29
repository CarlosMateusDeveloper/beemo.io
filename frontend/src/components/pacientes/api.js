import { apiRequest } from '../../lib/apiClient'

export function fetchPacientesKpis() {
  return apiRequest('/api/pacientes/kpis')
}

export function fetchPacientesListagem() {
  return apiRequest('/api/pacientes/listagem')
}

export function fetchPacientesFila() {
  return apiRequest('/api/pacientes/fila')
}

export function criarPaciente(dados) {
  return apiRequest('/api/pacientes', { method: 'POST', body: JSON.stringify(dados) })
}

export function fetchConvenios() {
  return apiRequest('/api/convenios')
}
