// Helpers puros da tela /whatsapp. Os dados em si vêm de chatbot/whatsapp_controller
// (ver ./api.js) — nada aqui é mock.

export const PERIODOS_DESEMPENHO = ['Hoje', '7 dias', 'Últimos 30 dias']

export function ordenarConversas(lista) {
  const aguardando = lista.filter((c) => c.estado === 'aguardando').sort((a, b) => b.esperaMin - a.esperaMin)
  const outras = lista.filter((c) => c.estado !== 'aguardando').sort((a, b) => (b.horario || '').localeCompare(a.horario || ''))
  return [...aguardando, ...outras]
}

export const VARIAVEIS = ['nome', 'clinica', 'data', 'hora', 'medico', 'especialidade']

export function preencherVariaveis(texto, vars) {
  return texto.replace(/\{(\w+)\}/g, (m, chave) => (chave in vars ? vars[chave] : m))
}

export function fInt(v) {
  return Math.round(v).toLocaleString('pt-BR')
}
export function pct(v, casas) {
  return v.toLocaleString('pt-BR', { minimumFractionDigits: 0, maximumFractionDigits: casas == null ? 1 : casas }) + '%'
}
export function fDelta(v) {
  return (v > 0 ? '+' : '') + v.toLocaleString('pt-BR', { maximumFractionDigits: 1 }) + '%'
}
