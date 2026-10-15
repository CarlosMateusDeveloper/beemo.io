import { useCallback, useEffect, useState } from 'react'
import { AlertTriangle, CheckCircle2, Clock3, RefreshCw, RotateCcw } from 'lucide-react'
import { usePermissions } from '../../auth/permissions'
import { fetchMensageria, fetchMensageriaResumo, reprocessarMensagem } from './api'

const STATUS = [
  ['', 'Todos'], ['pendente', 'Na fila'], ['retentativa', 'Retentativas'],
  ['morta', 'Intervenção'], ['enviada', 'Enviadas'],
]

function data(value) {
  return value ? new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value)) : '—'
}

export default function WhatsappOperacao() {
  const { can } = usePermissions()
  const [summary, setSummary] = useState(null)
  const [items, setItems] = useState([])
  const [status, setStatus] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(null)

  const load = useCallback(async () => {
    try {
      const [nextSummary, nextItems] = await Promise.all([fetchMensageriaResumo(), fetchMensageria(status)])
      setSummary(nextSummary); setItems(nextItems); setError('')
    } catch (err) { setError(err.message) }
  }, [status])

  useEffect(() => {
    load()
    const timer = setInterval(load, 10000)
    return () => clearInterval(timer)
  }, [load])

  async function retry(id) {
    setBusy(id)
    try { await reprocessarMensagem(id); await load() } catch (err) { setError(err.message) } finally { setBusy(null) }
  }

  return <section className="whatsapp-operacao" aria-label="Operação da mensageria">
    <div className="whatsapp-operacao-toolbar">
      <div><h2>Saúde dos envios</h2><p>Fila, retentativas e falhas do provedor em tempo real.</p></div>
      <button type="button" className="whatsapp-btn-secundario" onClick={load}><RefreshCw size={15} /> Atualizar</button>
    </div>
    {error && <div className="whatsapp-op-alert danger" role="alert"><AlertTriangle size={16} />{error}</div>}
    {summary?.alertas?.map((alert) => <div key={alert} className="whatsapp-op-alert"><AlertTriangle size={16} />{alert}</div>)}
    <div className="whatsapp-op-cards">
      <article><Clock3 size={18} /><span>Na fila</span><strong>{summary?.fila ?? '—'}</strong></article>
      <article><RotateCcw size={18} /><span>Retentativas</span><strong>{summary?.retentativas ?? '—'}</strong></article>
      <article className={summary?.mortas ? 'danger' : ''}><AlertTriangle size={18} /><span>Intervenção</span><strong>{summary?.mortas ?? '—'}</strong></article>
      <article><CheckCircle2 size={18} /><span>Enviadas</span><strong>{summary?.enviadas ?? '—'}</strong></article>
      <article><span>Provedor Meta</span><strong className={`whatsapp-op-state ${summary?.provedor?.estado || 'fechado'}`}>{summary?.provedor?.estado || '—'}</strong><small>{summary?.provedor?.falhas_consecutivas || 0} falhas seguidas</small></article>
    </div>
    {summary && <div className="whatsapp-op-metrics">
      <span>Latência média: <b>{summary.latenciaMediaSeg}s</b></span>
      <span>Última hora: <b>{summary.enviadasUltimaHora} enviadas</b></span>
      <span>Erros na última hora: <b>{summary.errosUltimaHora}</b></span>
      <span>Mais antiga na fila: <b>{summary.idadeMaisAntigaSeg}s</b></span>
    </div>}
    <div className="whatsapp-op-filter">
      {STATUS.map(([value, label]) => <button key={value} type="button" className={status === value ? 'active' : ''} onClick={() => setStatus(value)}>{label}</button>)}
    </div>
    <div className="whatsapp-op-table-wrap"><table className="whatsapp-op-table">
      <thead><tr><th>ID</th><th>Destino</th><th>Origem</th><th>Status</th><th>Tentativas</th><th>Criada em</th><th>Último erro</th><th /></tr></thead>
      <tbody>{items.map((item) => <tr key={item.id_outbox}>
        <td>#{item.id_outbox}</td><td>{item.destino}</td><td>{item.origem}</td>
        <td><span className={`whatsapp-op-badge ${item.status}`}>{item.status}</span></td>
        <td>{item.tentativas}/{item.max_tentativas}</td><td>{data(item.criado_em)}</td>
        <td title={item.erro_mensagem || ''}>{item.erro_codigo || '—'}</td>
        <td>{item.status === 'morta' && can('mensageria.operar') && <button type="button" disabled={busy === item.id_outbox} onClick={() => retry(item.id_outbox)}>Reprocessar</button>}</td>
      </tr>)}</tbody>
    </table>{!items.length && <p className="whatsapp-op-empty">Nenhum envio neste filtro.</p>}</div>
  </section>
}
