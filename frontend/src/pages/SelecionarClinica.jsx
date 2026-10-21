import { useEffect, useState } from 'react'
import { Building2, Clock3, ShieldCheck } from 'lucide-react'
import { apiRequest } from '../lib/apiClient'
import './SelecionarClinica.css'

export default function SelecionarClinica() {
  const [items, setItems] = useState([])
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(null)
  useEffect(() => { apiRequest('/api/tenants').then(setItems).catch(e => setError(e.message)) }, [])
  async function select(item) {
    setBusy(item.id); setError('')
    try { await apiRequest(`/api/tenants/${item.id}/select`, { method: 'POST' }); window.location.assign('/dashboard') }
    catch (e) { setError(e.message); setBusy(null) }
  }
  return <main className="tenant-picker">
    <section className="tenant-picker-card">
      <ShieldCheck size={34} /><h1>Escolha o ambiente</h1>
      <p>Seu acesso será limitado às permissões deste ambiente.</p>
      {error && <div className="support-error" role="alert">{error}</div>}
      <div className="tenant-list">{items.map(item => <button key={`${item.id}-${item.suporteAcessoId || 'member'}`} onClick={() => select(item)} disabled={busy !== null}>
        <Building2 size={22} /><span><strong>{item.nome}</strong><small>{item.suporteTemporario ? 'Suporte temporário' : item.papeis.join(' + ')}</small></span>
        {item.expiraEm && <time><Clock3 size={14} /> até {new Date(item.expiraEm).toLocaleString('pt-BR')}</time>}
      </button>)}</div>
      {!error && items.length === 0 && <p>Nenhuma clínica autorizou acesso para esta conta.</p>}
    </section>
  </main>
}
