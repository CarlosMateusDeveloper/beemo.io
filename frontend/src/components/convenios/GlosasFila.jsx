import { useEffect, useRef, useState } from 'react'
import { CheckCircle2, TrendingUp, Wallet, XCircle } from 'lucide-react'
import { alterarResponsavelGlosa, fetchGlosaIndicadores, fetchGlosas } from './api'
import { brl, corPrazoClasse, statusGlosaMeta } from './conveniosData'

export default function GlosasFila({
  statusGlosa, idConvenio, idUsuarioResponsavel, usuarios, searchParams, onAbrirGlosa,
}) {
  const [linhas, setLinhas] = useState([])
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState(null)
  const [pagina, setPagina] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [indicadores, setIndicadores] = useState(null)
  const [selecionados, setSelecionados] = useState(new Set())
  const [atribuindo, setAtribuindo] = useState(false)

  const scrollRef = useRef(null)
  const chaveScroll = `convenios-glosas-scroll:${searchParams?.toString() ?? ''}`

  useEffect(() => { setPagina(0) }, [statusGlosa, idConvenio, idUsuarioResponsavel])

  useEffect(() => {
    let cancelado = false
    setCarregando(true)
    setSelecionados(new Set())
    fetchGlosas({ status: statusGlosa || undefined, idConvenio: idConvenio || undefined, idUsuarioResponsavel: idUsuarioResponsavel || undefined, page: pagina })
      .then((dados) => {
        if (cancelado) return
        setLinhas(dados.content)
        setTotalPages(dados.totalPages)
        setTotalElements(dados.totalElements)
        setErro(null)
      })
      .catch((err) => { if (!cancelado) setErro(err.message) })
      .finally(() => { if (!cancelado) setCarregando(false) })
    return () => { cancelado = true }
  }, [statusGlosa, idConvenio, idUsuarioResponsavel, pagina])

  useEffect(() => {
    fetchGlosaIndicadores().then(setIndicadores).catch(() => {})
  }, [])

  // Restaura a posição de rolagem salva ao voltar da página de detalhe.
  useEffect(() => {
    if (carregando) return
    const salvo = window.sessionStorage.getItem(chaveScroll)
    if (salvo && scrollRef.current) scrollRef.current.scrollTop = Number(salvo)
  }, [carregando, chaveScroll])

  function aoRolar() {
    if (!scrollRef.current) return
    window.sessionStorage.setItem(chaveScroll, String(scrollRef.current.scrollTop))
  }

  function alternarSelecionado(id) {
    setSelecionados((prev) => {
      const proximo = new Set(prev)
      if (proximo.has(id)) proximo.delete(id); else proximo.add(id)
      return proximo
    })
  }

  function alternarTodos() {
    setSelecionados((prev) => (prev.size === linhas.length ? new Set() : new Set(linhas.map((l) => l.id))))
  }

  async function atribuirEmLote(idUsuario) {
    if (!idUsuario || selecionados.size === 0 || atribuindo) return
    setAtribuindo(true)
    try {
      await Promise.all([...selecionados].map((id) => alterarResponsavelGlosa(id, Number(idUsuario))))
      const dados = await fetchGlosas({ status: statusGlosa || undefined, idConvenio: idConvenio || undefined, idUsuarioResponsavel: idUsuarioResponsavel || undefined, page: pagina })
      setLinhas(dados.content)
      setSelecionados(new Set())
    } catch (err) {
      setErro(err.message)
    } finally {
      setAtribuindo(false)
    }
  }

  function abrir(id) {
    onAbrirGlosa(id, linhas.map((l) => l.id))
  }

  const vazio = !carregando && linhas.length === 0 && !erro

  return (
    <div className="convenios-painel">
      {indicadores && (
        <div className="convenios-painel-head" style={{ gap: 24, flexWrap: 'wrap' }}>
          <span className="convenios-indicador"><TrendingUp size={14} strokeWidth={2} /> Taxa de recuperação <strong>{indicadores.taxaRecuperacaoPct.toFixed(0)}%</strong></span>
          <span className="convenios-indicador"><Wallet size={14} strokeWidth={2} /> Recuperável <strong>{brl(indicadores.valorRecuperavel)}</strong></span>
          <span className="convenios-indicador"><CheckCircle2 size={14} strokeWidth={2} /> Recuperado <strong>{brl(indicadores.valorRecuperado)}</strong></span>
          <span className="convenios-indicador"><XCircle size={14} strokeWidth={2} /> Perdido <strong>{brl(indicadores.valorPerdido)}</strong></span>
          <span className="convenios-indicador">Recursos pendentes <strong>{indicadores.recursosPendentes}</strong></span>
        </div>
      )}

      {erro && <div className="convenios-modal-erro" style={{ margin: '0 16px 16px' }}>{erro}</div>}

      {selecionados.size > 0 && (
        <div className="convenios-bulk">
          <span className="convenios-bulk-txt">{selecionados.size} {selecionados.size === 1 ? 'glosa selecionada' : 'glosas selecionadas'}</span>
          <div className="convenios-bulk-actions">
            <select
              className="convenios-sel" disabled={atribuindo} defaultValue=""
              onChange={(e) => atribuirEmLote(e.target.value)}
              aria-label="Atribuir responsável às selecionadas"
            >
              <option value="" disabled>Atribuir responsável…</option>
              {usuarios.map((u) => <option key={u.id} value={u.id}>{u.nome}</option>)}
            </select>
            <button type="button" className="convenios-bclear" onClick={() => setSelecionados(new Set())}>Limpar seleção</button>
          </div>
        </div>
      )}

      {vazio ? (
        <div className="convenios-vazio">
          <div className="convenios-vazio-titulo">Nenhuma glosa pendente. Tudo em dia.</div>
          <div className="convenios-vazio-texto">Novas glosas entram nesta fila automaticamente conforme os convênios recusam atendimentos faturados.</div>
        </div>
      ) : (
        <div className="convenios-tabela-scroll" ref={scrollRef} onScroll={aoRolar}>
          <table className="convenios-tabela convenios-tabela-lista">
            <thead>
              <tr>
                <th style={{ width: 34 }}>
                  <input type="checkbox" className="convenios-chk" aria-label="Selecionar todas" checked={linhas.length > 0 && selecionados.size === linhas.length} onChange={alternarTodos} />
                </th>
                <th>Paciente / procedimento</th>
                <th>Convênio</th>
                <th>Recurso</th>
                <th className="num">Valor</th>
                <th>Prazo</th>
                <th>Status</th>
                <th>Responsável</th>
              </tr>
            </thead>
            <tbody>
              {carregando && Array.from({ length: 6 }).map((_, i) => (
                <tr key={`sk-${i}`}>
                  <td colSpan={8} style={{ padding: '14px 16px' }}>
                    <div style={{ height: 12, width: '70%', borderRadius: 6, background: 'var(--surface-muted)' }} />
                  </td>
                </tr>
              ))}
              {!carregando && linhas.map((g) => {
                const meta = statusGlosaMeta(g.status)
                return (
                  <tr key={g.id} className={meta.cls === 'st-perdida' ? 'perdida' : ''}>
                    <td onClick={(e) => e.stopPropagation()}>
                      <input type="checkbox" className="convenios-chk" aria-label={`Selecionar glosa ${g.id}`} checked={selecionados.has(g.id)} onChange={() => alternarSelecionado(g.id)} />
                    </td>
                    <td onClick={() => abrir(g.id)}>
                      <div className="convenios-pac">{g.pacienteNome}</div>
                      <div className="convenios-proc">{g.procedimento} · {g.dataGlosaTxt}</div>
                    </td>
                    <td onClick={() => abrir(g.id)}>{g.convenioNome}</td>
                    <td onClick={() => abrir(g.id)} className="convenios-motivo-txt">{g.statusRecursoAtual ?? '—'}</td>
                    <td className="num convenios-valor" onClick={() => abrir(g.id)}>{brl(g.valorGlosado)}</td>
                    <td onClick={() => abrir(g.id)}>
                      <div className={`convenios-prazo-val ${corPrazoClasse(g.corPrazo)}`}>
                        {g.prazoRecursoTxt ?? '—'}
                      </div>
                      {g.diasRestantes != null && (
                        <div className="convenios-prazo-sub">{g.diasRestantes >= 0 ? `${g.diasRestantes} dias` : 'expirado'}</div>
                      )}
                    </td>
                    <td onClick={() => abrir(g.id)}>
                      <span className={`convenios-badge ${meta.cls}`}>{meta.rotulo}</span>
                    </td>
                    <td onClick={() => abrir(g.id)}>
                      {g.responsavelNome ? (
                        <div className="convenios-resp"><span className="convenios-resp-av">{g.responsavelNome.slice(0, 2).toUpperCase()}</span>{g.responsavelNome}</div>
                      ) : (
                        <div className="convenios-resp-vazio"><span className="convenios-resp-vazio-av" />Sem responsável</div>
                      )}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}

      {!vazio && (
        <div className="convenios-foot">
          <span>{totalElements} {totalElements === 1 ? 'glosa' : 'glosas'}</span>
          {totalPages > 1 && (
            <div className="convenios-filtros">
              <button type="button" className="convenios-btn-ghost sm" disabled={pagina === 0} onClick={() => setPagina((p) => p - 1)}>‹ Anterior</button>
              <span style={{ fontSize: 12.5, color: 'var(--text-tertiary)' }}>Página {pagina + 1} de {totalPages}</span>
              <button type="button" className="convenios-btn-ghost sm" disabled={pagina >= totalPages - 1} onClick={() => setPagina((p) => p + 1)}>Próxima ›</button>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
