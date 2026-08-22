import { useEffect, useMemo, useState } from 'react'
import DashboardFiltros from './DashboardFiltros'
import DashboardKpis from './DashboardKpis'
import DashboardFaturamento from './DashboardFaturamento'
import DashboardPagador from './DashboardPagador'
import DashboardRanking from './DashboardRanking'
import DashboardNovosRetornos from './DashboardNovosRetornos'
import { calcularDashboard } from './dashboardData'
import './dashboard.css'

export function Dashboard() {
  const [periodo, setPeriodo] = useState('Mês')
  const [profissionalId, setProfissionalId] = useState('todos')
  const [carregando, setCarregando] = useState(true)

  // Simula a latência do futuro GET /dashboard ao trocar filtros, mesmo
  // padrão usado em /medicos e /convenios — dá uso real ao skeleton.
  useEffect(() => {
    setCarregando(true)
    const t = setTimeout(() => setCarregando(false), 420)
    return () => clearTimeout(t)
  }, [periodo, profissionalId])

  const dados = useMemo(() => calcularDashboard({ periodo, profissionalId }), [periodo, profissionalId])
  const vazio = dados.empty

  return (
    <div className="dashboard-page">
      <DashboardFiltros
        periodo={periodo} onPeriodoChange={setPeriodo}
        profissionalId={profissionalId} onProfissionalChange={setProfissionalId}
      />

      <DashboardKpis carregando={carregando} vazio={!carregando && vazio} dados={dados.kpi} />

      <div className="dashboard-row-mid">
        <DashboardFaturamento
          carregando={carregando} empty={vazio} unidade={dados.unidade}
          serie={dados.serie} metaSemanal={dados.metaSemanal}
        />
        <DashboardPagador carregando={carregando} empty={vazio} dados={dados.pagador} />
      </div>

      <div className="dashboard-row-bottom">
        <DashboardRanking carregando={carregando} empty={vazio} linhas={dados.ranking} />
        <DashboardNovosRetornos carregando={carregando} empty={vazio} dados={dados.novosRetornos} />
      </div>
    </div>
  )
}
