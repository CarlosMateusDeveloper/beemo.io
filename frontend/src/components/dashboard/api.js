import { apiRequest } from '../../lib/apiClient'

export function fetchDashboard({ periodo, profissionalId, dataInicio, dataFim }) {
  return apiRequest('/api/dashboard', {
    method: 'POST',
    body: JSON.stringify({
      periodo,
      profissionalId: profissionalId && profissionalId !== 'todos' ? Number(profissionalId) : null,
      dataInicio: periodo === 'Personalizado' ? dataInicio : null,
      dataFim: periodo === 'Personalizado' ? dataFim : null,
    }),
  })
}

export function fetchMedicos() {
  return apiRequest('/api/medicos')
}
