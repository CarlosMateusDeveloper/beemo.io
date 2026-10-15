import { apiRequest } from '../lib/apiClient'

export function fetchOnboarding() { return apiRequest('/api/onboarding') }
export function saveOnboarding(data) {
  return apiRequest('/api/onboarding', { method: 'PUT', body: JSON.stringify(data) })
}
export function completeOnboarding() { return apiRequest('/api/onboarding/concluir', { method: 'POST' }) }
export function reopenOnboarding() { return apiRequest('/api/onboarding/reabrir', { method: 'POST' }) }
export function validateMappedCsv(file, configuration) {
  const body = new FormData()
  body.append('arquivo', file)
  body.append('configuracao', new Blob([JSON.stringify(configuration)], { type: 'application/json' }))
  return apiRequest('/api/pos-venda/importacoes/csv/validar', { method: 'POST', body })
}
export function rollbackImport(id) {
  return apiRequest(`/api/pos-venda/importacoes/${id}/rollback`, { method: 'POST' })
}
