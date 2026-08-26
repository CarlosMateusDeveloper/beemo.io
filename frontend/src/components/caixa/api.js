const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080'

async function request(path, options) {
  const res = await fetch(`${API_BASE}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  })
  if (!res.ok) {
    const body = await res.json().catch(() => ({}))
    throw new Error(body.erro || body.message || `Erro ${res.status} em ${path}`)
  }
  if (res.status === 204) return null
  return res.json()
}

export function fetchTurnoAtual() {
  return request('/api/caixa/turno-atual')
}

export function registrarPagamento({ idFatura, valor, metodo, parcelas, desconto, motivoDesconto }) {
  return request('/api/caixa/pagamentos', {
    method: 'POST',
    body: JSON.stringify({ idFatura, valor, metodo, parcelas, desconto, motivoDesconto }),
  })
}

export function fecharTurno({ dinheiroContado, observacao }) {
  return request('/api/caixa/turno/fechar', {
    method: 'POST',
    body: JSON.stringify({ dinheiroContado, observacao }),
  })
}
