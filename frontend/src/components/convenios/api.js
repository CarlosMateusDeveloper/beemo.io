import { apiRequest } from '../../lib/apiClient'

export function fetchListagem() {
  return apiRequest('/api/convenios/listagem')
}

export function fetchKpis(periodo, convenioId) {
  const params = new URLSearchParams()
  if (periodo) params.set('periodo', periodo)
  if (convenioId) params.set('convenioId', convenioId)
  const qs = params.toString()
  return apiRequest(`/api/convenios/kpis${qs ? `?${qs}` : ''}`)
}

export function fetchConvenio(id) {
  return apiRequest(`/api/convenios/${id}`)
}

export function criarConvenio(dados) {
  return apiRequest('/api/convenios', { method: 'POST', body: JSON.stringify(dados) })
}

export function atualizarConvenio(id, dados) {
  return apiRequest(`/api/convenios/${id}`, { method: 'PUT', body: JSON.stringify(dados) })
}

// --- Planos ---

export function fetchPlanos(id) {
  return apiRequest(`/api/convenios/${id}/planos`)
}

export function criarPlano(id, dados) {
  return apiRequest(`/api/convenios/${id}/planos`, { method: 'POST', body: JSON.stringify(dados) })
}

export function atualizarPlano(id, idPlano, dados) {
  return apiRequest(`/api/convenios/${id}/planos/${idPlano}`, { method: 'PUT', body: JSON.stringify(dados) })
}

// --- Procedimentos ---

export function fetchProcedimentos(id) {
  return apiRequest(`/api/convenios/${id}/procedimentos`)
}

export function criarProcedimento(id, dados) {
  return apiRequest(`/api/convenios/${id}/procedimentos`, { method: 'POST', body: JSON.stringify(dados) })
}

export function atualizarProcedimento(id, idProcedimento, dados) {
  return apiRequest(`/api/convenios/${id}/procedimentos/${idProcedimento}`, { method: 'PUT', body: JSON.stringify(dados) })
}

// --- Regras de auditoria ---

export function fetchRegras(id) {
  return apiRequest(`/api/convenios/${id}/regras`)
}

export function criarRegra(id, dados) {
  return apiRequest(`/api/convenios/${id}/regras`, { method: 'POST', body: JSON.stringify(dados) })
}

export function atualizarRegra(id, idRegra, dados) {
  return apiRequest(`/api/convenios/${id}/regras/${idRegra}`, { method: 'PUT', body: JSON.stringify(dados) })
}

// --- Documentos obrigatórios ---

export function fetchDocumentos(id) {
  return apiRequest(`/api/convenios/${id}/documentos-obrigatorios`)
}

export function criarDocumento(id, dados) {
  return apiRequest(`/api/convenios/${id}/documentos-obrigatorios`, { method: 'POST', body: JSON.stringify(dados) })
}

export function atualizarDocumento(id, idDocumento, dados) {
  return apiRequest(`/api/convenios/${id}/documentos-obrigatorios/${idDocumento}`, { method: 'PUT', body: JSON.stringify(dados) })
}
