import { useEffect, useMemo, useRef, useState } from 'react'
import MedicosFiltros from './MedicosFiltros'
import MedicosKpis from './MedicosKpis'
import MedicosTabela from './MedicosTabela'
import { fetchEspecialidades, fetchMedicosPainel } from './api'
import { VOLUME_MINIMO_PADRAO, brl, horasFmt, iniciaisDe, pct } from './medicosData'
import './medicos.css'

const STATUS_ROTULO = { ativo: 'ativo', ferias: 'férias', afastado: 'afastado', desligado: 'desligado' }

function mapearMedico(m) {
  return {
    id: m.id,
    nome: m.nome,
    crm: m.crm,
    especialidade: m.especialidade,
    especialidadeId: m.especialidadeId,
    status: m.status,
    atendimentos: m.atendimentos,
    novos: m.novos,
    retornos: m.retornos,
    receitaBruta: m.receitaBruta,
    repassePercentual: m.repassePercentual,
    receitaLiquida: m.receitaLiquida,
    horariosUsados: m.horariosUsados,
    horariosAbertos: m.horariosAbertos,
    noShow: m.noShowPct,
    pacientesTotal: m.pacientesTotal,
    pacientesRetorno: m.pacientesRetorno,
    retorno: m.pacientesTotal ? (m.pacientesRetorno / m.pacientesTotal) * 100 : 0,
    consultasComInicio: m.consultasComInicio,
    consultasPontuais: m.consultasPontuais,
    atrasoMedioMin: m.atrasoMedioMin,
    horasPerdidas: m.horasPerdidas,
    proximoHorario: m.proximoHorarioTxt,
    proximoHorarioNota: m.proximoTipo,
  }
}

export function Medicos() {
  const [periodo, setPeriodo] = useState('Mês')
  const [status, setStatus] = useState('Ativos')
  const [especialidades, setEspecialidades] = useState([])
  const [dica, setDica] = useState(null)
  const [ordem, setOrdem] = useState('nome')
  const [direcao, setDirecao] = useState('asc')
  const [manual, setManual] = useState(false)
  const [volumeMinimoAtivo, setVolumeMinimoAtivo] = useState(true)
  const [abrindo, setAbrindo] = useState(null)
  const toastTimer = useRef(null)

  const [medicosApi, setMedicosApi] = useState([])
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState(null)
  const [listaEspecialidades, setListaEspecialidades] = useState([])

  useEffect(() => {
    fetchEspecialidades().then(setListaEspecialidades).catch(() => { /* filtro fica vazio se falhar */ })
  }, [])

  useEffect(() => {
    let cancelado = false
    setCarregando(true)
    setErro(null)
    fetchMedicosPainel(periodo)
      .then((resp) => { if (!cancelado) setMedicosApi(resp.empty ? [] : resp.medicos.map(mapearMedico)) })
      .catch((err) => { if (!cancelado) setErro(err.message) })
      .finally(() => { if (!cancelado) setCarregando(false) })
    return () => { cancelado = true }
  }, [periodo])

  useEffect(() => () => clearTimeout(toastTimer.current), [])

  const base = useMemo(() => (
    medicosApi
      .filter((m) => (status === 'Todos' ? true : m.status === 'ativo'))
      .filter((m) => especialidades.length === 0 || especialidades.includes(m.especialidade))
  ), [medicosApi, status, especialidades])

  const visiveis = useMemo(
    () => (volumeMinimoAtivo ? base.filter((m) => m.atendimentos >= VOLUME_MINIMO_PADRAO) : base),
    [base, volumeMinimoAtivo],
  )
  const ocultos = base.length - visiveis.length

  const linhasOrdenadas = useMemo(() => {
    const chaves = { nome: 'nome', atend: 'atendimentos', liq: 'receitaBruta', noshow: 'noShow', atraso: 'atrasoMedioMin', retorno: 'retorno' }
    return [...visiveis].sort((a, b) => {
      let va, vb
      if (ordem === 'ocup') {
        va = a.horariosAbertos ? a.horariosUsados / a.horariosAbertos : 0
        vb = b.horariosAbertos ? b.horariosUsados / b.horariosAbertos : 0
      } else {
        const k = chaves[ordem] || 'nome'
        va = a[k]; vb = b[k]
      }
      const c = typeof va === 'string' ? va.localeCompare(vb, 'pt-BR') : va - vb
      return direcao === 'asc' ? c : -c
    })
  }, [visiveis, ordem, direcao])

  const linhas = useMemo(() => linhasOrdenadas.map((m) => {
    const ocupPct = m.horariosAbertos ? Math.round((m.horariosUsados / m.horariosAbertos) * 100) : 0
    const temRepasse = m.repassePercentual != null
    return {
      id: m.id,
      nome: m.nome,
      iniciais: iniciaisDe(m.nome),
      especialidade: m.especialidade,
      status: m.status,
      statusRotulo: STATUS_ROTULO[m.status],
      inativo: m.status !== 'ativo',
      crm: m.crm,
      atendTxt: m.atendimentos ? String(m.atendimentos) : '—',
      divisaoTxt: m.atendimentos ? `${m.novos} nov · ${m.retornos} ret` : 'sem atendimentos',
      liqTxt: m.receitaBruta ? brl(m.receitaBruta) : '—',
      brutoNotaTxt: temRepasse ? `líquida ${brl(m.receitaLiquida)} · repasse ${m.repassePercentual}%` : null,
      ocupTxt: ocupPct ? `${ocupPct}%` : '—',
      ocupBarraPct: `${ocupPct}%`,
      noShowTxt: m.atendimentos ? pct(m.noShow) : '—',
      noShowAlerta: m.atendimentos > 0 && m.noShow > 20,
      atrasoTxt: m.consultasComInicio ? `${Math.round(m.atrasoMedioMin)} min` : '—',
      atrasoAlerta: m.consultasComInicio > 0 && m.atrasoMedioMin > 15,
      retornoTxt: m.atendimentos ? pct(m.retorno) : '—',
      proximoTxt: m.proximoHorario ?? '—',
      proximoVazio: !m.proximoHorario,
      proximoNotaTxt: m.proximoHorarioNota,
    }
  }), [linhasOrdenadas])

  const agregados = useMemo(() => {
    const soma = (f) => visiveis.reduce((acc, m) => acc + f(m), 0)
    const comAtendimentos = visiveis.filter((m) => m.atendimentos > 0)
    const media = (f) => (comAtendimentos.length
      ? soma((m) => (m.atendimentos > 0 ? f(m) : 0)) / comAtendimentos.length
      : 0)

    const capTotal = soma((m) => m.horariosAbertos)
    const usadosTotal = soma((m) => m.horariosUsados)
    const ocupacaoGeral = capTotal ? (usadosTotal / capTotal) * 100 : 0
    const noShowGeral = media((m) => m.noShow)

    const pacientesTotal = soma((m) => m.pacientesTotal)
    const pacientesRetorno = soma((m) => m.pacientesRetorno)
    const retornoGeral = pacientesTotal ? (pacientesRetorno / pacientesTotal) * 100 : 0

    const comInicioTotal = soma((m) => m.consultasComInicio)
    const pontuaisTotal = soma((m) => m.consultasPontuais)
    const pontualidadeGeral = comInicioTotal ? (pontuaisTotal / comInicioTotal) * 100 : 0
    const atrasoMinTotal = soma((m) => m.atrasoMedioMin * m.consultasComInicio)
    const atrasoMedioGeral = comInicioTotal ? atrasoMinTotal / comInicioTotal : 0
    const pontualidadeCor = pontualidadeGeral < 70 ? 'danger' : pontualidadeGeral < 85 ? 'warning' : 'success'

    const horasPerdidasTotal = soma((m) => m.horasPerdidas)

    const totalBruto = soma((m) => m.receitaBruta)

    return {
      kpis: {
        ocupacaoPct: pct(ocupacaoGeral),
        ocupacaoBarraPct: `${Math.round(ocupacaoGeral)}%`,
        ocupacaoApoio: `${usadosTotal.toLocaleString('pt-BR')} de ${capTotal.toLocaleString('pt-BR')} horários preenchidos`,
        pontualidadeAmostra: comInicioTotal,
        pontualidadePct: pct(pontualidadeGeral),
        pontualidadeCor,
        atrasoMedioMin: Math.round(atrasoMedioGeral),
        horasPerdidasTxt: horasFmt(horasPerdidasTotal),
        retornoPct: pct(retornoGeral),
      },
      rodape: {
        titulo: 'Total · média',
        totalAtend: soma((m) => m.atendimentos).toLocaleString('pt-BR'),
        totalDivisao: `${soma((m) => m.novos)} nov · ${soma((m) => m.retornos)} ret`,
        totalLiq: brl(totalBruto),
        mediaOcup: pct(ocupacaoGeral),
        mediaNoShow: pct(noShowGeral),
        mediaAtraso: comInicioTotal ? `${Math.round(atrasoMedioGeral)} min` : '—',
        mediaRetorno: pct(retornoGeral),
      },
    }
  }, [visiveis])

  const listaVazia = visiveis.length === 0
  const vazioPorVolume = listaVazia && base.length > 0

  function ordenar(coluna) {
    const novaDirecao = ordem === coluna ? (direcao === 'asc' ? 'desc' : 'asc') : (coluna === 'nome' ? 'asc' : 'desc')
    setOrdem(coluna)
    setDirecao(novaDirecao)
    setManual(true)
  }

  function limparOrdem() {
    setOrdem('nome')
    setDirecao('asc')
    setManual(false)
  }

  function abrirMedico(nome) {
    // Sem rota de ficha individual ainda: mostra um toast, como no protótipo.
    setAbrindo(`Abrindo a ficha de ${nome}…`)
    clearTimeout(toastTimer.current)
    toastTimer.current = setTimeout(() => setAbrindo(null), 2200)
  }

  return (
    <div className="medicos-page">
      <MedicosFiltros
        periodo={periodo} onPeriodoChange={setPeriodo}
        status={status} onStatusChange={setStatus}
        especialidades={especialidades} onEspecialidadesChange={setEspecialidades}
        listaEspecialidades={listaEspecialidades} medicos={medicosApi}
      />

      {erro && (
        <div className="medicos-erro">
          Não foi possível carregar o painel ({erro}). Confirme se a API está rodando em{' '}
          {import.meta.env.VITE_API_URL || 'http://localhost:8080'}.
        </div>
      )}

      <MedicosKpis
        carregando={carregando} vazio={!carregando && listaVazia} dados={agregados.kpis}
        dica={dica} onAbrirDica={setDica} onFecharDica={() => setDica(null)}
      />

      <MedicosTabela
        carregando={carregando} listaVazia={listaVazia} vazioPorVolume={vazioPorVolume}
        linhas={linhas} ordem={ordem} direcao={direcao} onOrdenar={ordenar}
        manual={manual} onLimparOrdem={limparOrdem} rodape={agregados.rodape}
        ocultos={ocultos}
        textoOcultos={`${ocultos} ${ocultos === 1 ? 'médico oculto' : 'médicos ocultos'} por não atingir ${VOLUME_MINIMO_PADRAO} atendimentos no período — volumes baixos distorcem percentuais.`}
        onAbrirMedico={abrirMedico}
        onDesativarVolume={() => setVolumeMinimoAtivo(false)}
      />

      {abrindo && (
        <div className="medicos-toast">
          <span className="medicos-toast-dot" />
          {abrindo}
        </div>
      )}
    </div>
  )
}
