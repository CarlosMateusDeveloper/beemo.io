// Formatação e constantes de UI da tela /medicos. Os dados vêm de
// POST /api/medicos/painel e GET /api/especialidades (ver ./api.js).

export const VOLUME_MINIMO_PADRAO = 10

export const PERIODOS = ['Hoje', '7 dias', 'Mês', 'Personalizado']

export function brl(n) {
  return n.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL', maximumFractionDigits: 0 })
}

export function pct(n) {
  return Math.round(n) + '%'
}

export function horasFmt(n) {
  return (Number.isInteger(n) ? String(n) : n.toFixed(1).replace('.', ',')) + 'h'
}

export function iniciaisDe(nome) {
  return nome.split(' ').map((p) => p[0]).slice(0, 2).join('').toUpperCase()
}
