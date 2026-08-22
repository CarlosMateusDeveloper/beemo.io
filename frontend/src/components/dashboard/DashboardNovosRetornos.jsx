import { useMemo } from 'react'
import { fInt } from './dashboardData'

const GW = 600, GH = 158, G_T = 22, G_B = 24
const GPH = GH - G_T - G_B

function Skeleton() {
  return <div className="dashboard-card-skel" />
}

export default function DashboardNovosRetornos({ carregando, empty, dados }) {
  const geo = useMemo(() => {
    if (!dados) return null
    const { meses } = dados
    const novM = meses.map((m) => m.novos)
    const retM = meses.map((m) => m.retornos)
    const gMax = Math.max(...retM, ...novM, 4) * 1.18
    const bars = meses.map((m, i) => {
      const c = (GW * (i + 0.5)) / 3
      const nh = (m.novos / gMax) * GPH, rh = (m.retornos / gMax) * GPH
      const ny = G_T + GPH - nh, ry = G_T + GPH - rh
      return {
        nx: +(c - 40).toFixed(1), ny: +ny.toFixed(1), nh: +nh.toFixed(1),
        nlx: +(c - 21).toFixed(1), nty: +(ny - 7).toFixed(1), nv: fInt(m.novos),
        rx: +(c + 2).toFixed(1), ry: +ry.toFixed(1), rh: +rh.toFixed(1),
        rlx: +(c + 21).toFixed(1), rty: +(ry - 7).toFixed(1), rv: fInt(m.retornos),
        lx: +c.toFixed(1), lb: m.mes,
      }
    })
    const grid = [1, 2].map((k) => ({ y: +(G_T + GPH - (GPH * k) / 3).toFixed(1) }))
    const trend = bars.map((b) => `${b.nlx.toFixed(1)},${(b.ny - 0.5).toFixed(1)}`).join(' ')
    return { bars, grid, trend }
  }, [dados])

  return (
    <section className="dashboard-card" aria-label="Novos pacientes e retornos">
      <div className="dashboard-card-head">
        <span className="dashboard-card-titulo">Novos pacientes × retornos</span>
        <span className="dashboard-legenda-inline">
          <span><i className="dashboard-legend-swatch grad small" />Novos</span>
          <span><i className="dashboard-legend-swatch part small" />Retornos</span>
        </span>
      </div>

      {carregando ? <Skeleton /> : empty || !geo ? (
        <div className="dashboard-chart-vazio small"><div>Sem dados no período selecionado</div></div>
      ) : (
        <>
          <div className="dashboard-destaque-row">
            <span className="dashboard-destaque-num">{dados.novTotTxt}</span>
            <span className="dashboard-destaque-txt">novos pacientes no período</span>
            <span className={`dashboard-kpi-delta ${dados.positivo ? 'success' : 'danger'}`}>
              {dados.positivo ? '▲' : '▼'} {dados.deltaTxt}
            </span>
          </div>

          <svg viewBox={`0 0 ${GW} ${GH}`} preserveAspectRatio="xMidYMid meet" className="dashboard-chart-svg small">
            <defs>
              <linearGradient id="dashGbar2" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0" className="dashboard-grad-a" />
                <stop offset="1" className="dashboard-grad-b" />
              </linearGradient>
            </defs>
            {geo.grid.map((g, i) => <line key={i} x1="28" x2="572" y1={g.y} y2={g.y} className="dashboard-chart-grid" />)}
            <line x1="28" x2="572" y1={G_T + GPH} y2={G_T + GPH} className="dashboard-chart-baseline" />
            {geo.bars.map((b, i) => (
              <g key={i}>
                <rect x={b.nx} y={b.ny} width="38" height={b.nh} rx="4" fill="url(#dashGbar2)" opacity="0.95" />
                <rect x={b.rx} y={b.ry} width="38" height={b.rh} rx="4" className="dashboard-chart-part" />
                <text x={b.rlx} y={b.rty} textAnchor="middle" className="dashboard-chart-axis">{b.rv}</text>
                <text x={b.lx} y={GH - 6} textAnchor="middle" className="dashboard-chart-xlabel">{b.lb}</text>
              </g>
            ))}
            <polyline points={geo.trend} className="dashboard-chart-trend" />
            {geo.bars.map((b, i) => (
              <text key={i} x={b.nlx} y={b.nty} textAnchor="middle" className="dashboard-chart-barlabel accent">{b.nv}</text>
            ))}
          </svg>
        </>
      )}
    </section>
  )
}
