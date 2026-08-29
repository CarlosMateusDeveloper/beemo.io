import { apiRequest } from '../../lib/apiClient'

export function fetchMedicosPainel(periodo) {
  return apiRequest('/api/medicos/painel', {
    method: 'POST',
    body: JSON.stringify({ periodo }),
  })
}

export function fetchEspecialidades() {
  return apiRequest('/api/especialidades')
}
