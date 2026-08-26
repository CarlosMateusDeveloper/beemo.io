import { useEffect, useState } from 'react'
import { fInt, pct, fDelta, PERIODOS_DESEMPENHO } from './whatsappData'
import { fetchDesempenho } from './api'

function segAtendente(seg) {
  const min = Math.floor(seg / 60)
  const s = seg % 60
  return `${min} min ${s.toString().padStart(2, '0')} s`
}

function VolumeChart({ byHour, peakLabel, peakPct }) {
  const max = Math.max(...byHour.map((h) => h.bot + h.humano), 1) * 1.15
  return (
    <section className="whatsapp-card whatsapp-chart-card" aria-label="Volume por horário do dia">
      <div className="whatsapp-card-head">
        <span className="whatsapp-card-titulo">Volume por horário do dia</span>
        <span className="whatsapp-peak-nota">{peakLabel} · {pct(peakPct, 0)} de todo o volume</span>
        <span className="whatsapp-flex" />
        <span className="whatsapp-legenda-item"><i className="whatsapp-swatch bot" />resolvido pelo assistente</span>
        <span className="whatsapp-legenda-item"><i className="whatsapp-swatch humano" />foi para atendente</span>
      </div>

      <div className="whatsapp-volume-plot">
        {byHour.map((h) => {
          const total = h.bot + h.humano
          const alturaTotal = (total / max) * 100
          const alturaBot = total ? (h.bot / total) * alturaTotal : 0
          const alturaHumano = alturaTotal - alturaBot
          return (
            <div key={h.hora} className="whatsapp-volume-col" title={`${h.hora} · assistente ${fInt(h.bot)} · atendente ${fInt(h.humano)}`}>
              <div className="whatsapp-volume-barras">
                <span className="whatsapp-volume-barra bot" style={{ height: `${alturaBot}%` }} />
                <span className="whatsapp-volume-barra humano" style={{ height: `${alturaHumano}%`, bottom: `${alturaBot}%` }} />
              </div>
              <span className="whatsapp-volume-label">{h.hora}</span>
            </div>
          )
        })}
      </div>
    </section>
  )
}

function EvolucaoChart({ byMonth }) {
  const W = 1080, H = 70
  const n = byMonth.length
  const stepX = W / n
  const min = Math.min(...byMonth.map((m) => m.pct))
  const max = Math.max(...byMonth.map((m) => m.pct))
  const y = (v) => H - 6 - ((v - min) / Math.max(1, max - min)) * (H - 20)
  const pts = byMonth.map((m, i) => ({ x: stepX * (i + 0.5), y: y(m.pct), m }))
  const linha = pts.map((p) => `${p.x.toFixed(1)},${p.y.toFixed(1)}`).join(' ')
  const area = `${pts[0].x.toFixed(1)},${H} ${linha} ${pts[pts.length - 1].x.toFixed(1)},${H}`
  const deltaPontos = byMonth[byMonth.length - 1].pct - byMonth[0].pct

  return (
    <section className="whatsapp-card whatsapp-chart-card small" aria-label="Evolução mensal">
      <div className="whatsapp-card-head">
        <span className="whatsapp-card-titulo">Resolvidas sem atendente · evolução mensal</span>
        <span className="whatsapp-flex" />
        <span className={`whatsapp-delta-nota ${deltaPontos >= 0 ? 'success' : 'danger'}`}>
          {deltaPontos >= 0 ? '+' : ''}{deltaPontos.toFixed(1)} pontos em {n} meses
        </span>
      </div>

      <svg viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" className="whatsapp-evolucao-svg">
        <polygon points={area} className="whatsapp-evolucao-area" />
        <polyline points={linha} className="whatsapp-evolucao-linha" />
        {pts.map((p, i) => (
          <circle key={i} cx={p.x} cy={p.y} r={i === pts.length - 1 ? 4 : 3.5} className={`whatsapp-evolucao-dot${i === pts.length - 1 ? ' last' : ''}`} />
        ))}
      </svg>
      <div className="whatsapp-evolucao-labels">
        {byMonth.map((m, i) => (
          <span key={`${m.mes}-${i}`} className="whatsapp-evolucao-item">
            <span className="whatsapp-evolucao-mes">{m.mes}</span>
            <span className={i === byMonth.length - 1 ? 'strong' : ''}>{pct(m.pct, 0)}</span>
          </span>
        ))}
      </div>
    </section>
  )
}

export default function WhatsappDesempenho() {
  const [periodo, setPeriodo] = useState('Últimos 30 dias')
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState(null)
  const [d, setD] = useState(null)

  useEffect(() => {
    let cancelado = false
    setCarregando(true)
    setErro(null)
    fetchDesempenho(periodo)
      .then((resp) => { if (!cancelado) setD(resp) })
      .catch((err) => { if (!cancelado) setErro(err.message) })
      .finally(() => { if (!cancelado) setCarregando(false) })
    return () => { cancelado = true }
  }, [periodo])

  const pronto = !carregando && d

  return (
    <div className="whatsapp-desempenho">
      <div className="whatsapp-desempenho-head">
        <div className="whatsapp-seg">
          {PERIODOS_DESEMPENHO.map((p) => (
            <label key={p} className={`whatsapp-seg-opt${periodo === p ? ' active' : ''}`}>
              <input type="radio" name="whatsapp-periodo" className="whatsapp-sr-only" checked={periodo === p} onChange={() => setPeriodo(p)} />
              {p}
            </label>
          ))}
        </div>
      </div>

      {erro && <div className="dashboard-erro">Não foi possível carregar o desempenho ({erro}).</div>}

      <div className="whatsapp-desemp-row hero">
        <div className="whatsapp-card whatsapp-hero-card">
          {!pronto ? <div className="whatsapp-card-skel" /> : (
            <>
              <span className="whatsapp-hero-num">{fInt(d.resolvidasSemAtendente)}</span>
              <span className="whatsapp-hero-txt">
                <span className="whatsapp-hero-linha1">conversas resolvidas sem atendente</span>
                <span className="whatsapp-hero-linha2">{pct(d.sharePct, 0)} do total · {fDelta(d.deltaPct)} vs. período anterior</span>
                <span className="whatsapp-hero-linha3">de {fInt(d.total)} conversas · {periodo.toLowerCase()}</span>
              </span>
              <span className="whatsapp-flex" />
              <span className="whatsapp-hero-barra-col">
                <span className="whatsapp-hero-barra">
                  <span style={{ width: `${d.sharePct}%` }} className="bot" />
                  <span style={{ width: `${100 - d.sharePct}%` }} className="humano" />
                </span>
                <span className="whatsapp-hero-legenda">
                  <span><i className="whatsapp-swatch bot" />assistente {pct(d.sharePct, 0)}</span>
                  <span><i className="whatsapp-swatch humano" />atendente {pct(100 - d.sharePct, 0)}</span>
                </span>
              </span>
            </>
          )}
        </div>

        <div className="whatsapp-card whatsapp-agora-card">
          {!pronto ? <div className="whatsapp-card-skel" /> : (
            <>
              <span className="whatsapp-card-nota upper">agora</span>
              <span className="whatsapp-agora-num-row">
                <span className="whatsapp-agora-num">{d.aguardandoAgora}</span>
                <span>conversas aguardando atendente</span>
              </span>
              <span className="whatsapp-card-nota">espera média hoje · {d.esperaMediaHojeMin} min</span>
            </>
          )}
        </div>
      </div>

      <div className="whatsapp-desemp-row tres">
        <div className="whatsapp-card">
          {!pronto ? <div className="whatsapp-card-skel" /> : (
            <>
              <span className="whatsapp-card-nota upper">foram para atendente</span>
              <span className="whatsapp-agora-num-row"><span className="whatsapp-metric-num">{fInt(d.escalonamento.total)}</span><span className="whatsapp-card-nota">{pct(100 - d.sharePct, 0)} do total</span></span>
              <div className="whatsapp-breakdown">
                {d.escalonamento.motivos.length === 0 ? (
                  <span className="whatsapp-campo-nota">Sem detalhamento por motivo ainda</span>
                ) : d.escalonamento.motivos.map((m) => (
                  <div key={m.motivo} className="whatsapp-breakdown-linha">
                    <span className="whatsapp-breakdown-label">{m.motivo}</span>
                    <span className="whatsapp-breakdown-barra"><span style={{ width: `${(m.qtd / (d.escalonamento.total || 1)) * 100}%` }} /></span>
                    <span className="whatsapp-breakdown-valor">{fInt(m.qtd)}</span>
                  </div>
                ))}
              </div>
            </>
          )}
        </div>

        <div className="whatsapp-card">
          {!pronto ? <div className="whatsapp-card-skel" /> : (
            <>
              <span className="whatsapp-card-nota upper">tempo até primeira resposta</span>
              <span className="whatsapp-agora-num-row"><span className="whatsapp-metric-num">{d.tempoPrimeiraRespostaSeg}</span><span>segundos</span></span>
              <span className="whatsapp-card-nota">mediana do assistente · 24 h por dia</span>
              <span className="whatsapp-divisor" />
              <div className="whatsapp-linha-valor small">
                <span>quando vai para atendente</span>
                <span className="whatsapp-valor-amber">{segAtendente(d.tempoPrimeiraRespostaAtendenteSeg)}</span>
              </div>
            </>
          )}
        </div>

        <div className="whatsapp-card">
          {!pronto ? <div className="whatsapp-card-skel" /> : (
            <>
              <span className="whatsapp-card-nota upper">ações concluídas</span>
              <span className="whatsapp-agora-num-row"><span className="whatsapp-metric-num">{fInt(d.acoes.total)}</span><span className="whatsapp-card-nota">pelo assistente, sem recepção</span></span>
              <div className="whatsapp-acoes-grid">
                {d.acoes.porTipo.map((a) => (
                  <span key={a.tipo} className="whatsapp-acao-item"><b>{fInt(a.qtd)}</b>{a.tipo.toLowerCase()}</span>
                ))}
              </div>
            </>
          )}
        </div>
      </div>

      {!pronto ? <div className="whatsapp-card whatsapp-chart-card"><div className="whatsapp-card-skel" /></div>
        : <VolumeChart byHour={d.byHour} peakLabel={d.peakLabel} peakPct={d.peakPct} />}

      {!pronto ? <div className="whatsapp-card whatsapp-chart-card small"><div className="whatsapp-card-skel" /></div>
        : <EvolucaoChart byMonth={d.byMonth} />}
    </div>
  )
}
