import { useEffect, useMemo, useRef, useState } from 'react'
import PacientesFiltros from './PacientesFiltros'
import PacientesKpis from './PacientesKpis'
import PacientesTabela from './PacientesTabela'
import PacientesKanban from './PacientesKanban'
import NovoPacienteModal from './NovoPacienteModal'
import { fetchConvenios, fetchPacientesFila, fetchPacientesKpis, fetchPacientesListagem } from './api'
import {
  COLUNAS_KANBAN_DEF, EVENTO_POR_COLUNA, iniciaisDe, statusLabel,
} from './pacientesData'
import './pacientes.css'

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
  const [toast, setToast] = useState(null)
  const toastTimer = useRef(null)
  const [modalAberto, setModalAberto] = useState(false)

  const [kpisApi, setKpisApi] = useState(null)
  const [carregandoKpis, setCarregandoKpis] = useState(true)
  const [erroKpis, setErroKpis] = useState(null)

  const [pacientesApi, setPacientesApi] = useState([])
  const [carregandoTabela, setCarregandoTabela] = useState(true)
  const [erroTabela, setErroTabela] = useState(null)

  const [filaApi, setFilaApi] = useState([])
  const [erroFila, setErroFila] = useState(null)

  const [opcoesConvenio, setOpcoesConvenio] = useState([])

  useEffect(() => {
    let cancelado = false
    setCarregandoKpis(true)
    setErroKpis(null)
    fetchPacientesKpis()
      .then((dados) => { if (!cancelado) setKpisApi(dados) })
      .catch((err) => { if (!cancelado) setErroKpis(err.message) })
      .finally(() => { if (!cancelado) setCarregandoKpis(false) })
    return () => { cancelado = true }
  }, [])

  useEffect(() => {
    let cancelado = false
    setCarregandoTabela(true)
    setErroTabela(null)
    fetchPacientesListagem()
      .then((dados) => { if (!cancelado) setPacientesApi(dados) })
      .catch((err) => { if (!cancelado) setErroTabela(err.message) })
      .finally(() => { if (!cancelado) setCarregandoTabela(false) })
    return () => { cancelado = true }
  }, [])

  useEffect(() => {
    let cancelado = false
    setErroFila(null)
    fetchPacientesFila()
      .then((dados) => { if (!cancelado) setFilaApi(dados) })
      .catch((err) => { if (!cancelado) setErroFila(err.message) })
    return () => { cancelado = true }
  }, [])

  useEffect(() => {
    fetchConvenios()
      .then((lista) => setOpcoesConvenio(lista.map((c) => c.nome)))
      .catch(() => { /* filtro de convênio fica vazio se a lista falhar */ })
  }, [])

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
    return pacientesApi.filter((p) => {
      if (status === 'Ativos' && p.status === 'off') return false
      if (filtroKpi === 'risk' && p.status !== 'risk') return false
      if (filtroKpi === 'inc' && p.status !== 'inc') return false
      if (convenios.length > 0 && !convenios.includes(p.convenio)) return false
      if (termo && !(p.nome.toLowerCase().includes(termo) || p.telefone.includes(termo))) return false
      return true
    })
  }, [pacientesApi, busca, status, filtroKpi, convenios])

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

  const linhas = useMemo(() => linhasOrdenadas.map((p) => ({
    ...p, statusTxt: statusLabel(p.status), iniciais: iniciaisDe(p.nome),
  })), [linhasOrdenadas])

  const kpis = useMemo(() => {
    const base = kpisApi ?? {
      totalCadastros: 0, baseAtiva: 0, novos: 0, novosDeltaPct: 0, novosCanalWhatsappPct: 0,
      risco: 0, incompletos: 0, incompletosSemDocumento: 0,
    }
    const riscoPct = base.baseAtiva ? (base.risco / base.baseAtiva) * 100 : 0
    const baseAtivaPct = base.totalCadastros ? Math.round((base.baseAtiva / base.totalCadastros) * 100) : 0
    return {
      baseAtivaTxt: base.baseAtiva.toLocaleString('pt-BR'),
      baseApoio: `de ${base.totalCadastros.toLocaleString('pt-BR')} cadastros · ${baseAtivaPct}%`,
      novosTxt: String(base.novos),
      novosApoio: `${base.novosDeltaPct >= 0 ? '+' : ''}${base.novosDeltaPct}% vs. mês anterior · ${base.novosCanalWhatsappPct}% via WhatsApp`,
      riscoTxt: String(base.risco),
      riscoCor: riscoPct > 10 ? 'danger' : 'warning',
      riscoApoio: `${Math.round(riscoPct)}% da base ativa · sem retorno há 6-12 meses`,
      incTxt: String(base.incompletos),
      incApoio: `${base.incompletosSemDocumento} sem documento`,
    }
  }, [kpisApi])

  const colunasKanban = useMemo(() => (
    COLUNAS_KANBAN_DEF.map((def) => ({
      ...def,
      cards: filaApi.filter((c) => c.coluna === def.id),
    }))
  ), [filaApi])

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
    // Ainda só visual: mover no board não faz PUT no backend pra mudar o
    // status_consulta/checkin — a fila volta ao estado real no próximo fetch.
    const cardAtual = colunasKanban.find((c) => c.id === origemId)?.cards[index]
    if (!cardAtual) return
    setFilaApi((prev) => prev.map((c) => (c === cardAtual ? { ...c, coluna: destinoId } : c)))
    mostrarToast(`${EVENTO_POR_COLUNA[destinoId]} — ${cardAtual.nome}`)
  }

  const erro = erroKpis || erroTabela || erroFila
  const filtroTexto = filtroKpi === 'risk' ? 'em risco de abandono' : filtroKpi === 'inc' ? 'cadastros incompletos' : ''
  const footTexto = `Mostrando ${linhas.length} de ${pacientesApi.length} pacientes carregados`

  return (
    <div className="pacientes-page">
      <PacientesFiltros
        busca={busca} onBuscaChange={setBusca}
        periodo={periodo} onPeriodoChange={setPeriodo}
        convenios={convenios} onConveniosChange={setConvenios} opcoesConvenio={opcoesConvenio}
        status={status} onStatusChange={setStatus}
        onNovoPaciente={() => setModalAberto(true)}
      />

      {erro && (
        <div className="pacientes-modal-erro" style={{ marginBottom: 16 }}>
          Não foi possível carregar a tela ({erro}). Confirme se a API está rodando em{' '}
          {import.meta.env.VITE_API_URL || 'http://localhost:8080'}.
        </div>
      )}

      <PacientesKpis carregando={carregandoKpis} filtro={filtroKpi} onFiltro={aplicarFiltroKpi} dados={kpis} />

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
            carregando={carregandoTabela} linhas={linhas} ordem={ordem} direcao={direcao} onOrdenar={ordenarPor}
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

      {modalAberto && (
        <NovoPacienteModal
          onClose={() => setModalAberto(false)}
          onCriado={(paciente) => {
            setModalAberto(false)
            mostrarToast(`${paciente.nome} cadastrado com sucesso.`)
          }}
        />
      )}
    </div>
  )
}
