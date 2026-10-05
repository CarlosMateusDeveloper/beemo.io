import { useEffect, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { useAuth } from './AuthContext'
import { apiRequest } from '../lib/apiClient'
import './Clinicas.css'

export default function Convite() {
  const { autenticado, carregando } = useAuth()
  const location = useLocation()
  const [token] = useState(() => new URLSearchParams(location.hash.slice(1)).get('token') || '')
  const [erro, setErro] = useState('')
  const [busy, setBusy] = useState(false)
  useEffect(() => { if (token) window.history.replaceState(null, '', '/convite') }, [token])
  async function aceitar() {
    setBusy(true); setErro('')
    try {
      await apiRequest('/api/tenants/invites/accept', { method: 'POST', body: JSON.stringify({ token }) })
      window.location.replace('/dashboard')
    } catch (e) { setErro(e.message); setBusy(false) }
  }
  return <main className="tenants-page"><section className="tenants-card">
    <div className="login-brand">ClinicOS</div><h1>Convite para uma clínica</h1>
    {carregando ? <p role="status">Verificando sessão…</p> : !autenticado ? <>
      <p>Entre com o e-mail que recebeu o convite. Após entrar, abra este link novamente.</p>
      <Link to="/login">Entrar no ClinicOS</Link>
    </> : token ? <><p>Aceite o convite para vincular sua conta a esta clínica.</p><button className="login-btn" disabled={busy} onClick={aceitar}>Aceitar convite</button></> : <p>Abra novamente o link completo do convite.</p>}
    {erro && <p role="alert" className="login-erro">{erro}</p>}
    <footer><Link to="/dashboard">Voltar ao ClinicOS</Link></footer>
  </section></main>
}
