import { useEffect, useState } from 'react'
import DashboardFiltros from './DashboardFiltros'
import DashboardKpis from './DashboardKpis'
import DashboardProximas from './DashboardProximas'
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

const HOJE_VAZIO = { consultas: 0, filaAguardando: 0, proximas: [] }

// Mapeia a resposta de POST /api/dashboard pro formato que os cartões esperam.
function mapearResposta(resp) {
  if (!resp) return { empty: true, hoje: HOJE_VAZIO }

  const hoje = resp.hoje ?? HOJE_VAZIO
  if (resp.empty) return { empty: true, hoje }

  const noShowCor = resp.noShow.percentual > 20 ? 'danger' : resp.noShow.percentual >= 10 ? 'warning' : 'neutral'
  const maxFaturamento = Math.max(1, ...resp.ranking.map((r) => r.faturamento || 0))

  const maxTipo = Math.max(1, ...resp.pagador.porTipo.map((t) => t.faturamento || 0))
  const partPct = 100 - resp.pagador.convenioPercentual

  return {
    empty: false,
    hoje,
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

// Não usa toISOString() aqui: ela converte pra UTC antes de fatiar a data, o
// que erra o "hoje" à noite em fusos atrás de UTC (ex.: Brasil) — a data
// muda um dia antes da meia-noite local.
function hojeISO() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

export function Dashboard() {
  const [periodo, setPeriodo] = useState('Mês')
  const [profissionalId, setProfissionalId] = useState('todos')
  const [profissionais, setProfissionais] = useState([])
  const [dataInicio, setDataInicio] = useState(hojeISO())
  const [dataFim, setDataFim] = useState(hojeISO())
  const [dados, setDados] = useState({ empty: true, hoje: HOJE_VAZIO })
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
    // "Personalizado" só dispara quando as duas datas já foram escolhidas.
    if (periodo === 'Personalizado' && (!dataInicio || !dataFim)) return

    let cancelado = false
    setCarregando(true)
    setErro(null)
    fetchDashboard({ periodo, profissionalId, dataInicio, dataFim })
      .then((resp) => { if (!cancelado) setDados(mapearResposta(resp)) })
      .catch((err) => { if (!cancelado) setErro(err.message) })
      .finally(() => { if (!cancelado) setCarregando(false) })
    return () => { cancelado = true }
  }, [periodo, profissionalId, dataInicio, dataFim])

  const vazio = dados.empty

  return (
    <div className="dashboard-page">
      <DashboardFiltros
        periodo={periodo} onPeriodoChange={setPeriodo}
        profissionalId={profissionalId} onProfissionalChange={setProfissionalId}
        profissionais={profissionais}
        dataInicio={dataInicio} onDataInicioChange={setDataInicio}
        dataFim={dataFim} onDataFimChange={setDataFim}
      />

      {erro && (
        <div className="dashboard-erro">
          Não foi possível carregar o dashboard ({erro}). Confirme se a API está rodando em{' '}
          {import.meta.env.VITE_API_URL || 'http://localhost:8080'}.
        </div>
      )}

      <DashboardKpis
        carregando={carregando} vazio={!carregando && vazio} dados={dados.kpi}
        carregandoHoje={carregando} hoje={dados.hoje}
      />

      <div className="dashboard-row-full">
        <DashboardProximas carregando={carregando} proximas={dados.hoje.proximas} />
      </div>

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
