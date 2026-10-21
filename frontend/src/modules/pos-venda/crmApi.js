import { apiRequest } from '../../lib/apiClient'

function query(params = {}) {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== '' && value !== null && value !== undefined) search.set(key, value)
  })
  return search.toString() ? `?${search}` : ''
}

export const fetchCrmOportunidades = (params) => apiRequest(`/api/crm/oportunidades${query(params)}`)
export const fetchCrmOportunidade = (id) => apiRequest(`/api/crm/oportunidades/${id}`)
export const fetchCrmIndicadores = (params) => apiRequest(`/api/crm/oportunidades/indicadores${query(params)}`)
export const fetchCrmResponsaveis = () => apiRequest('/api/crm/oportunidades/responsaveis')
export const criarCrmOportunidade = (payload) => apiRequest('/api/crm/oportunidades', { method: 'POST', body: JSON.stringify(payload) })
export const atualizarCrmOportunidade = (id, payload) => apiRequest(`/api/crm/oportunidades/${id}`, { method: 'PUT', body: JSON.stringify(payload) })
export const transicionarCrmOportunidade = (id, payload) => apiRequest(`/api/crm/oportunidades/${id}/transicoes`, { method: 'POST', body: JSON.stringify(payload) })
export const registrarCrmInteracao = (id, payload) => apiRequest(`/api/crm/oportunidades/${id}/interacoes`, { method: 'POST', body: JSON.stringify(payload) })
export const converterCrmOportunidade = (id, payload) => apiRequest(`/api/crm/oportunidades/${id}/converter`, { method: 'POST', body: JSON.stringify(payload) })
export const buscarPacientesCrm = (busca) => apiRequest(`/api/pacientes${query({ busca, size: 10 })}`)
export const fetchMedicosCrm = () => apiRequest('/api/medicos')
