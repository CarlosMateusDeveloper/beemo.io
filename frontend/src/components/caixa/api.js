import { apiRequest } from '../../lib/apiClient'

export function fetchTurnoAtual() {
  return apiRequest('/api/caixa/turno-atual')
}

export function registrarPagamento({ idFatura, valor, metodo, parcelas, desconto, motivoDesconto }) {
  return apiRequest('/api/caixa/pagamentos', {
    method: 'POST',
    body: JSON.stringify({ idFatura, valor, metodo, parcelas, desconto, motivoDesconto }),
  })
}

export function fecharTurno({ dinheiroContado, observacao }) {
  return apiRequest('/api/caixa/turno/fechar', {
    method: 'POST',
    body: JSON.stringify({ dinheiroContado, observacao }),
  })
}

export function fetchCaixaDre(periodo) {
  const params = new URLSearchParams()
  if (periodo) params.set('periodo', periodo)
  const qs = params.toString()
  return apiRequest(`/api/caixa/dre${qs ? `?${qs}` : ''}`)
}

export function fetchCaixaFluxoConsolidado(periodo) {
  const params = new URLSearchParams()
  if (periodo) params.set('periodo', periodo)
  const qs = params.toString()
  return apiRequest(`/api/caixa/fluxo-consolidado${qs ? `?${qs}` : ''}`)
}

// --- Despesas ---

export function fetchDespesas({ status, categoria, periodo, page = 0, size = 20 } = {}) {
  const params = new URLSearchParams()
  if (status) params.set('status', status)
  if (categoria) params.set('categoria', categoria)
  if (periodo) params.set('periodo', periodo)
  params.set('page', page)
  params.set('size', size)
  return apiRequest(`/api/despesas?${params.toString()}`)
}

export function fetchDespesasResumo(periodo) {
  const params = new URLSearchParams()
  if (periodo) params.set('periodo', periodo)
  const qs = params.toString()
  return apiRequest(`/api/despesas/resumo${qs ? `?${qs}` : ''}`)
}

export function criarDespesa(dados) {
  return apiRequest('/api/despesas', { method: 'POST', body: JSON.stringify(dados) })
}

export function atualizarDespesa(id, dados) {
  return apiRequest(`/api/despesas/${id}`, { method: 'PUT', body: JSON.stringify(dados) })
}

export function marcarDespesaPaga(id, dataPagamento) {
  return apiRequest(`/api/despesas/${id}/pagar`, { method: 'POST', body: JSON.stringify({ dataPagamento: dataPagamento || null }) })
}

export function cancelarDespesa(id) {
  return apiRequest(`/api/despesas/${id}/cancelar`, { method: 'POST' })
}
