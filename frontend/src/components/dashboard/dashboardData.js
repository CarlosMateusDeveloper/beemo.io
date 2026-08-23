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

// Rótulo compacto do eixo de receita do gráfico Faturamento × atendimentos
// (ex.: 2500 -> "R$ 2.5k"). Só entra em "k" a partir de 1000 pra manter os
// steps pequenos de MONEY_STEPS (100/200/250/500) legíveis por extenso.
export function fAxis(v) {
  if (v >= 1000) {
    const k = v / 1000
    return `R$ ${Number.isInteger(k) ? k : k.toFixed(1)}k`
  }
  return `R$ ${Math.round(v)}`
}

// Número fixo de divisões do eixo Y do gráfico — receita e atendimentos
// compartilham a mesma grade horizontal (eixo duplo), só o "step" muda.
const EIXO_DIVISOES = 4

// Acha, em `passos` (uma lista de valores "redondos" ascendente), o menor
// step cujo step*divisões cobre `valor` — vira o topo do eixo Y.
export function axisMax(valor, passos) {
  const passo = stepUp(valor / EIXO_DIVISOES, passos)
  return { max: passo * EIXO_DIVISOES, divs: EIXO_DIVISOES }
}

// Menor valor de `passos` que seja >= valor (fallback: o maior step disponível).
export function stepUp(valor, passos) {
  return passos.find((p) => p >= valor) ?? passos[passos.length - 1]
}
