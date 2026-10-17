import { API_BASE, apiDownload, apiRequest, authenticatedRequest } from '../../lib/apiClient'

export const listarPreAtendimentos = (status = '') => apiRequest(`/api/pre-atendimentos${status ? `?status=${status}` : ''}`)
export const obterPreAtendimento = (id) => apiRequest(`/api/pre-atendimentos/${id}`)
export const criarLinkPreAtendimento = (consultaId, payload = {}) => apiRequest(`/api/pre-atendimentos/consultas/${consultaId}/links`, {
  method: 'POST', body: JSON.stringify(payload),
})
export const revisarPreAtendimento = (id, payload) => apiRequest(`/api/pre-atendimentos/${id}/revisao`, {
  method: 'POST', body: JSON.stringify(payload),
})
export const revogarPreAtendimento = (id) => apiRequest(`/api/pre-atendimentos/${id}/revogar`, { method: 'POST' })
export const baixarAnexoPreAtendimento = (pre, attachment) => apiDownload(`/api/pre-atendimentos/${pre}/anexos/${attachment}`)

function publicRequest(path, token, session, options = {}) {
  const headers = new Headers(options.headers)
  headers.set('X-Pre-Atendimento-Token', token)
  if (session) headers.set('X-Pre-Atendimento-Session', session)
  return authenticatedRequest(`${API_BASE}/api/pre-atendimento/publico${path}`, { ...options, headers, authenticated: false })
}

export const consultarLink = (token) => publicRequest('', token)
export const verificarIdentidade = (token, payload) => publicRequest('/verificar', token, null, { method: 'POST', body: JSON.stringify(payload) })
export const carregarFormulario = (token, session) => publicRequest('/dados', token, session)
export const salvarRascunho = (token, session, payload) => publicRequest('/rascunho', token, session, { method: 'PUT', body: JSON.stringify(payload) })
export const enviarFormulario = (token, session, versao) => publicRequest('/enviar', token, session, { method: 'POST', body: JSON.stringify({ versao }) })
export const enviarAnexo = (token, session, arquivo) => {
  const body = new FormData(); body.append('arquivo', arquivo)
  return publicRequest('/anexos', token, session, { method: 'POST', body })
}
