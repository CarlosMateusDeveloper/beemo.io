import { fInt } from './dashboardData'

function Skeleton() {
  return <div className="dashboard-card-skel" />
}

export default function DashboardNovosRetornos({ carregando, empty, dados }) {
  const total = dados ? dados.novos + dados.retornos : 0
  const novosPct = total ? (dados.novos / total) * 100 : 0

  return (
    <section className="dashboard-card" aria-label="Novos pacientes e retornos">
      <div className="dashboard-card-head">
        <span className="dashboard-card-titulo">Novos pacientes × retornos</span>
      </div>

      {carregando ? <Skeleton /> : empty || !dados || total === 0 ? (
        <div className="dashboard-chart-vazio small"><div>Sem dados no período selecionado</div></div>
      ) : (
        <>
          <div className="dashboard-nr-row">
            <div className="dashboard-nr-stat">
              <span className="dashboard-nr-num">{fInt(dados.novos)}</span>
              <span className="dashboard-nr-lab"><i className="dashboard-legend-swatch grad small" />novos</span>
            </div>
            <div className="dashboard-nr-stat">
              <span className="dashboard-nr-num">{fInt(dados.retornos)}</span>
              <span className="dashboard-nr-lab"><i className="dashboard-legend-swatch part small" />retornos</span>
            </div>
          </div>
          <div className="dashboard-nr-barra">
            <div className="dashboard-nr-barra-fill" style={{ width: `${novosPct}%` }} />
          </div>
          <div className="dashboard-nr-apoio">{fInt(dados.novos)} de {fInt(total)} pacientes atendidos no período eram novos</div>
        </>
      )}
    </section>
  )
}
