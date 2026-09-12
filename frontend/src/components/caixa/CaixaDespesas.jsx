import { useEffect, useState } from 'react'
import { Check, Plus, X } from 'lucide-react'
import {
  fetchDespesas, fetchDespesasResumo, criarDespesa, marcarDespesaPaga, cancelarDespesa,
} from './api'
import {
  brl, PERIODOS, CATEGORIAS_DESPESA, categoriaDespesaLabel, statusDespesaMeta,
} from './caixaData'
import './caixa.css'

const STATUS_FILTRO = [
  { valor: '', rotulo: 'Todas' },
  { valor: 'pendente', rotulo: 'Pendentes' },
  { valor: 'pago', rotulo: 'Pagas' },
  { valor: 'cancelado', rotulo: 'Canceladas' },
]

const FORM_VAZIO = { descricao: '', categoria: 'outros', valor: '', vencimento: '', fornecedor: '', observacoes: '' }

export function CaixaDespesas() {
  const [periodo, setPeriodo] = useState('Últimos 30 dias')
  const [status, setStatus] = useState('')
  const [categoria, setCategoria] = useState('')
  const [pagina, setPagina] = useState(0)

  const [despesas, setDespesas] = useState([])
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [resumo, setResumo] = useState(null)
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState(null)
  const [recarregarSinal, setRecarregarSinal] = useState(0)

  const [painel, setPainel] = useState(null) // null | 'nova'
  const [form, setForm] = useState(FORM_VAZIO)
  const [salvando, setSalvando] = useState(false)

  const [alvoPagamento, setAlvoPagamento] = useState(null)
  const [dataPagamento, setDataPagamento] = useState(() => new Date().toISOString().slice(0, 10))

  const [toast, setToast] = useState(null)

  useEffect(() => { setPagina(0) }, [periodo, status, categoria])

  useEffect(() => {
    let cancelado = false
    setCarregando(true)
    fetchDespesas({ status: status || undefined, categoria: categoria || undefined, periodo, page: pagina })
      .then((d) => {
        if (cancelado) return
        setDespesas(d.content)
        setTotalPages(d.totalPages)
        setTotalElements(d.totalElements)
        setErro(null)
      })
      .catch((err) => { if (!cancelado) setErro(err.message) })
      .finally(() => { if (!cancelado) setCarregando(false) })
    return () => { cancelado = true }
  }, [periodo, status, categoria, pagina, recarregarSinal])

  useEffect(() => {
    fetchDespesasResumo(periodo).then(setResumo).catch(() => {})
  }, [periodo, recarregarSinal])

  function mostrarToast(texto) {
    setToast(texto)
    setTimeout(() => setToast(null), 2600)
  }

  function recarregar() {
    setRecarregarSinal((n) => n + 1)
  }

  function salvarDespesa(e) {
    e.preventDefault()
    if (!form.descricao.trim() || !form.valor || !form.vencimento) return
    setSalvando(true)
    criarDespesa({
      descricao: form.descricao.trim(),
      categoria: form.categoria,
      valor: Number(form.valor.replace(',', '.')),
      vencimento: form.vencimento,
      fornecedor: form.fornecedor.trim() || null,
      observacoes: form.observacoes.trim() || null,
    })
      .then(() => {
        mostrarToast('Despesa cadastrada.')
        setPainel(null)
        setForm(FORM_VAZIO)
        recarregar()
      })
      .catch((err) => mostrarToast(`Não foi possível cadastrar (${err.message}).`))
      .finally(() => setSalvando(false))
  }

  function confirmarPagamento() {
    if (!alvoPagamento) return
    marcarDespesaPaga(alvoPagamento.id, dataPagamento)
      .then(() => {
        mostrarToast(`"${alvoPagamento.descricao}" marcada como paga.`)
        setAlvoPagamento(null)
        recarregar()
      })
      .catch((err) => mostrarToast(`Não foi possível registrar o pagamento (${err.message}).`))
  }

  function cancelar(despesa) {
    cancelarDespesa(despesa.id)
      .then(() => { mostrarToast(`"${despesa.descricao}" cancelada.`); recarregar() })
      .catch((err) => mostrarToast(`Não foi possível cancelar (${err.message}).`))
  }

  return (
    <div className="caixa-page caixa-financeiro">
      <div className="caixa-head" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 16, flexWrap: 'wrap' }}>
        <h1 className="caixa-titulo">Despesas</h1>
        <div style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
          <select className="caixa-input-sm" style={{ width: 'auto' }} value={periodo} onChange={(e) => setPeriodo(e.target.value)}>
            {PERIODOS.map((p) => <option key={p.valor} value={p.valor}>{p.rotulo}</option>)}
          </select>
          <button type="button" className="caixa-btn-primary" style={{ width: 'auto', padding: '10px 18px', display: 'inline-flex', alignItems: 'center', gap: 7 }} onClick={() => setPainel('nova')}>
            <Plus size={16} strokeWidth={2} />Nova despesa
          </button>
        </div>
      </div>

      {resumo && (
        <div className="caixa-resumos">
          <ResumoTile label="Total do período" valor={resumo.totalPeriodo} />
          <ResumoTile label="Pago" valor={resumo.totalPago} cor="var(--success)" />
          <ResumoTile label="Pendente" valor={resumo.totalPendente} cor="var(--warning)" />
          <ResumoTile label="Atrasado" valor={resumo.totalAtrasado} cor="var(--danger)" sub={`${resumo.quantidadeAtrasada} conta(s)`} />
        </div>
      )}

      <div className="caixa-painel">
        <div className="caixa-painel-head" style={{ gap: 10, flexWrap: 'wrap' }}>
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            {STATUS_FILTRO.map((s) => (
              <button key={s.valor} type="button" className={`caixa-pill ${status === s.valor ? 'active' : ''}`} onClick={() => setStatus(s.valor)}>
                {s.rotulo}
              </button>
            ))}
          </div>
          <select className="caixa-input-sm" style={{ width: 'auto' }} value={categoria} onChange={(e) => setCategoria(e.target.value)}>
            <option value="">Todas as categorias</option>
            {CATEGORIAS_DESPESA.map((c) => <option key={c.valor} value={c.valor}>{c.rotulo}</option>)}
          </select>
        </div>

        {erro && <div className="dashboard-erro">Não foi possível carregar as despesas ({erro}).</div>}

        {!erro && !carregando && despesas.length === 0 && (
          <div className="caixa-vazio">
            <div className="caixa-vazio-titulo">Nenhuma despesa encontrada</div>
            <div className="caixa-vazio-texto">Ajuste os filtros ou cadastre a primeira despesa do período.</div>
          </div>
        )}

        {(carregando || despesas.length > 0) && !erro && (
          <div className="caixa-tabela-scroll">
            <table style={{ width: '100%', borderCollapse: 'collapse' }}>
              <thead>
                <tr style={{ borderBottom: '1px solid var(--border)' }}>
                  {['Descrição', 'Categoria', 'Fornecedor', 'Vencimento', 'Status', ''].map((h, i) => (
                    <th key={h || i} style={{ textAlign: i >= 3 && i <= 4 ? 'left' : 'left', fontSize: 11, letterSpacing: '.06em', textTransform: 'uppercase', color: 'var(--text-tertiary)', padding: '10px 16px' }}>{h}</th>
                  ))}
                  <th style={{ textAlign: 'right', fontSize: 11, letterSpacing: '.06em', textTransform: 'uppercase', color: 'var(--text-tertiary)', padding: '10px 16px' }}>Valor</th>
                </tr>
              </thead>
              <tbody>
                {carregando && Array.from({ length: 5 }).map((_, i) => (
                  <tr key={`sk-${i}`}><td colSpan={7} style={{ padding: '14px 16px' }}><div style={{ height: 12, width: '70%', borderRadius: 6, background: 'var(--surface-muted)' }} /></td></tr>
                ))}
                {!carregando && despesas.map((d) => {
                  const meta = statusDespesaMeta(d.status, d.atrasado)
                  return (
                    <tr key={d.id} style={{ borderBottom: '1px solid var(--border-faint)' }}>
                      <td style={{ padding: '12px 16px' }}>{d.descricao}</td>
                      <td style={{ padding: '12px 16px', color: 'var(--text-secondary)' }}>{categoriaDespesaLabel(d.categoria)}</td>
                      <td style={{ padding: '12px 16px', color: 'var(--text-secondary)' }}>{d.fornecedor || '—'}</td>
                      <td style={{ padding: '12px 16px' }}>{d.vencimentoTxt}</td>
                      <td style={{ padding: '12px 16px' }}><span className={`caixa-badge ${meta.cls}`}>{meta.rotulo}</span></td>
                      <td style={{ padding: '12px 16px' }}>
                        {d.status === 'pendente' && (
                          <div style={{ display: 'flex', gap: 6, justifyContent: 'flex-end' }}>
                            <button type="button" className="caixa-btn-ghost" style={{ height: 28, padding: '0 10px', fontSize: 12 }} onClick={() => { setAlvoPagamento(d); setDataPagamento(new Date().toISOString().slice(0, 10)) }}>Marcar paga</button>
                            <button type="button" className="caixa-btn-ghost" style={{ height: 28, padding: '0 10px', fontSize: 12 }} onClick={() => cancelar(d)}>Cancelar</button>
                          </div>
                        )}
                      </td>
                      <td style={{ padding: '12px 16px', textAlign: 'right', fontWeight: 600 }} className="caixa-mono">{brl(d.valor)}</td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}

        {totalElements > 0 && (
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '12px 16px', borderTop: '1px solid var(--border-subtle)' }}>
            <span style={{ fontSize: 12.5, color: 'var(--text-tertiary)' }}>{totalElements} despesa(s)</span>
            {totalPages > 1 && (
              <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                <button type="button" className="caixa-btn-ghost" style={{ height: 28, padding: '0 10px', fontSize: 12 }} disabled={pagina === 0} onClick={() => setPagina((p) => p - 1)}>‹ Anterior</button>
                <span style={{ fontSize: 12.5, color: 'var(--text-tertiary)' }}>Página {pagina + 1} de {totalPages}</span>
                <button type="button" className="caixa-btn-ghost" style={{ height: 28, padding: '0 10px', fontSize: 12 }} disabled={pagina >= totalPages - 1} onClick={() => setPagina((p) => p + 1)}>Próxima ›</button>
              </div>
            )}
          </div>
        )}
      </div>

      {painel === 'nova' && (
        <>
          <div className="caixa-overlay" onClick={() => setPainel(null)} />
          <aside className="caixa-side">
            <div className="caixa-side-head">
              <div>
                <div className="caixa-side-kicker">Nova despesa</div>
                <div className="caixa-side-nome">Cadastrar conta a pagar</div>
              </div>
              <button type="button" className="caixa-icon-btn" onClick={() => setPainel(null)} aria-label="Fechar"><X size={15} strokeWidth={1.8} /></button>
            </div>

            <form onSubmit={salvarDespesa} style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
              <div>
                <label className="caixa-field-label">Descrição</label>
                <input type="text" className="caixa-input-sm" required value={form.descricao} onChange={(e) => setForm((f) => ({ ...f, descricao: e.target.value }))} placeholder="Ex.: Aluguel setembro" />
              </div>
              <div className="caixa-grid2">
                <div>
                  <label className="caixa-field-label">Categoria</label>
                  <select className="caixa-input-sm" value={form.categoria} onChange={(e) => setForm((f) => ({ ...f, categoria: e.target.value }))}>
                    {CATEGORIAS_DESPESA.map((c) => <option key={c.valor} value={c.valor}>{c.rotulo}</option>)}
                  </select>
                </div>
                <div>
                  <label className="caixa-field-label">Valor</label>
                  <input type="text" className="caixa-input-sm" required placeholder="0,00" value={form.valor} onChange={(e) => setForm((f) => ({ ...f, valor: e.target.value }))} />
                </div>
              </div>
              <div>
                <label className="caixa-field-label">Vencimento</label>
                <input type="date" className="caixa-input-sm" required value={form.vencimento} onChange={(e) => setForm((f) => ({ ...f, vencimento: e.target.value }))} />
              </div>
              <div>
                <label className="caixa-field-label">Fornecedor <span style={{ color: 'var(--text-tertiary)' }}>opcional</span></label>
                <input type="text" className="caixa-input-sm" value={form.fornecedor} onChange={(e) => setForm((f) => ({ ...f, fornecedor: e.target.value }))} />
              </div>
              <div>
                <label className="caixa-field-label">Observações <span style={{ color: 'var(--text-tertiary)' }}>opcional</span></label>
                <textarea rows={2} className="caixa-textarea" value={form.observacoes} onChange={(e) => setForm((f) => ({ ...f, observacoes: e.target.value }))} />
              </div>
              <button type="submit" className="caixa-btn-primary" disabled={salvando}>{salvando ? 'Salvando…' : 'Cadastrar despesa'}</button>
            </form>
          </aside>
        </>
      )}

      {alvoPagamento && (
        <>
          <div className="caixa-overlay" onClick={() => setAlvoPagamento(null)} />
          <div className="caixa-modal" style={{ width: 420 }}>
            <div className="caixa-modal-head">
              <div>
                <div className="caixa-modal-titulo">Marcar como paga</div>
                <div className="caixa-modal-sub">{alvoPagamento.descricao} · {brl(alvoPagamento.valor)}</div>
              </div>
              <button type="button" className="caixa-icon-btn" onClick={() => setAlvoPagamento(null)} aria-label="Fechar"><X size={15} strokeWidth={1.8} /></button>
            </div>
            <div>
              <label className="caixa-field-label">Data do pagamento</label>
              <input type="date" className="caixa-input-sm" value={dataPagamento} onChange={(e) => setDataPagamento(e.target.value)} />
            </div>
            <div className="caixa-modal-footer">
              <button type="button" className="caixa-btn-secondary" onClick={() => setAlvoPagamento(null)}>Cancelar</button>
              <button type="button" className="caixa-btn-primary" style={{ width: 'auto', padding: '12px 24px' }} onClick={confirmarPagamento}>
                <Check size={16} strokeWidth={2} style={{ marginRight: 6 }} />Confirmar pagamento
              </button>
            </div>
          </div>
        </>
      )}

      {toast && (
        <div className="caixa-toast">
          <span className="caixa-toast-dot" />
          {toast}
        </div>
      )}
    </div>
  )
}

function ResumoTile({ label, valor, cor, sub }) {
  return (
    <div className="caixa-painel" style={{ padding: '16px 18px' }}>
      <div style={{ fontSize: 13, color: 'var(--text-secondary)', marginBottom: 8 }}>{label}</div>
      <div className="caixa-mono caixa-resumo-valor" style={{ color: cor || 'var(--text-primary)' }}>{brl(valor)}</div>
      {sub && <div style={{ fontSize: 12, color: cor || 'var(--text-tertiary)', marginTop: 4 }}>{sub}</div>}
    </div>
  )
}
