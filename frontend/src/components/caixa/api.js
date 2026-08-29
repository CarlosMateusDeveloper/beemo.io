import { apiRequest } from '../../lib/apiClient'

export function fetchTurnoAtual() {
  return apiRequest('/api/caixa/turno-atual')
}

export function registrarPagamento({ idFatura, valor, metodo, parcelas, desconto, motivoDesconto }) {
  return apiRequest('/api/caixa/pagamentos', {
    method: 'POST',
    body: JSON.stringify({ idFatura, valor, metodo, parcelas, desconto, motivoDesconto }),
  })
}

export function fecharTurno({ dinheiroContado, observacao }) {
  return apiRequest('/api/caixa/turno/fechar', {
    method: 'POST',
    body: JSON.stringify({ dinheiroContado, observacao }),
  })
}
