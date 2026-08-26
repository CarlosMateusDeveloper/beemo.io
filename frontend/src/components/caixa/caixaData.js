// Helpers puros da tela /caixa. Os dados em si vêm de GET/POST /api/caixa/*
// (ver ./api.js) — nada aqui é mock.

export const METODOS = [
  { key: 'dinheiro', label: 'Dinheiro' },
  { key: 'debito', label: 'Débito' },
  { key: 'credito', label: 'Crédito' },
  { key: 'pix', label: 'Pix' },
]

export function brl(valor) {
  return valor.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

export function formatNum(valor) {
  return Number(valor).toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

export function parseValorInput(raw) {
  const v = parseFloat(String(raw ?? '').replace(/\s/g, '').replace(/\./g, '').replace(',', '.'))
  return isNaN(v) ? 0 : v
}
