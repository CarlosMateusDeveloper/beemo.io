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

// --- Usuários (pra selects de responsável) ---

export function fetchUsuarios() {
  return apiRequest('/api/usuarios')
}

// --- Glosas ---

export function fetchGlosas({ status, idConvenio, idUsuarioResponsavel, recorribilidade, prazoAte, page = 0, size = 20 } = {}) {
  const params = new URLSearchParams()
  if (status) params.set('status', status)
  if (idConvenio) params.set('idConvenio', idConvenio)
  if (idUsuarioResponsavel) params.set('idUsuarioResponsavel', idUsuarioResponsavel)
  if (recorribilidade) params.set('recorribilidade', recorribilidade)
  if (prazoAte) params.set('prazoAte', prazoAte)
  params.set('page', page)
  params.set('size', size)
  return apiRequest(`/api/glosas?${params.toString()}`)
}

export function fetchGlosaIndicadores() {
  return apiRequest('/api/glosas/indicadores')
}

export function fetchGlosa(id) {
  return apiRequest(`/api/glosas/${id}`)
}

export function criarGlosa(dados) {
  return apiRequest('/api/glosas', { method: 'POST', body: JSON.stringify(dados) })
}

export function classificarGlosa(id, dados) {
  return apiRequest(`/api/glosas/${id}/classificacao`, { method: 'PUT', body: JSON.stringify(dados) })
}

export function aceitarGlosa(id) {
  return apiRequest(`/api/glosas/${id}/aceitar`, { method: 'POST' })
}

export function alterarResponsavelGlosa(id, idUsuarioResponsavel) {
  return apiRequest(`/api/glosas/${id}/responsavel`, { method: 'PUT', body: JSON.stringify({ idUsuarioResponsavel }) })
}

export function criarRecurso(idGlosa, dados) {
  return apiRequest(`/api/glosas/${idGlosa}/recursos`, { method: 'POST', body: JSON.stringify(dados) })
}

// --- Recursos de glosa ---

export function fetchRecurso(id) {
  return apiRequest(`/api/recursos/${id}`)
}

export function atualizarRecurso(id, dados) {
  return apiRequest(`/api/recursos/${id}`, { method: 'PUT', body: JSON.stringify(dados) })
}

export function anexarDocumentoRecurso(id, dados) {
  return apiRequest(`/api/recursos/${id}/documentos`, { method: 'POST', body: JSON.stringify(dados) })
}

export function enviarRecurso(id, dados) {
  return apiRequest(`/api/recursos/${id}/enviar`, { method: 'POST', body: JSON.stringify(dados) })
}

export function registrarResultadoRecurso(id, dados) {
  return apiRequest(`/api/recursos/${id}/resultado`, { method: 'POST', body: JSON.stringify(dados) })
}

// --- Lotes ---

export function fetchSugestoesLotes() {
  return apiRequest('/api/lotes/sugestoes')
}

export function fetchElegiveisLote(idConvenio) {
  return apiRequest(`/api/lotes/elegiveis?idConvenio=${idConvenio}`)
}

export function criarLote(idConvenio, idsFatura) {
  return apiRequest('/api/lotes', { method: 'POST', body: JSON.stringify({ idConvenio, idsFatura }) })
}

export function fetchLotes({ status, idConvenio, page = 0, size = 20 } = {}) {
  const params = new URLSearchParams()
  if (status) params.set('status', status)
  if (idConvenio) params.set('idConvenio', idConvenio)
  params.set('page', page)
  params.set('size', size)
  return apiRequest(`/api/lotes?${params.toString()}`)
}

export function fetchLote(id) {
  return apiRequest(`/api/lotes/${id}`)
}

export function atualizarStatusLote(id, status) {
  return apiRequest(`/api/lotes/${id}/status`, { method: 'PUT', body: JSON.stringify({ status }) })
}

// --- Auditoria ---

export function fetchAuditoriaResumo() {
  return apiRequest('/api/auditoria/resumo')
}

export function fetchAuditoriaLista({ status, page = 0, size = 20 } = {}) {
  const params = new URLSearchParams()
  if (status) params.set('status', status)
  params.set('page', page)
  params.set('size', size)
  return apiRequest(`/api/auditoria?${params.toString()}`)
}

export function fetchAuditoriaDetalhe(id) {
  return apiRequest(`/api/auditoria/${id}`)
}
