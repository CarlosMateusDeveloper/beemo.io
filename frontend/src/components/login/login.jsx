import { useEffect, useRef, useState } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { Mail } from 'lucide-react'
import { useAuth } from '../../auth/AuthContext'
import { apiRequest } from '../../lib/apiClient'
import './login.css'

const PROVIDERS = [{ id: 'google', nome: 'Google' }, { id: 'microsoft', nome: 'Microsoft' }]

function ProviderIcon({ provider }) {
  return provider === 'microsoft'
    ? <svg aria-hidden="true" width="18" height="18" viewBox="0 0 20 20"><path fill="#f25022" d="M0 0h9v9H0z" /><path fill="#7fba00" d="M11 0h9v9h-9z" /><path fill="#00a4ef" d="M0 11h9v9H0z" /><path fill="#ffb900" d="M11 11h9v9h-9z" /></svg>
    : <svg aria-hidden="true" width="18" height="18" viewBox="0 0 24 24"><path fill="#4285f4" d="M21.6 12.23c0-.71-.06-1.39-.18-2.05H12v3.88h5.38a4.6 4.6 0 0 1-2 3.02v2.51h3.24c1.9-1.75 2.98-4.33 2.98-7.36Z" /><path fill="#34a853" d="M12 22c2.7 0 4.96-.9 6.62-2.41l-3.24-2.51c-.9.6-2.05.97-3.38.97-2.6 0-4.8-1.76-5.59-4.13H3.07v2.59A10 10 0 0 0 12 22Z" /><path fill="#fbbc05" d="M6.41 13.92A6 6 0 0 1 6.1 12c0-.67.11-1.31.31-1.92V7.49H3.07A10 10 0 0 0 2 12c0 1.61.39 3.14 1.07 4.51l3.34-2.59Z" /><path fill="#ea4335" d="M12 5.95c1.47 0 2.79.5 3.83 1.5l2.87-2.87A9.6 9.6 0 0 0 12 2a10 10 0 0 0-8.93 5.49l3.34 2.59C7.2 7.71 9.4 5.95 12 5.95Z" /></svg>
}

export default function Login() {
  const { autenticado, carregando: restaurando } = useAuth()
  const location = useLocation()
  const [providers, setProviders] = useState([])
  const [loadingProviders, setLoadingProviders] = useState(true)
  const [providersError, setProvidersError] = useState(false)
  const params = new URLSearchParams(location.search)
  const oauthError = params.get('oauth_error')
  const [email, setEmail] = useState('')
  const [erro, setErro] = useState('')
  const [aviso, setAviso] = useState('')
  const [carregando, setCarregando] = useState(false)
  const enviando = useRef(false)
  const destino = location.state?.from || '/dashboard'
  useEffect(() => {
    let active = true
    apiRequest('/api/auth/providers', { authenticated: false })
      .then(data => { if (active) setProviders(data) })
      .catch(() => { if (active) setProvidersError(true) })
      .finally(() => { if (active) setLoadingProviders(false) })
    return () => { active = false }
  }, [])
  async function enviar(e) {
    e.preventDefault()
    if (enviando.current) return
    enviando.current = true
    setCarregando(true); setErro(''); setAviso('')
    try {
      const resposta = await apiRequest('/api/auth/magic-link', {
        method: 'POST', authenticated: false, body: JSON.stringify({ email: email.trim() }),
      })
      setAviso(resposta.message)
    } catch (e) { setErro(e.message) }
    finally { enviando.current = false; setCarregando(false) }
  }
  function entrarSso(provider) {
    const available = providers.find(p => p.id === provider.id)
    if (!available) {
      setErro(providersError ? 'Não foi possível consultar as formas de acesso. Atualize a página e tente novamente.' : `O acesso com ${provider.nome} ainda não foi ativado nesta instalação. Solicite a configuração à administração.`)
      return
    }
    window.location.assign(available.url)
  }
  if (restaurando) return <div className="login-page" role="status">Verificando sessão…</div>
  if (autenticado) return <Navigate to={destino.startsWith('/') && !destino.startsWith('//') ? destino : '/dashboard'} replace />
  return <div className="login-page login-page--photo">
    <div className="login-card">
      <div className="login-brand">ClinicOS</div>
      <h1 className="login-titulo">Entrar na sua clínica</h1>
      <p className="login-subtitulo">Receba um link por e-mail ou continue com sua conta Google ou Microsoft. Sem senha.</p>
      {oauthError && <p className="login-erro" role="alert">Não foi possível concluir a autenticação com o provedor. Tente novamente.</p>}
      <form className="login-form" onSubmit={enviar}>
        <label className="login-campo"><span className="login-label">E-mail</span><span className="login-input-wrap"><Mail size={16} className="login-input-icon" />
          <input type="email" required maxLength={100} className="login-input" autoComplete="email" value={email} onChange={e => { setEmail(e.target.value); setAviso('') }} disabled={carregando} placeholder="voce@clinica.com.br" /></span></label>
        {erro && <div className="login-erro" role="alert">{erro}</div>}
        {aviso && <div className="login-aviso" role="status">{aviso}<p>O link vale por 15 minutos e pode ser usado uma vez.</p></div>}
        <button className="login-btn" type="submit" disabled={carregando}>{carregando ? 'Enviando link…' : aviso ? 'Enviar outro link' : 'Enviar link de acesso'}</button>
      </form>
      <div className="login-divisor login-divisor-sso"><span>ou continue com</span></div>
      <div className="login-sso">
        {PROVIDERS.map(provider => <button key={provider.id} type="button" className="login-sso-btn" disabled={carregando || loadingProviders} onClick={() => entrarSso(provider)}>
          <ProviderIcon provider={provider.id} /><span>Continuar com {provider.nome}</span>
          {!loadingProviders && !providersError && !providers.some(p => p.id === provider.id) && <small>Indisponível</small>}
        </button>)}
      </div>
      {providersError && <p className="login-ajuda" role="status">Não foi possível carregar a disponibilidade do Google e Microsoft. Atualize a página para tentar novamente.</p>}
      <p className="login-ajuda">Entre com seu e-mail para acessar diretamente seu ambiente de trabalho.</p>
    </div>
  </div>
}
