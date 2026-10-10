import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useAuth } from './AuthContext'
import { apiRequest } from '../lib/apiClient'
import './Conta.css'
import MembrosTenant from './MembrosTenant'
import AuditoriaAcesso from './AuditoriaAcesso'
import { hasPermission } from './permissions'

export default function Conta() {
  const { usuario } = useAuth()
  const [params] = useSearchParams()
  const [providers, setProviders] = useState([])
  const [linked, setLinked] = useState([])
  const [erro, setErro] = useState('')
  const [busy, setBusy] = useState('')
  const [loading, setLoading] = useState(true)
  useEffect(() => {
    let active = true
    Promise.all([apiRequest('/api/auth/providers'), apiRequest('/api/auth/oauth/identities')])
      .then(([available, identities]) => { if (active) { setProviders(available); setLinked(identities.map(identity => identity.provider)) } })
      .catch(e => { if (active) setErro(e.message) }).finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [])
  async function vincular(id) {
    setBusy(id); setErro('')
    try {
      const { url } = await apiRequest('/api/auth/oauth/link/' + id, { method: 'POST' })
      window.location.assign(url)
    } catch (e) { setErro(e.message); setBusy('') }
  }
  return <section className="conta-card">
    <Link to="/dashboard">Voltar ao ClinicOS</Link>
    <h1>Minha conta</h1>
    <p><strong>{usuario.nome}</strong><br />{usuario.email}</p>
    <h2>Formas de acesso</h2>
    <p>Vincule sua conta Google ou Microsoft para entrar com ela nas próximas visitas. Confirme que está usando sua própria conta.</p>
    {params.get('oauth') === 'linked' && <p role="status">Conta vinculada com sucesso.</p>}
    {params.has('oauth_error') && <p role="alert">Não foi possível vincular a conta. Verifique se ela já está vinculada a outro usuário e tente novamente.</p>}
    {erro && <p role="alert">{erro}</p>}
    {loading ? <p role="status">Carregando formas de acesso…</p> : providers.length === 0 && <p>Google e Microsoft ainda não foram configurados pela administração. Você pode entrar com um link por e-mail.</p>}
    {providers.map(provider => <div className="conta-provider" key={provider.id}>
      <span>{provider.nome}</span>
      {linked.includes(provider.id) ? <strong>Vinculada</strong> : <button disabled={!!busy} onClick={() => vincular(provider.id)}>{busy === provider.id ? 'Conectando…' : 'Vincular conta'}</button>}
    </div>)}
    {usuario.tenantAtivo && hasPermission(usuario, 'usuario.gerenciar') && <MembrosTenant tenant={usuario.tenantAtivo} />}
    {usuario.tenantAtivo && hasPermission(usuario, ['auditoria.visualizar', 'auditoria.financeira.visualizar'])
      && <AuditoriaAcesso tenant={usuario.tenantAtivo} />}
  </section>
}
