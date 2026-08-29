import { apiRequest } from '../../lib/apiClient'

export function fetchDashboard({ periodo, profissionalId }) {
  return apiRequest('/api/dashboard', {
    method: 'POST',
    body: JSON.stringify({
      periodo,
      profissionalId: profissionalId && profissionalId !== 'todos' ? Number(profissionalId) : null,
    }),
  })
}

export function fetchMedicos() {
  return apiRequest('/api/medicos')
}
