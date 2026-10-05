import { useEffect, useState } from 'react'
import { apiRequest } from '../lib/apiClient'
import './Clinicas.css'

export default function MembrosTenant({ tenant }) {
  const [members, setMembers] = useState([])
  const [email, setEmail] = useState('')
  const [perfil, setPerfil] = useState('medico')
  const [invite, setInvite] = useState('')
  const [erro, setErro] = useState('')
  const [busy, setBusy] = useState(false)
  const base = `/api/tenants/${tenant.id}`
  useEffect(() => {
    let active = true
    apiRequest(base + '/members').then(data => { if (active) setMembers(data) }).catch(e => { if (active) setErro(e.message) })
    return () => { active = false }
  }, [base])
  async function convidar(e) {
    e.preventDefault(); setBusy(true); setErro(''); setInvite('')
    try {
      const result = await apiRequest(base + '/invites', { method: 'POST', body: JSON.stringify({ email: email.trim(), perfil }) })
      setInvite(result.url); setEmail('')
      setMembers(await apiRequest(base + '/members'))
    } catch (e) { setErro(e.message) } finally { setBusy(false) }
  }
  async function revogar(id) {
    setBusy(true); setErro('')
    try {
      await apiRequest(`${base}/members/${id}`, { method: 'DELETE' })
      setMembers(await apiRequest(base + '/members'))
    } catch (e) { setErro(e.message) } finally { setBusy(false) }
  }
  return <section className="tenant-members"><h2>Acesso a {tenant.nome}</h2>
    <p>Autorize o e-mail e escolha o perfil apenas nesta clínica.</p>
    <form onSubmit={convidar}>
      <label>E-mail<input type="email" required maxLength={100} value={email} onChange={e => setEmail(e.target.value)} disabled={busy} /></label>
      <label>Perfil<select value={perfil} onChange={e => setPerfil(e.target.value)} disabled={busy}><option value="medico">Médico</option><option value="administrador">Administrador</option></select></label>
      <button disabled={busy}>Gerar convite</button>
    </form>
    {invite && <div role="status"><p>Compartilhe este convite com a pessoa. Ele expira em 7 dias.</p><input aria-label="Link do convite" readOnly value={invite} onFocus={e => e.target.select()} /></div>}
    {erro && <p role="alert">{erro}</p>}
    <ul>{members.map(m => <li key={m.id}>{m.nome} · {m.email || `Conta ${m.id}`} · {m.perfil} · {m.ativo ? 'Ativo' : 'Revogado'} {m.ativo && <button disabled={busy} onClick={() => revogar(m.id)}>Revogar acesso</button>}</li>)}</ul>
  </section>
}
