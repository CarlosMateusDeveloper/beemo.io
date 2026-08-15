import { useEffect, useMemo, useRef, useState } from 'react'
import PacientesFiltros from './PacientesFiltros'
import PacientesKpis from './PacientesKpis'
import PacientesTabela from './PacientesTabela'
import PacientesKanban from './PacientesKanban'
import {
  COLUNAS_KANBAN, EVENTO_POR_COLUNA, KPI_BASE, PACIENTES, statusLabel,
} from './pacientesData'
import './pacientes.css'

function clonarColunas() {
  return COLUNAS_KANBAN.map((c) => ({ ...c, cards: c.cards.map((card) => ({ ...card })) }))
}

export function Pacientes() {
  const [busca, setBusca] = useState('')
  const [periodo, setPeriodo] = useState('Mês')
  const [convenios, setConvenios] = useState([])
  const [status, setStatus] = useState('Ativos')
  const [filtroKpi, setFiltroKpi] = useState(null)
  const [view, setView] = useState('tabela')
  const [ordem, setOrdem] = useState('ultima')
  const [direcao, setDirecao] = useState('desc')
  const [selecionados, setSelecionados] = useState(new Set())
  const [carregando, setCarregando] = useState(true)
  const [colunasKanban, setColunasKanban] = useState(clonarColunas)
  const [toast, setToast] = useState(null)
  const toastTimer = useRef(null)

  // Simula a latência do futuro GET /pacientes ao trocar filtros, para o
  // skeleton ter uso real e não só aparecer no primeiro carregamento.
  useEffect(() => {
    setCarregando(true)
    const t = setTimeout(() => setCarregando(false), 420)
    return () => clearTimeout(t)
  }, [busca, periodo, convenios, status, filtroKpi])

  useEffect(() => () => clearTimeout(toastTimer.current), [])

  function mostrarToast(texto) {
    setToast(texto)
    clearTimeout(toastTimer.current)
    toastTimer.current = setTimeout(() => setToast(null), 2600)
  }

  function mudarView(novaView) {
    setView(novaView)
    // Kanban reflete a fila de hoje — período maior que "7 dias" não faz sentido aqui.
    if (novaView === 'kanban' && periodo !== 'Hoje' && periodo !== '7 dias') setPeriodo('Hoje')
  }

  function aplicarFiltroKpi(tipo) {
    setFiltroKpi((prev) => (prev === tipo ? null : tipo))
    setSelecionados(new Set())
    setView('tabela')
  }

  const linhasFiltradas = useMemo(() => {
    const termo = busca.trim().toLowerCase()
    return PACIENTES.filter((p) => {
      if (status === 'Ativos' && p.status === 'off') return false
      if (filtroKpi === 'risk' && p.status !== 'risk') return false
      if (filtroKpi === 'inc' && p.status !== 'inc') return false
      if (convenios.length > 0 && !convenios.includes(p.convenio)) return false
      if (termo && !(p.nome.toLowerCase().includes(termo) || p.telefone.includes(termo))) return false
      return true
    })
  }, [busca, status, filtroKpi, convenios])

  const linhasOrdenadas = useMemo(() => {
    const chaves = { nome: 'nome', ultima: 'ultimaData', proxima: 'proximaData', status: 'status' }
    const chave = chaves[ordem] ?? 'ultimaData'
    return [...linhasFiltradas].sort((a, b) => {
      const va = a[chave] ?? ''
      const vb = b[chave] ?? ''
      const c = va.localeCompare(vb, 'pt-BR')
      return direcao === 'asc' ? c : -c
    })
  }, [linhasFiltradas, ordem, direcao])

  const linhas = useMemo(() => linhasOrdenadas.map((p) => ({ ...p, statusTxt: statusLabel(p.status) })), [linhasOrdenadas])

  const kpis = useMemo(() => {
    const riscoPct = (KPI_BASE.risco / KPI_BASE.baseAtiva) * 100
    return {
      baseAtivaTxt: KPI_BASE.baseAtiva.toLocaleString('pt-BR'),
      baseApoio: `de ${KPI_BASE.totalCadastros.toLocaleString('pt-BR')} cadastros · ${Math.round((KPI_BASE.baseAtiva / KPI_BASE.totalCadastros) * 100)}%`,
      novosTxt: String(KPI_BASE.novos),
      novosApoio: `+${KPI_BASE.novosDeltaPct}% · ${KPI_BASE.novosCanalPct}% via WhatsApp`,
      riscoTxt: String(KPI_BASE.risco),
      riscoCor: riscoPct > 10 ? 'danger' : 'warning',
      riscoApoio: `${Math.round(riscoPct)}% da base ativa · sem retorno há 8+ meses`,
      incTxt: String(KPI_BASE.incompletos),
      incApoio: `${KPI_BASE.incompletosSemDocumento} sem documento`,
    }
  }, [])

  function ordenarPor(coluna) {
    if (ordem === coluna) setDirecao((d) => (d === 'asc' ? 'desc' : 'asc'))
    else { setOrdem(coluna); setDirecao(coluna === 'nome' ? 'asc' : 'desc') }
  }

  function toggleSelecionado(id) {
    setSelecionados((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id); else next.add(id)
      return next
    })
  }

  function toggleTodos() {
    setSelecionados((prev) => (prev.size === linhas.length ? new Set() : new Set(linhas.map((l) => l.id))))
  }

  function acaoLote(tipo) {
    const n = selecionados.size
    mostrarToast(tipo === 'exportar'
      ? `Exportando ${n} ${n === 1 ? 'paciente' : 'pacientes'}…`
      : `Mensagem de recontato enviada para ${n} ${n === 1 ? 'paciente' : 'pacientes'}`)
    setSelecionados(new Set())
  }

  function abrirPaciente(nome) {
    // Sem ficha /pacientes/{id} ainda: mostra um toast, como no protótipo.
    mostrarToast(`Abrindo a ficha de ${nome}…`)
  }

  function moverCard(origemId, destinoId, index) {
    const cardAtual = colunasKanban.find((c) => c.id === origemId)?.cards[index]
    if (!cardAtual) return
    setColunasKanban((prev) => {
      const clone = prev.map((c) => ({ ...c, cards: [...c.cards] }))
      const origem = clone.find((c) => c.id === origemId)
      const destino = clone.find((c) => c.id === destinoId)
      const [movido] = origem.cards.splice(index, 1)
      if (destinoId === 'recepcao' && movido.esperaMin == null) movido.esperaMin = 0
      if (destinoId !== 'recepcao' && movido.esperaMin != null) delete movido.esperaMin
      destino.cards.unshift(movido)
      return clone
    })
    mostrarToast(`${EVENTO_POR_COLUNA[destinoId]} — ${cardAtual.nome}`)
  }

  const filtroTexto = filtroKpi === 'risk' ? 'em risco de abandono' : filtroKpi === 'inc' ? 'cadastros incompletos' : ''
  const footTexto = `Mostrando ${linhas.length} de ${PACIENTES.length} pacientes carregados`

  return (
    <div className="pacientes-page">
      <PacientesFiltros
        busca={busca} onBuscaChange={setBusca}
        periodo={periodo} onPeriodoChange={setPeriodo}
        convenios={convenios} onConveniosChange={setConvenios}
        status={status} onStatusChange={setStatus}
        onNovoPaciente={() => mostrarToast('Formulário de novo paciente ainda não implementado.')}
      />

      <PacientesKpis carregando={carregando} filtro={filtroKpi} onFiltro={aplicarFiltroKpi} dados={kpis} />

      <div className="pacientes-painel">
        <div className="pacientes-painel-head">
          <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
            <span className="pacientes-painel-titulo">
              {view === 'tabela' ? 'Todos os pacientes' : 'Fila do dia'} <span>· {view === 'tabela' ? linhas.length : ''}</span>
            </span>
            {filtroKpi && (
              <span className="pacientes-chip">
                {filtroTexto}
                <button type="button" aria-label="Remover filtro" onClick={() => setFiltroKpi(null)}>×</button>
              </span>
            )}
            {view === 'kanban' && <span className="pacientes-chip info">mostrando apenas hoje</span>}
          </div>
          <div className="pacientes-toggle" role="group" aria-label="Visão">
            <button type="button" aria-pressed={view === 'tabela'} onClick={() => mudarView('tabela')}>Tabela</button>
            <button type="button" aria-pressed={view === 'kanban'} onClick={() => mudarView('kanban')}>Kanban</button>
          </div>
        </div>

        {view === 'tabela' ? (
          <PacientesTabela
            carregando={carregando} linhas={linhas} ordem={ordem} direcao={direcao} onOrdenar={ordenarPor}
            onAbrirPaciente={abrirPaciente}
            selecionados={selecionados} onToggleSelecionado={toggleSelecionado} onToggleTodos={toggleTodos}
            onAcaoLote={acaoLote} footTexto={footTexto}
          />
        ) : (
          <>
            <PacientesKanban colunas={colunasKanban} onMoverCard={moverCard} onAbrirPaciente={abrirPaciente} />
            <div className="pacientes-painel-foot">
              <span>Arraste um cartão para registrar o evento — mover para <strong style={{ color: 'var(--text-secondary)', fontWeight: 500 }}>Recepção</strong> faz o check-in.</span>
            </div>
          </>
        )}
      </div>

      {toast && (
        <div className="pacientes-toast" role="status" aria-live="polite">
          <span className="pacientes-toast-dot" />{toast}
        </div>
      )}
    </div>
  )
}
