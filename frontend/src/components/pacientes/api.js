import { apiRequest } from '../../lib/apiClient'

export function fetchPacientesKpis() {
  return apiRequest('/api/pacientes/kpis')
}

export function fetchPacientesListagem() {
  return apiRequest('/api/pacientes/listagem')
}

// Issue #4: tabela paginada — busca/status/convênio/ordenação viram
// parâmetros de query, o backend devolve só a página pedida.
export function fetchPacientesTabela({ busca, status, filtroKpi, convenios, ordem, direcao, pagina }) {
  const params = new URLSearchParams()
  if (busca) params.set('busca', busca)
  if (status) params.set('status', status)
  if (filtroKpi) params.set('filtroKpi', filtroKpi)
  if (convenios) convenios.forEach((c) => params.append('convenio', c))
  if (ordem) params.set('ordem', ordem)
  if (direcao) params.set('direcao', direcao)
  params.set('page', String(pagina ?? 0))
  return apiRequest(`/api/pacientes/tabela?${params.toString()}`)
}

// "Revelar CPF" busca o cadastro completo pontualmente — a listagem nunca
// traz o CPF sem máscara (issue #11).
export function fetchPacienteCompleto(id) {
  return apiRequest(`/api/pacientes/${id}`)
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
