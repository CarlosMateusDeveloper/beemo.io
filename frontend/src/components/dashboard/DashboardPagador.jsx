const R = 62
const CIRC = 2 * Math.PI * R

function Skeleton() {
  return <div className="dashboard-card-skel" />
}

export default function DashboardPagador({ carregando, empty, dados }) {
  const convDash = dados ? `${Math.max(0, (CIRC * dados.convPct) / 100 - 4).toFixed(1)} ${CIRC.toFixed(1)}` : ''
  const partDash = dados ? `${Math.max(0, (CIRC * dados.partPct) / 100 - 4).toFixed(1)} ${CIRC.toFixed(1)}` : ''
  const partRot = dados ? `rotate(${(-90 + 3.6 * dados.convPct).toFixed(2)} 88 88)` : ''

  return (
    <section className="dashboard-card" aria-label="Distribuição por pagador">
      <span className="dashboard-card-titulo">Distribuição por pagador</span>

      {carregando ? <Skeleton /> : empty ? (
        <div className="dashboard-chart-vazio small"><div>Sem dados no período selecionado</div></div>
      ) : (
        <>
          <div className="dashboard-donut-row">
            <div className="dashboard-donut-wrap">
              <svg viewBox="0 0 176 176" className="dashboard-donut-svg">
                <circle cx="88" cy="88" r={R} fill="none" className="dashboard-donut-trilha" strokeWidth="22" />
                <circle cx="88" cy="88" r={R} fill="none" className="dashboard-donut-conv" strokeWidth="22" strokeDasharray={convDash} transform="rotate(-90 88 88)" />
                <circle cx="88" cy="88" r={R} fill="none" className="dashboard-donut-part" strokeWidth="22" strokeDasharray={partDash} transform={partRot} />
              </svg>
              <div className="dashboard-donut-centro">
                <span className="dashboard-donut-pct">{dados.convPctTxt}</span>
                <span className="dashboard-donut-rot">convênio</span>
              </div>
            </div>
            <div className="dashboard-donut-legenda">
              <div className="dashboard-donut-item">
                <span className="dashboard-donut-item-head"><i className="dashboard-donut-bar conv" />Convênio</span>
                <span className="dashboard-donut-item-val">
                  <b>{dados.convValTxt}</b>
                  <em>{dados.convPctTxt}</em>
                </span>
              </div>
              <div className="dashboard-donut-item">
                <span className="dashboard-donut-item-head"><i className="dashboard-donut-bar part" />Particular</span>
                <span className="dashboard-donut-item-val">
                  <b>{dados.partValTxt}</b>
                  <em>{dados.partPctTxt}</em>
                </span>
              </div>
            </div>
          </div>

          <div className="dashboard-procs">
            <div className="dashboard-procs-titulo">Top 3 procedimentos por receita</div>
            {dados.procs.map((p) => (
              <div key={p.nome} className="dashboard-proc-item">
                <div className="dashboard-proc-linha"><span>{p.nome}</span><span>{p.valorTxt}</span></div>
                <div className="dashboard-proc-barra"><div className="dashboard-proc-barra-fill" style={{ width: p.barraPct }} /></div>
              </div>
            ))}
          </div>
        </>
      )}
    </section>
  )
}
