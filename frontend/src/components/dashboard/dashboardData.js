// Formatação usada pela tela /dashboard. Os dados em si vêm de
// POST /api/dashboard (ver ./api.js) — nada aqui é mock.

export const PERIODOS = ['Hoje', '7 dias', 'Mês', 'Personalizado']

export function fInt(v) {
  return Math.round(v).toLocaleString('pt-BR')
}
export function brl(v) {
  return 'R$ ' + fInt(v)
}
export function pct(v, casas) {
  return v.toLocaleString('pt-BR', { minimumFractionDigits: 0, maximumFractionDigits: casas == null ? 1 : casas }) + '%'
}
