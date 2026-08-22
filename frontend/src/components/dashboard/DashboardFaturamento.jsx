import { useMemo, useState } from 'react'
import { axisMax, stepUp, fAxis, fInt, brl } from './dashboardData'

const W = 920, H = 310
const PAD_L = 64, PAD_T = 18, PAD_B = 34
const PW = 802, PH = H - PAD_T - PAD_B

const MONEY_STEPS = [100, 200, 250, 500, 1000, 2000, 2500, 5000, 10000, 20000, 25000, 50000]
const COUNT_STEPS_A = [2, 4, 5, 10, 20, 25, 50, 100]
const COUNT_STEPS_S = [2, 4, 5, 10, 20, 25, 50, 100, 200]

function Skeleton() {
  return <div className="dashboard-card-skel" />
}

function EstadoVazio() {
  return (
    <div className="dashboard-chart-vazio">
      <div>Sem dados no período selecionado</div>
      <span>Ajuste o período ou o profissional para atualizar este bloco.</span>
    </div>
  )
}

export default function DashboardFaturamento({ carregando, empty, unidade, serie, metaSemanal }) {
  const [view, setView] = useState('receita')
  const [tip, setTip] = useState(null)

  const geo = useMemo(() => {
    if (!serie || serie.length === 0) return null
    const n = serie.length
    const xs = PW / n
    const revPts = serie.map((s) => s.receita)
    const attPts = serie.map((s) => s.atendimentos)
    const falPts = serie.map((s) => s.faltas)
    const canPts = serie.map((s) => s.cancelamentos)

    const revTop = Math.max(...revPts, metaSemanal || 0) * 1.06
    const ax = axisMax(Math.max(revTop, 10), MONEY_STEPS)
    const attTop = Math.max(...attPts, 1) * 1.15
    const aStep = stepUp(Math.max(1, Math.ceil(attTop / ax.divs)), COUNT_STEPS_A)
    const attMax = aStep * ax.divs
    const schedPts = attPts.map((v, i) => v + canPts[i] + falPts[i])
    const sx = axisMax(Math.max(...schedPts, 4) * 1.12, COUNT_STEPS_S)

    const yRev = (v) => PAD_T + PH - (v / ax.max) * PH
    const yAtt = (v) => PAD_T + PH - (v / attMax) * PH

    const divs = view === 'receita' ? ax.divs : sx.divs
    const grid = []
    for (let i = 1; i <= divs; i++) {
      grid.push({
        y: +(PAD_T + PH - (PH * i) / divs).toFixed(1),
        yl: view === 'receita' ? fAxis((ax.max * i) / divs) : fInt((sx.max * i) / divs),
        yr: view === 'receita' ? fInt((attMax * i) / divs) : '',
      })
    }

    const bw = Math.min(74, xs * 0.42)
    const op = (i) => (tip == null ? 0.9 : tip === i ? 1 : 0.32)
    const bars = revPts.map((v, i) => {
      const y = yRev(v), h = (v / ax.max) * PH, cx = PAD_L + xs * (i + 0.5)
      const inside = h >= 30
      return {
        x: +(cx - bw / 2).toFixed(1), y: +y.toFixed(1), w: +bw.toFixed(1), h: +h.toFixed(1), cx: +cx.toFixed(1),
        opacity: op(i), label: fAxis(v),
        labelY: +(inside ? y + 17 : y - 8).toFixed(1), labelInside: inside,
      }
    })
    const pts = attPts.map((v, i) => `${(PAD_L + xs * (i + 0.5)).toFixed(1)},${yAtt(v).toFixed(1)}`).join(' ')
    const dots = attPts.map((v, i) => ({
      cx: +(PAD_L + xs * (i + 0.5)).toFixed(1), cy: +yAtt(v).toFixed(1),
      r: tip === i ? 5.5 : 3, opacity: tip == null ? 1 : tip === i ? 1 : 0.45,
    }))
    const sBars = schedPts.map((total, i) => {
      const cx = PAD_L + xs * (i + 0.5)
      const hA = (attPts[i] / sx.max) * PH, hC = (canPts[i] / sx.max) * PH, hF = (falPts[i] / sx.max) * PH
      const yA = PAD_T + PH - hA, yC = yA - hC, yF = yC - hF
      return {
        x: +(cx - bw / 2).toFixed(1), w: +bw.toFixed(1), cx: +cx.toFixed(1), opacity: op(i),
        ay: +yA.toFixed(1), ah: +hA.toFixed(1), cy: +yC.toFixed(1), ch: +hC.toFixed(1), fy: +yF.toFixed(1), fh: +hF.toFixed(1),
        label: fInt(total), labelY: +(yF - 8).toFixed(1),
      }
    })
    const xlabs = serie.map((s, i) => ({ x: +(PAD_L + xs * (i + 0.5)).toFixed(1), t: s.label }))
    const cols = serie.map((s, i) => ({ x: +(PAD_L + xs * i).toFixed(1), w: +xs.toFixed(1), i }))
    const metaY = metaSemanal != null ? +yRev(metaSemanal).toFixed(1) : null

    return { grid, bars, pts, dots, sBars, xlabs, cols, metaY, bw }
  }, [serie, metaSemanal, view, tip])

  const tooltip = useMemo(() => {
    if (tip == null || !serie || !geo) return null
    const s = serie[tip]
    const cx = geo.xlabs[tip].x
    const left = Math.min(86, Math.max(13, (cx / W) * 100))
    const topY = view === 'receita' ? Math.min(geo.bars[tip].y, geo.dots[tip].cy) : geo.sBars[tip].fy
    const top = Math.min(58, Math.max(5, (topY / H) * 100 + 3))
    return { label: s.label, left: `${left}%`, top: `${top}%`, receita: brl(s.receita), atendimentos: fInt(s.atendimentos), cancelamentos: fInt(s.cancelamentos), faltas: fInt(s.faltas) }
  }, [tip, serie, geo, view])

  return (
    <section className="dashboard-card dashboard-card-chart" aria-label="Faturamento e atendimentos">
      <div className="dashboard-card-head">
        <span className="dashboard-card-titulo">Faturamento × atendimentos {!carregando && !empty && <span className="dashboard-card-unidade">· {unidade}</span>}</span>
        <div className="dashboard-seg-mini">
          <label className={`dashboard-seg-mini-opt${view === 'receita' ? ' active' : ''}`}>
            <input type="radio" name="dashboard-view" className="dashboard-sr-only" checked={view === 'receita'} onChange={() => { setView('receita'); setTip(null) }} />
            Receita
          </label>
          <label className={`dashboard-seg-mini-opt${view === 'agendamentos' ? ' active' : ''}`}>
            <input type="radio" name="dashboard-view" className="dashboard-sr-only" checked={view === 'agendamentos'} onChange={() => { setView('agendamentos'); setTip(null) }} />
            Agendamentos
          </label>
        </div>
      </div>

      {carregando ? <Skeleton /> : empty || !geo ? <EstadoVazio /> : (
        <>
          <div className="dashboard-chart-wrap" onMouseLeave={() => setTip(null)}>
            <svg viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="xMidYMid meet" className="dashboard-chart-svg">
              <defs>
                <linearGradient id="dashGbar" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0" className="dashboard-grad-a" />
                  <stop offset="1" className="dashboard-grad-b" />
                </linearGradient>
              </defs>
              {geo.grid.map((g, i) => (
                <g key={i}>
                  <line x1={PAD_L} x2={W - 54} y1={g.y} y2={g.y} className="dashboard-chart-grid" />
                  <text x={PAD_L - 8} y={g.y} textAnchor="end" dominantBaseline="middle" className="dashboard-chart-axis">{g.yl}</text>
                  {view === 'receita' && <text x={W - 46} y={g.y} textAnchor="start" dominantBaseline="middle" className="dashboard-chart-axis">{g.yr}</text>}
                </g>
              ))}
              <line x1={PAD_L} x2={W - 54} y1={PAD_T + PH} y2={PAD_T + PH} className="dashboard-chart-baseline" />

              {view === 'receita' ? (
                <>
                  {geo.bars.map((b, i) => (
                    <g key={i}>
                      <rect x={b.x} y={b.y} width={b.w} height={b.h} rx="4" fill="url(#dashGbar)" opacity={b.opacity} />
                      <text x={b.cx} y={b.labelY} textAnchor="middle" opacity={b.opacity} className={`dashboard-chart-barlabel${b.labelInside ? ' inside' : ''}`}>{b.label}</text>
                    </g>
                  ))}
                  {geo.metaY != null && (
                    <>
                      <line x1={PAD_L} x2={W - 54} y1={geo.metaY} y2={geo.metaY} className="dashboard-chart-meta" />
                      <text x={W - 54} y={geo.metaY - 6} textAnchor="end" className="dashboard-chart-metalbl">meta semanal</text>
                    </>
                  )}
                  <polyline points={geo.pts} className="dashboard-chart-line" />
                  {geo.dots.map((d, i) => (
                    <circle key={i} cx={d.cx} cy={d.cy} r={d.r} opacity={d.opacity} className="dashboard-chart-dot" />
                  ))}
                </>
              ) : (
                geo.sBars.map((b, i) => (
                  <g key={i}>
                    <rect x={b.x} y={b.ay} width={b.w} height={b.ah} fill="url(#dashGbar)" opacity={b.opacity} />
                    <rect x={b.x} y={b.cy} width={b.w} height={b.ch} opacity={b.opacity} className="dashboard-chart-canc" />
                    <rect x={b.x} y={b.fy} width={b.w} height={b.fh} opacity={b.opacity} className="dashboard-chart-falta" />
                    <text x={b.cx} y={b.labelY} textAnchor="middle" opacity={b.opacity} className="dashboard-chart-barlabel">{b.label}</text>
                  </g>
                ))
              )}

              {geo.xlabs.map((x, i) => (
                <text key={i} x={x.x} y={H - 10} textAnchor="middle" className="dashboard-chart-xlabel">{x.t}</text>
              ))}
              {geo.cols.map((c, i) => (
                <rect key={i} x={c.x} y={PAD_T} width={c.w} height={PH} fill="transparent" onMouseEnter={() => setTip(c.i)} />
              ))}
            </svg>

            {tooltip && (
              <div className="dashboard-chart-tooltip" style={{ left: tooltip.left, top: tooltip.top }}>
                <div className="dashboard-chart-tooltip-lab">{tooltip.label}</div>
                <div className="dashboard-chart-tooltip-grid">
                  <span>Receita</span><span className="strong">{tooltip.receita}</span>
                  <span>Atendimentos</span><span>{tooltip.atendimentos}</span>
                  <span>Cancelamentos</span><span>{tooltip.cancelamentos}</span>
                  <span>Faltas</span><span>{tooltip.faltas}</span>
                </div>
              </div>
            )}
          </div>

          <div className="dashboard-chart-legenda">
            {view === 'receita' ? (
              <>
                <span><i className="dashboard-legend-swatch grad" />Receita (R$)</span>
                <span><i className="dashboard-legend-swatch linha" />Atendimentos</span>
              </>
            ) : (
              <>
                <span><i className="dashboard-legend-swatch grad" />Realizados</span>
                <span><i className="dashboard-legend-swatch canc" />Cancelados</span>
                <span><i className="dashboard-legend-swatch falta" />Faltas</span>
              </>
            )}
          </div>
        </>
      )}
    </section>
  )
}
