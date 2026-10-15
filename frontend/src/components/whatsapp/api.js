import { authenticatedRequest, serviceBase } from '../../lib/apiClient'
const API_BASE = serviceBase(import.meta.env.VITE_CHATBOT_API_URL, 8082)

async function request(path, options) {
  return authenticatedRequest(`${API_BASE}${path}`, options)
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

export function enviarMensagem(conversaId, texto, idempotencyKey) {
  return request(`/whatsapp/conversas/${conversaId}/mensagens`, {
    method: 'POST',
    body: JSON.stringify({ texto, idempotencyKey }),
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

export function fetchWhatsappAtualizacoes(after) {
  const query = after ? `?after=${encodeURIComponent(after)}` : ''
  return request(`/whatsapp/atualizacoes${query}`)
}

export function fetchMensageriaResumo() {
  return request('/whatsapp/operacao/mensageria/resumo')
}

export function fetchMensageria(status) {
  const query = status ? `?status=${encodeURIComponent(status)}` : ''
  return request(`/whatsapp/operacao/mensageria${query}`)
}

export function reprocessarMensagem(outboxId) {
  return request(`/whatsapp/operacao/mensageria/${outboxId}/reprocessar`, { method: 'POST' })
}

export function fetchWhatsappConfiguracao() {
  return request('/whatsapp/configuracao')
}

export function salvarWhatsappConfiguracao(data) {
  return request('/whatsapp/configuracao', { method: 'PUT', body: JSON.stringify(data) })
}

export function prepararWhatsappPosVenda(caseId) {
  return request(`/whatsapp/pos-venda/casos/${caseId}`)
}

export function enviarWhatsappPosVenda(caseId, data) {
  return request(`/whatsapp/pos-venda/casos/${caseId}/mensagens`, { method: 'POST', body: JSON.stringify(data) })
}
