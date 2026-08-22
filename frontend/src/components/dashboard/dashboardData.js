// Dataset mock da tela /dashboard ("Visão geral"). Sem endpoint de backend
// ainda — em produção, GET /dashboard?periodo=&profissionalId= deve devolver
// os agregados já calculados pelo servidor (ver README do handoff de design
// em docs/ — este mock replica os números de referência de lá: R$ 84.320 no
// mês, 78% de ocupação, 14% de no-show).

export const PERIODOS = ['Hoje', '7 dias', 'Mês', 'Personalizado']

const PERIODO_KEY = { Hoje: 'hoje', '7 dias': 'd7', Mês: 'mes', Personalizado: 'custom' }
const FATOR_PERIODO = { mes: 1, d7: 0.26, hoje: 0.048 }
const DELTA_INDEX = { mes: 0, d7: 1, hoje: 2 }

const SERIES = {
  mes: { labels: ['Semana 1', 'Semana 2', 'Semana 3', 'Semana 4'], shares: [0.225, 0.245, 0.255, 0.275], unidade: 'por semana' },
  d7: { labels: ['ter 28', 'qua 29', 'qui 30', 'sex 31', 'sáb 01', 'dom 02', 'seg 03'], shares: [0.165, 0.17, 0.16, 0.175, 0.115, 0, 0.215], unidade: 'por dia' },
  hoje: { labels: ['8h', '10h', '12h', '14h', '16h', '18h'], shares: [0.22, 0.24, 0.10, 0.18, 0.16, 0.10], unidade: 'por horário' },
}

export const META_MENSAL = 90000
const TICKET_MEDIO = 300

export const PROFISSIONAIS = [
  { id: 'ana', nome: 'Dra. Ana Beltrão', iniciais: 'AB', especialidade: 'Cardiologia',
    receita: 21440, atendimentos: 74, faltas: 8, cancelamentos: 5, horarios: 96, convenioPct: 0.58,
    deltaReceita: [15.2, 4.2, -6.2], mix: [0.42, 0.20, 0.14], novos: [11, 13, 15], retornos: [52, 55, 58] },
  { id: 'carlos', nome: 'Dr. Carlos Menezes', iniciais: 'CM', especialidade: 'Ortopedia',
    receita: 18760, atendimentos: 62, faltas: 9, cancelamentos: 6, horarios: 88, convenioPct: 0.66,
    deltaReceita: [9.8, -2.1, 3.1], mix: [0.38, 0.26, 0.12], novos: [9, 10, 12], retornos: [44, 46, 48] },
  { id: 'fernanda', nome: 'Dra. Fernanda Ruiz', iniciais: 'FR', especialidade: 'Dermatologia',
    receita: 16480, atendimentos: 52, faltas: 11, cancelamentos: 4, horarios: 80, convenioPct: 0.71,
    deltaReceita: [18.4, 6.8, -1.8], mix: [0.30, 0.18, 0.28], novos: [7, 9, 10], retornos: [38, 40, 41] },
  { id: 'joao', nome: 'Dr. João Prado', iniciais: 'JP', especialidade: 'Clínica Geral',
    receita: 14980, atendimentos: 44, faltas: 7, cancelamentos: 5, horarios: 72, convenioPct: 0.55,
    deltaReceita: [-4.6, 1.5, -8.4], mix: [0.45, 0.22, 0.10], novos: [6, 7, 8], retornos: [34, 36, 36] },
  { id: 'marina', nome: 'Dra. Marina Costa', iniciais: 'MC', especialidade: 'Pediatria',
    receita: 12660, atendimentos: 36, faltas: 9, cancelamentos: 4, horarios: 64, convenioPct: 0.61,
    deltaReceita: [11.9, -3.4, 5.6], mix: [0.36, 0.24, 0.16], novos: [5, 6, 7], retornos: [30, 33, 33] },
]

const MESES_NOVOS_RETORNOS = ['jun', 'jul', 'ago']
const CATEGORIAS_PROCEDIMENTO = ['Consultas', 'Exames de imagem', 'Pequenos procedimentos']

export function fInt(v) {
  return Math.round(v).toLocaleString('pt-BR')
}
export function brl(v) {
  return 'R$ ' + fInt(v)
}
export function pct(v, casas) {
  return v.toLocaleString('pt-BR', { minimumFractionDigits: 0, maximumFractionDigits: casas == null ? 1 : casas }) + '%'
}
export function fAxis(v) {
  return v >= 1000 ? (v / 1000).toLocaleString('pt-BR', { maximumFractionDigits: 1 }) + ' mil' : fInt(v)
}
export function fDelta(v) {
  return (v > 0 ? '+' : '') + v.toLocaleString('pt-BR', { maximumFractionDigits: 1 }) + '%'
}

export function dist(total, shares) {
  const out = []
  let acc = 0
  for (let i = 0; i < shares.length; i++) {
    if (i === shares.length - 1) out.push(Math.max(0, total - acc))
    else { const v = Math.round(total * shares[i]); out.push(v); acc += v }
  }
  return out
}

export function axisMax(v, steps) {
  for (const s of steps) { const d = Math.ceil(v / s); if (d >= 2 && d <= 5) return { max: s * d, divs: d } }
  const s = steps[steps.length - 1]
  const d = Math.max(2, Math.ceil(v / s))
  return { max: s * d, divs: d }
}

export function stepUp(v, steps) {
  for (const s of steps) if (s >= v) return s
  return steps[steps.length - 1]
}

// Calcula todos os agregados da tela para um período + filtro de profissional.
// Em produção isto é a resposta (já pronta) de GET /dashboard.
export function calcularDashboard({ periodo, profissionalId }) {
  const per = PERIODO_KEY[periodo] ?? 'mes'
  const empty = per === 'custom'
  const pk = empty ? 'mes' : per
  const f = FATOR_PERIODO[pk]
  const di = DELTA_INDEX[pk]
  const sel = profissionalId === 'todos' ? PROFISSIONAIS : PROFISSIONAIS.filter((p) => p.id === profissionalId)
  const sc = (v) => Math.round(v * f)

  const rev = sel.reduce((a, p) => a + sc(p.receita), 0)
  const att = sel.reduce((a, p) => a + sc(p.atendimentos), 0)
  const fal = sel.reduce((a, p) => a + sc(p.faltas), 0)
  const can = sel.reduce((a, p) => a + sc(p.cancelamentos), 0)
  const preench = att + fal
  const slots = sel.reduce((a, p) => a + ({ mes: p.horarios, d7: Math.round(p.horarios / 4), hoje: Math.max(2, Math.round(p.horarios / 22)) })[pk], 0)
  const prevRev = sel.reduce((a, p) => a + sc(p.receita) / (1 + p.deltaReceita[di] / 100), 0)
  const deltaRev = prevRev ? (rev / prevRev - 1) * 100 : 0
  const ocup = slots ? (preench / slots) * 100 : 0
  const ns = preench ? (fal / preench) * 100 : 0
  const noShowCor = ns > 20 ? 'danger' : ns >= 10 ? 'warning' : 'neutral'

  const prevLbl = { mes: 'vs. mês anterior', d7: 'vs. 7 dias anteriores', hoje: 'vs. ontem' }[pk]
  const tituloFaturamento = { mes: 'Faturamento do mês', d7: 'Faturamento — 7 dias', hoje: 'Faturamento de hoje' }[pk]

  const def = SERIES[pk]
  const revPts = dist(rev, def.shares)
  const attPts = dist(att, def.shares)
  const falPts = dist(fal, def.shares)
  const canPts = dist(can, def.shares)
  const serie = def.labels.map((label, i) => ({
    label, receita: revPts[i], atendimentos: attPts[i], cancelamentos: canPts[i], faltas: falPts[i],
  }))

  const metaSemanal = META_MENSAL / 4
  const mostrarMeta = pk === 'mes' && profissionalId === 'todos' && !empty

  const convRev = sel.reduce((a, p) => a + sc(p.receita) * p.convenioPct, 0)
  const partRev = rev - convRev
  const convPct = rev ? (convRev / rev) * 100 : 0

  const procsBrutos = CATEGORIAS_PROCEDIMENTO
    .map((nome, ci) => ({ nome, valor: sel.reduce((a, p) => a + sc(p.receita) * p.mix[ci], 0) }))
    .sort((a, b) => b.valor - a.valor)
  const procMax = Math.max(1, procsBrutos[0]?.valor || 0)
  const procs = procsBrutos.map((x) => ({ nome: x.nome, valorTxt: brl(x.valor), barraPct: `${(x.valor / procMax * 100).toFixed(0)}%` }))

  const rankBruto = sel.map((p) => {
    const r = sc(p.receita), a = sc(p.atendimentos), fl = sc(p.faltas), pr = a + fl
    return { p, r, a, nsv: pr ? (fl / pr) * 100 : 0 }
  }).sort((x, y) => y.r - x.r)
  const maxR = rankBruto.length ? (rankBruto[0].r || 1) : 1
  const ranking = rankBruto.slice(0, 5).map((x) => ({
    id: x.p.id, nome: x.p.nome, iniciais: x.p.iniciais,
    atendimentosTxt: fInt(x.a), receitaTxt: brl(x.r),
    noShowTxt: pct(x.nsv), noShowCor: x.nsv > 20 ? 'danger' : x.nsv >= 10 ? 'warning' : 'neutral',
    barraPct: `${(x.r / maxR * 100).toFixed(0)}%`,
  }))

  const novM = MESES_NOVOS_RETORNOS.map((_, i) => sel.reduce((a, p) => a + p.novos[i], 0))
  const retM = MESES_NOVOS_RETORNOS.map((_, i) => sel.reduce((a, p) => a + p.retornos[i], 0))
  const novTot = Math.max(1, Math.round(novM[2] * f))
  const deltaNovos = novM[1] ? ((novM[2] - novM[1]) / novM[1]) * 100 : 0
  const meses = MESES_NOVOS_RETORNOS.map((mes, i) => ({ mes, novos: novM[i], retornos: retM[i] }))

  return {
    empty,
    unidade: def.unidade,
    profNome: sel.length === 1 ? sel[0].nome : null,
    kpi: {
      faturamento: { titulo: tituloFaturamento, valorTxt: brl(rev), deltaTxt: `${fDelta(deltaRev)} ${prevLbl}`, positivo: deltaRev >= 0 },
      ocupacao: { valorTxt: pct(ocup, 0), barraPct: `${Math.min(100, ocup).toFixed(1)}%`, apoio: `${fInt(preench)} de ${fInt(slots)} horários preenchidos` },
      noShow: { valorTxt: pct(ns), apoio: `${fInt(fal)} faltas no período`, cor: noShowCor, custoTxt: brl(fal * TICKET_MEDIO) },
    },
    serie,
    metaSemanal: mostrarMeta ? metaSemanal : null,
    pagador: {
      convPct, partPct: 100 - convPct,
      convPctTxt: pct(convPct, 0), partPctTxt: pct(100 - convPct, 0),
      convValTxt: brl(convRev), partValTxt: brl(partRev),
      procs,
    },
    ranking,
    novosRetornos: {
      novTotTxt: fInt(novTot),
      deltaTxt: `${fDelta(deltaNovos)} vs. mês anterior`,
      positivo: deltaNovos >= 0,
      meses,
    },
  }
}
