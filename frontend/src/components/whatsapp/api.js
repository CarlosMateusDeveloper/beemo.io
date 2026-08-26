const API_BASE = import.meta.env.VITE_CHATBOT_API_URL || 'http://localhost:8082'

async function request(path, options) {
  const res = await fetch(`${API_BASE}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  })
  if (!res.ok) {
    const body = await res.json().catch(() => ({}))
    throw new Error(body.detail || `Erro ${res.status} em ${path}`)
  }
  if (res.status === 204) return null
  return res.json()
}

export function fetchStatus() {
  return request('/whatsapp/status')
}

export function fetchConversas() {
  return request('/whatsapp/conversas')
}

export function fetchMensagens(conversaId) {
  return request(`/whatsapp/conversas/${conversaId}/mensagens`)
}

export function fetchContexto(conversaId) {
  return request(`/whatsapp/conversas/${conversaId}/contexto`)
}

export function assumirConversa(conversaId, agenteNome) {
  return request(`/whatsapp/conversas/${conversaId}/assumir`, {
    method: 'POST',
    body: JSON.stringify({ agenteNome }),
  })
}

export function devolverConversa(conversaId) {
  return request(`/whatsapp/conversas/${conversaId}/devolver`, { method: 'POST' })
}

export function enviarMensagem(conversaId, texto) {
  return request(`/whatsapp/conversas/${conversaId}/mensagens`, {
    method: 'POST',
    body: JSON.stringify({ texto }),
  })
}

export function fetchAssistente() {
  return request('/whatsapp/assistente')
}

export function alternarCapacidade(capacidadeId, ativo) {
  return request(`/whatsapp/assistente/capacidades/${capacidadeId}`, {
    method: 'PATCH',
    body: JSON.stringify({ ativo }),
  })
}

export function salvarMensagensCapacidade(capacidadeId, campos) {
  return request(`/whatsapp/assistente/mensagens/${capacidadeId}`, {
    method: 'PATCH',
    body: JSON.stringify({ campos }),
  })
}

export function salvarRegras(regras) {
  return request('/whatsapp/assistente/regras', {
    method: 'PATCH',
    body: JSON.stringify(regras),
  })
}

export function fetchDesempenho(periodo) {
  return request(`/whatsapp/desempenho?periodo=${encodeURIComponent(periodo)}`)
}
