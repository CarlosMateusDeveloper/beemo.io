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

export function fetchMedicosPainel(periodo) {
  return request('/api/medicos/painel', {
    method: 'POST',
    body: JSON.stringify({ periodo }),
  })
}

export function fetchEspecialidades() {
  return request('/api/especialidades')
}
