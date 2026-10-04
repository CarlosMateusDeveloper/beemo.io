import { useEffect, useRef, useState } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { Mail, Lock } from 'lucide-react'
import { useAuth } from '../../auth/AuthContext'
import { apiRequest } from '../../lib/apiClient'
import './login.css'

export default function Login() {
  const { login, autenticado, carregando: restaurando } = useAuth()
  const location = useLocation()
  const [providers, setProviders] = useState([])
  const oauthError = new URLSearchParams(location.search).get('oauth_error')
  useEffect(() => {
    let active = true
    apiRequest('/api/auth/providers', { authenticated: false }).then(data => { if (active) setProviders(data) }).catch(() => {})
    return () => { active = false }
  }, [])
  const [modo, setModo] = useState('senha')
  const [email, setEmail] = useState('')
  const [senha, setSenha] = useState('')
  const [erro, setErro] = useState('')
  const [aviso, setAviso] = useState('')
  const [carregando, setCarregando] = useState(false)
  const enviando = useRef(false)
  const destino = location.state?.from || '/'
  async function enviar(e) {
    e.preventDefault()
    if (enviando.current) return
    enviando.current = true
    setCarregando(true); setErro(''); setAviso('')
    try {
      if (modo === 'senha') await login(email.trim(), senha)
      else {
        const resposta = await apiRequest('/api/auth/magic-link', {
          method: 'POST', authenticated: false, body: JSON.stringify({ email: email.trim() }),
        })
        setAviso(resposta.message)
      }
    } catch (e) { setErro(e.message) }
    finally { enviando.current = false; setCarregando(false) }
  }
  if (restaurando) return <div className="login-page" role="status">Verificando sessão…</div>
  if (autenticado) return <Navigate to={destino.startsWith('/') && !destino.startsWith('//') ? destino : '/'} replace />
  return <div className="login-page login-page--photo">
    <div className="login-card">
      <div className="login-brand">ClinicOS</div>
      <h1 className="login-titulo">Entrar na sua clínica</h1>
      <p className="login-subtitulo">{modo === 'senha' ? 'Use o e-mail e a senha da sua conta.' : 'Receba um link de acesso no seu e-mail, sem precisar da senha.'}</p>
      {oauthError && <p className="login-erro" role="alert">{oauthError === 'unlinked' ? 'Esta conta ainda não está vinculada. Entre com senha ou link por e-mail e acesse Perfil para vinculá-la.' : 'Não foi possível entrar com o provedor. Tente novamente.'}</p>}
      {providers.map(provider => <button key={provider.id} className="login-btn login-social" disabled={carregando} onClick={() => window.location.assign(provider.url)}>Entrar com {provider.nome}</button>)}
      <div className="login-modos" aria-label="Forma de acesso">
        <button type="button" aria-pressed={modo === 'senha'} disabled={carregando} onClick={() => { setModo('senha'); setErro(''); setAviso('') }}>Com senha</button>
        <button type="button" aria-pressed={modo === 'link'} disabled={carregando} onClick={() => { setModo('link'); setSenha(''); setErro('') }}>Link por e-mail</button>
      </div>
      <form className="login-form" onSubmit={enviar}>
        <label className="login-campo"><span className="login-label">E-mail</span><span className="login-input-wrap"><Mail size={16} className="login-input-icon" />
          <input type="email" required maxLength={100} className="login-input" autoComplete="username" value={email} onChange={e => { setEmail(e.target.value); setAviso('') }} disabled={carregando} placeholder="voce@clinica.com.br" /></span></label>
        {modo === 'senha' && <label className="login-campo"><span className="login-label">Senha</span><span className="login-input-wrap"><Lock size={16} className="login-input-icon" />
          <input type="password" required maxLength={200} className="login-input" autoComplete="current-password" value={senha} onChange={e => setSenha(e.target.value)} disabled={carregando} /></span></label>}
        {erro && <div className="login-erro" role="alert">{erro}</div>}
        {aviso && <div className="login-aviso" role="status">{aviso}<p>O link vale por 15 minutos e pode ser usado uma vez.</p></div>}
        <button className="login-btn" type="submit" disabled={carregando}>{carregando ? (modo === 'senha' ? 'Entrando…' : 'Solicitando link…') : modo === 'senha' ? 'Entrar' : aviso ? 'Solicitar outro link' : 'Receber link de acesso'}</button>
      </form>
      <p className="login-ajuda">Acesso exclusivo para contas cadastradas pela administração da clínica.</p>
    </div>
  </div>
}
