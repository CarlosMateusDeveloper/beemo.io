const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080'

async function request(path, options) {
  const res = await fetch(`${API_BASE}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  })
  if (!res.ok) {
    const body = await res.json().catch(() => ({}))
    throw new Error(body.message || body.erro || `Erro ${res.status} em ${path}`)
  }
  if (res.status === 204) return null
  return res.json()
}

export function fetchPacientesKpis() {
  return request('/api/pacientes/kpis')
}

export function fetchPacientesListagem() {
  return request('/api/pacientes/listagem')
}

export function fetchPacientesFila() {
  return request('/api/pacientes/fila')
}

export function criarPaciente(dados) {
  return request('/api/pacientes', { method: 'POST', body: JSON.stringify(dados) })
}

export function fetchConvenios() {
  return request('/api/convenios')
}
