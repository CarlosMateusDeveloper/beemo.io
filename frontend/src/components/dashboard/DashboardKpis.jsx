import { Wallet, CalendarCheck2, UserX } from 'lucide-react'

function Skeleton({ valorW }) {
  return (
    <>
      <div className="dashboard-skel dashboard-skel-valor shine" style={{ width: valorW }} />
      <div className="dashboard-skel dashboard-skel-linha" style={{ width: '70%' }} />
    </>
  )
}

function Vazio() {
  return (
    <>
      <div className="dashboard-kpi-vazio">—</div>
      <div className="dashboard-kpi-apoio">Sem dados no período selecionado</div>
    </>
  )
}

export default function DashboardKpis({ carregando, vazio, dados }) {
  return (
    <div className="dashboard-kpis">
      <div className="dashboard-kpi-card">
        <div className="dashboard-kpi-lab">
          <span className="dashboard-kpi-icon acc"><Wallet size={16} strokeWidth={1.8} /></span>
          Faturado no período
        </div>
        {carregando ? <Skeleton valorW="130px" /> : vazio ? <Vazio /> : (
          <>
            <div className="dashboard-kpi-valor-row">
              <span className="dashboard-kpi-valor">{dados.faturamento.valorTxt}</span>
            </div>
            <div className="dashboard-kpi-apoio">{dados.faturamento.apoio}</div>
          </>
        )}
      </div>

      <div className="dashboard-kpi-card">
        <div className="dashboard-kpi-lab">
          <span className="dashboard-kpi-icon acc"><CalendarCheck2 size={16} strokeWidth={1.8} /></span>
          Ocupação da agenda
        </div>
        {carregando ? <Skeleton valorW="80px" /> : vazio ? <Vazio /> : (
          <>
            <div className="dashboard-kpi-valor-row">
              <span className="dashboard-kpi-valor">{dados.ocupacao.valorTxt}</span>
            </div>
            <div className="dashboard-kpi-barra">
              <div className="dashboard-kpi-barra-fill" style={{ width: dados.ocupacao.barraPct }} />
            </div>
            <div className="dashboard-kpi-apoio">{dados.ocupacao.apoio}</div>
          </>
        )}
      </div>

      <div className="dashboard-kpi-card">
        <div className="dashboard-kpi-lab">
          <span className="dashboard-kpi-icon neutro"><UserX size={16} strokeWidth={1.8} /></span>
          Taxa de no-show
        </div>
        {carregando ? <Skeleton valorW="70px" /> : vazio ? <Vazio /> : (
          <>
            <div className="dashboard-kpi-valor-row">
              <span className={`dashboard-kpi-valor ${dados.noShow.cor}`}>{dados.noShow.valorTxt}</span>
            </div>
            <div className="dashboard-kpi-apoio">{dados.noShow.apoio}</div>
          </>
        )}
      </div>
    </div>
  )
}
