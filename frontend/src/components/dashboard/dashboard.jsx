import { useEffect, useState } from 'react'
import DashboardFiltros from './DashboardFiltros'
import DashboardKpis from './DashboardKpis'
import DashboardFaturamento from './DashboardFaturamento'
import DashboardPagador from './DashboardPagador'
import DashboardRanking from './DashboardRanking'
import DashboardNovosRetornos from './DashboardNovosRetornos'
import { fetchDashboard, fetchMedicos } from './api'
import { brl, fInt, pct } from './dashboardData'
import './dashboard.css'

function iniciaisDe(nome) {
  return nome.split(' ').map((p) => p[0]).slice(0, 2).join('').toUpperCase()
}

// Mapeia a resposta de POST /api/dashboard pro formato que os cartões esperam.
function mapearResposta(resp) {
  if (!resp || resp.empty) return { empty: true }

  const noShowCor = resp.noShow.percentual > 20 ? 'danger' : resp.noShow.percentual >= 10 ? 'warning' : 'neutral'
  const maxFaturamento = Math.max(1, ...resp.ranking.map((r) => r.faturamento || 0))

  const maxTipo = Math.max(1, ...resp.pagador.porTipo.map((t) => t.faturamento || 0))
  const partPct = 100 - resp.pagador.convenioPercentual

  return {
    empty: false,
    kpi: {
      faturamento: {
        valorTxt: brl(resp.faturamento),
        apoio: `${resp.totalConsultas} ${resp.totalConsultas === 1 ? 'consulta' : 'consultas'} faturáveis no período`,
      },
      ocupacao: {
        valorTxt: pct(resp.ocupacao.percentual, 0),
        barraPct: `${Math.min(100, resp.ocupacao.percentual).toFixed(1)}%`,
        apoio: `${fInt(resp.ocupacao.preenchidos)} de ${fInt(resp.ocupacao.totalSlots)} horários preenchidos`,
      },
      noShow: {
        valorTxt: pct(resp.noShow.percentual),
        apoio: `${fInt(resp.noShow.faltas)} faltas no período`,
        cor: noShowCor,
      },
    },
    ranking: resp.ranking.map((r) => ({
      id: r.id,
      nome: r.nome,
      iniciais: iniciaisDe(r.nome),
      atendimentosTxt: fInt(r.totalConsultas),
      receitaTxt: brl(r.faturamento),
      noShowTxt: pct(r.noShowPct),
      noShowCor: r.noShowPct > 20 ? 'danger' : r.noShowPct >= 10 ? 'warning' : 'neutral',
      barraPct: `${((r.faturamento || 0) / maxFaturamento * 100).toFixed(0)}%`,
    })),
    novosRetornos: resp.novosRetornos,
    pagador: {
      convPct: resp.pagador.convenioPercentual,
      convPctTxt: pct(resp.pagador.convenioPercentual, 0),
      partPct,
      partPctTxt: pct(partPct, 0),
      convValTxt: brl(resp.pagador.convenioValor),
      partValTxt: brl(resp.pagador.particularValor),
      procs: resp.pagador.porTipo.map((t) => ({
        nome: t.tipo,
        valorTxt: brl(t.faturamento),
        barraPct: `${((t.faturamento || 0) / maxTipo * 100).toFixed(0)}%`,
      })),
    },
    faturamentoSerie: resp.serieTemporal,
    faturamentoUnidade: resp.serieUnidade,
  }
}

export function Dashboard() {
  const [periodo, setPeriodo] = useState('Mês')
  const [profissionalId, setProfissionalId] = useState('todos')
  const [profissionais, setProfissionais] = useState([])
  const [dados, setDados] = useState({ empty: true })
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState(null)

  useEffect(() => {
    let cancelado = false
    fetchMedicos()
      .then((lista) => { if (!cancelado) setProfissionais(lista.map((m) => ({ id: m.id, nome: m.nome }))) })
      .catch(() => { /* filtro de profissional fica só com "Todos" se a lista falhar */ })
    return () => { cancelado = true }
  }, [])

  useEffect(() => {
    let cancelado = false
    setCarregando(true)
    setErro(null)
    fetchDashboard({ periodo, profissionalId })
      .then((resp) => { if (!cancelado) setDados(mapearResposta(resp)) })
      .catch((err) => { if (!cancelado) setErro(err.message) })
      .finally(() => { if (!cancelado) setCarregando(false) })
    return () => { cancelado = true }
  }, [periodo, profissionalId])

  const vazio = dados.empty

  return (
    <div className="dashboard-page">
      <DashboardFiltros
        periodo={periodo} onPeriodoChange={setPeriodo}
        profissionalId={profissionalId} onProfissionalChange={setProfissionalId}
        profissionais={profissionais}
      />

      {erro && (
        <div className="dashboard-erro">
          Não foi possível carregar o dashboard ({erro}). Confirme se a API está rodando em{' '}
          {import.meta.env.VITE_API_URL || 'http://localhost:8080'}.
        </div>
      )}

      <DashboardKpis carregando={carregando} vazio={!carregando && vazio} dados={dados.kpi} />

      <div className="dashboard-row-mid">
        <DashboardFaturamento
          carregando={carregando} empty={vazio}
          unidade={dados.faturamentoUnidade} serie={dados.faturamentoSerie}
        />
        <DashboardPagador carregando={carregando} empty={vazio} dados={dados.pagador} />
      </div>

      <div className="dashboard-row-bottom">
        <DashboardRanking carregando={carregando} empty={vazio} linhas={dados.ranking || []} />
        <DashboardNovosRetornos carregando={carregando} empty={vazio} dados={dados.novosRetornos} />
      </div>
    </div>
  )
}
