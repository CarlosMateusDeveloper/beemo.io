const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080'

async function request(path, options) {
  const res = await fetch(`${API_BASE}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  })
  if (!res.ok) {
    const body = await res.json().catch(() => ({}))
    throw new Error(body.erro || `Erro ${res.status} em ${path}`)
  }
  return res.json()
}

export function fetchDashboard({ periodo, profissionalId }) {
  return request('/api/dashboard', {
    method: 'POST',
    body: JSON.stringify({
      periodo,
      profissionalId: profissionalId && profissionalId !== 'todos' ? Number(profissionalId) : null,
    }),
  })
}

export function fetchMedicos() {
  return request('/api/medicos')
}
