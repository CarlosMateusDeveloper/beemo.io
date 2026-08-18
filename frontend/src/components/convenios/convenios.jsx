import { useEffect, useMemo, useRef, useState } from 'react'
import ConveniosFiltros from './ConveniosFiltros'
import ConveniosKpis from './ConveniosKpis'
import ConveniosLotes from './ConveniosLotes'
import ConveniosResumo from './ConveniosResumo'
import ConveniosTabela from './ConveniosTabela'
import { GLOSAS, LOTES } from './conveniosData'
import './convenios.css'

export function Convenios() {
  const [aba, setAba] = useState('glosas')
  const [periodo, setPeriodo] = useState('Últimos 30 dias')
  const [convenios, setConvenios] = useState([])
  const [status, setStatus] = useState('')
  const [responsavel, setResponsavel] = useState('')
  const [filtroPrazo, setFiltroPrazo] = useState(false)
  const [selecionados, setSelecionados] = useState(new Set())
  const [toast, setToast] = useState(null)
  const toastTimer = useRef(null)

  useEffect(() => () => clearTimeout(toastTimer.current), [])

  function mostrarToast(texto) {
    setToast(texto)
    clearTimeout(toastTimer.current)
    toastTimer.current = setTimeout(() => setToast(null), 2600)
  }

  const linhas = useMemo(() => {
    const filtradas = GLOSAS.filter((g) => {
      if (convenios.length > 0 && !convenios.includes(g.convenio)) return false
      if (status && g.status !== status) return false
      if (responsavel && g.responsavel !== responsavel) return false
      if (filtroPrazo && (g.prazoDias == null || g.prazoDias > 5)) return false
      return true
    })
    return [...filtradas].sort((a, b) => {
      const da = a.prazoDias ?? Infinity
      const db = b.prazoDias ?? Infinity
      return da - db
    })
  }, [convenios, status, responsavel, filtroPrazo])

  function toggleFiltroPrazo() {
    setFiltroPrazo((v) => !v)
    setSelecionados(new Set())
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
    mostrarToast(tipo === 'recurso'
      ? `Montando recurso em lote para ${n} ${n === 1 ? 'glosa' : 'glosas'}…`
      : `Atribuindo responsável para ${n} ${n === 1 ? 'glosa' : 'glosas'}…`)
  }

  function abrirGlosa(glosa) {
    // Sem /convenios/glosas/{id} ainda: mostra um toast, como nas outras telas.
    mostrarToast(`Abrindo a glosa de ${glosa.paciente}…`)
  }

  function abrirConvenio(convenio) {
    // Sem /convenios/{id} ainda: mostra um toast, como nas outras telas.
    mostrarToast(`Abrindo o convênio ${convenio.convenio}…`)
  }

  function abrirLote(lote) {
    mostrarToast(`Abrindo o lote ${lote.id}…`)
  }

  return (
    <div className="convenios-page">
      <ConveniosFiltros
        periodo={periodo} onPeriodoChange={setPeriodo}
        convenios={convenios} onConveniosChange={setConvenios}
        status={status} onStatusChange={setStatus}
        responsavel={responsavel} onResponsavelChange={setResponsavel}
      />

      <ConveniosKpis filtroPrazo={filtroPrazo} onToggleFiltroPrazo={toggleFiltroPrazo} />

      <div className="convenios-tabs">
        <button type="button" className={`convenios-tab ${aba === 'glosas' ? 'active' : ''}`} onClick={() => setAba('glosas')}>
          Glosas<span className="convenios-tab-count">{GLOSAS.length}</span>
        </button>
        <button type="button" className={`convenios-tab ${aba === 'convenios' ? 'active' : ''}`} onClick={() => setAba('convenios')}>
          Convênios<span className="convenios-tab-count">4</span>
        </button>
        <button type="button" className={`convenios-tab ${aba === 'lotes' ? 'active' : ''}`} onClick={() => setAba('lotes')}>
          Lotes<span className="convenios-tab-count">{LOTES.length}</span>
        </button>
      </div>

      {aba === 'glosas' && (
        <ConveniosTabela
          linhas={linhas}
          total={GLOSAS.length}
          selecionados={selecionados}
          onToggle={toggleSelecionado}
          onToggleTodos={toggleTodos}
          onLimparSelecao={() => setSelecionados(new Set())}
          onAcaoLote={acaoLote}
          onAbrirGlosa={abrirGlosa}
        />
      )}

      {aba === 'convenios' && <ConveniosResumo onAbrirConvenio={abrirConvenio} />}

      {aba === 'lotes' && <ConveniosLotes onAbrirLote={abrirLote} />}

      {toast && (
        <div className="convenios-toast">
          <span className="convenios-toast-dot" />
          {toast}
        </div>
      )}
    </div>
  )
}
