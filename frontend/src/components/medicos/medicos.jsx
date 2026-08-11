import { useEffect, useMemo, useRef, useState } from 'react'
import MedicosFiltros from './MedicosFiltros'
import MedicosKpis from './MedicosKpis'
import MedicosTabela from './MedicosTabela'
import { MEDICOS, VOLUME_MINIMO_PADRAO, TICKET_MEDIO, brl, pct, horasFmt, escalarMedico } from './medicosData'
import './medicos.css'

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
  const [carregando, setCarregando] = useState(true)
  const toastTimer = useRef(null)

  // Simula a latência do futuro GET /medicos ao trocar filtros, para que o
  // skeleton da tela tenha uso real e não só apareça no primeiro load.
  useEffect(() => {
    setCarregando(true)
    const t = setTimeout(() => setCarregando(false), 420)
    return () => clearTimeout(t)
  }, [periodo, status, especialidades])

  useEffect(() => () => clearTimeout(toastTimer.current), [])

  const base = useMemo(() => (
    MEDICOS
      .filter((m) => (status === 'Todos' ? true : m.status !== 'desligado'))
      .filter((m) => especialidades.length === 0 || especialidades.includes(m.especialidade))
      .map((m) => escalarMedico(m, periodo))
  ), [status, especialidades, periodo])

  const visiveis = useMemo(
    () => (volumeMinimoAtivo ? base.filter((m) => m.atendimentos >= VOLUME_MINIMO_PADRAO) : base),
    [base, volumeMinimoAtivo],
  )
  const ocultos = base.length - visiveis.length

  const linhasOrdenadas = useMemo(() => {
    const chaves = { nome: 'nome', atend: 'atendimentos', liq: 'receitaLiquida', noshow: 'noShow', atraso: 'atrasoMedio', retorno: 'retorno' }
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
    const statusRotulo = { ativo: 'ativo', ferias: 'férias', afastado: 'afastado', desligado: 'desligado' }[m.status]
    return {
      id: m.id,
      nome: m.nome,
      iniciais: m.iniciais,
      especialidade: m.especialidade,
      status: m.status,
      statusRotulo,
      inativo: m.status !== 'ativo',
      crm: m.crm,
      atendTxt: m.atendimentos ? String(m.atendimentos) : '—',
      divisaoTxt: m.atendimentos ? `${m.novos} nov · ${m.retornos} ret` : 'sem atendimentos',
      liqTxt: m.receitaLiquida ? brl(m.receitaLiquida) : '—',
      brutoNotaTxt: m.receitaLiquida ? `bruto ${brl(m.receitaBruta)} · ${m.repasse}%` : '—',
      ocupTxt: ocupPct ? `${ocupPct}%` : '—',
      ocupBarraPct: `${ocupPct}%`,
      noShowTxt: m.atendimentos ? pct(m.noShow) : '—',
      noShowAlerta: m.atendimentos > 0 && m.noShow > 20,
      atrasoTxt: m.atendimentos ? `${Math.round(m.atrasoMedio)} min` : '—',
      atrasoAlerta: m.atendimentos > 0 && m.atrasoMedio > 15,
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
    const horasTotal = Math.round(soma((m) => m.horasPerdidas) * 10) / 10
    const pontualidadeGeral = media((m) => m.pontualidade)
    const atrasoGeral = media((m) => m.atrasoMedio)
    const noShowGeral = media((m) => m.noShow)
    const retornoGeral = media((m) => m.retorno)
    const pontualidadeCor = pontualidadeGeral < 70 ? 'danger' : pontualidadeGeral < 85 ? 'warning' : 'success'

    const totalBruto = soma((m) => m.receitaBruta)
    const totalLiq = soma((m) => m.receitaLiquida)

    return {
      kpis: {
        ocupacaoPct: pct(ocupacaoGeral),
        ocupacaoBarraPct: `${Math.round(ocupacaoGeral)}%`,
        ocupacaoApoio: `${usadosTotal.toLocaleString('pt-BR')} de ${capTotal.toLocaleString('pt-BR')} horários preenchidos`,
        pontualidadePct: pct(pontualidadeGeral),
        pontualidadeCor,
        atrasoMedioMin: Math.round(atrasoGeral),
        horasPerdidasTxt: horasFmt(horasTotal),
        horasPerdidasCusto: brl(horasTotal * TICKET_MEDIO),
        horasPerdidasNota: `equivale a ${Math.round(horasTotal * 3)} consultas não realizadas`,
        retornoPct: pct(retornoGeral),
        retornoAnteriorPct: pct(Math.max(0, retornoGeral - 2)),
      },
      rodape: {
        titulo: 'Total · média',
        totalAtend: soma((m) => m.atendimentos).toLocaleString('pt-BR'),
        totalDivisao: `${soma((m) => m.novos)} nov · ${soma((m) => m.retornos)} ret`,
        totalLiq: brl(totalLiq),
        totalBrutoNota: `bruto ${brl(totalBruto)} · ${totalBruto ? Math.round((1 - totalLiq / totalBruto) * 100) : 0}%`,
        mediaOcup: pct(ocupacaoGeral),
        mediaNoShow: pct(noShowGeral),
        mediaAtraso: `${Math.round(atrasoGeral)} min`,
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
      />

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
