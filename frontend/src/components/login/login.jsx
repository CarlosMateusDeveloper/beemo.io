import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Eye, EyeOff, Lock, Mail } from 'lucide-react'
import ThemeToggle from '../../theme/ThemeToggle'
import './login.css'

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

function GoogleIcon() {
  return (
    <svg width="17" height="17" viewBox="0 0 18 18" aria-hidden="true">
      <path fill="#4285F4" d="M17.64 9.2c0-.637-.057-1.251-.164-1.84H9v3.481h4.844c-.209 1.125-.843 2.078-1.796 2.717v2.258h2.908c1.702-1.567 2.684-3.874 2.684-6.615z" />
      <path fill="#34A853" d="M9 18c2.43 0 4.467-.806 5.956-2.18l-2.908-2.259c-.806.54-1.837.86-3.048.86-2.344 0-4.328-1.584-5.036-3.711H.957v2.332C2.438 15.983 5.482 18 9 18z" />
      <path fill="#FBBC05" d="M3.964 10.71A5.41 5.41 0 013.682 9c0-.593.102-1.17.282-1.71V4.958H.957A8.996 8.996 0 000 9c0 1.452.348 2.827.957 4.042l3.007-2.332z" />
      <path fill="#EA4335" d="M9 3.58c1.321 0 2.508.454 3.44 1.345l2.582-2.58C13.463.891 11.426 0 9 0 5.482 0 2.438 2.017.957 4.958L3.964 7.29C4.672 5.163 6.656 3.58 9 3.58z" />
    </svg>
  )
}

function MicrosoftIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 21 21" aria-hidden="true">
      <rect x="1" y="1" width="9" height="9" fill="#F25022" />
      <rect x="11" y="1" width="9" height="9" fill="#7FBA00" />
      <rect x="1" y="11" width="9" height="9" fill="#00A4EF" />
      <rect x="11" y="11" width="9" height="9" fill="#FFB900" />
    </svg>
  )
}

export default function Login() {
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [senha, setSenha] = useState('')
  const [mostrarSenha, setMostrarSenha] = useState(false)
  const [erro, setErro] = useState(null)
  const [carregando, setCarregando] = useState(false)

  function entrarComSso(provedor) {
    setErro(`Login com ${provedor} ainda não implementado.`)
  }

  function handleSubmit(e) {
    e.preventDefault()
    if (!email.trim() || !EMAIL_RE.test(email.trim())) {
      setErro('Informe um e-mail válido.')
      return
    }
    if (!senha) {
      setErro('Informe sua senha.')
      return
    }
    setErro(null)
    setCarregando(true)
    // Sem endpoint de autenticação ainda — simula a latência e segue para o app.
    setTimeout(() => navigate('/'), 600)
  }

  return (
    <div className="login-page">
      <div className="login-theme"><ThemeToggle /></div>

      <div className="login-card">
        <div className="login-brand">ClinicOS</div>

        <div className="login-sso">
          <button type="button" className="login-sso-btn" onClick={() => entrarComSso('Google')}>
            <GoogleIcon />Continuar com Google
          </button>
          <button type="button" className="login-sso-btn" onClick={() => entrarComSso('Microsoft')}>
            <MicrosoftIcon />Continuar com Microsoft
          </button>
        </div>

        <div className="login-divisor"><span>ou entre com e-mail</span></div>

        <form className="login-form" onSubmit={handleSubmit} noValidate>
          <div className="login-campo">
            <label className="login-label" htmlFor="login-email">E-mail</label>
            <div className="login-input-wrap">
              <Mail size={16} strokeWidth={1.8} className="login-input-icon" />
              <input
                id="login-email" type="email" className="login-input" placeholder="voce@clinica.com.br"
                value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="username" autoFocus
              />
            </div>
          </div>

          <div className="login-campo">
            <div className="login-label-row">
              <label className="login-label" htmlFor="login-senha">Senha</label>
              <button type="button" className="login-link" onClick={() => setErro('Recuperação de senha ainda não implementada.')}>
                Esqueci minha senha
              </button>
            </div>
            <div className="login-input-wrap">
              <Lock size={16} strokeWidth={1.8} className="login-input-icon" />
              <input
                id="login-senha" type={mostrarSenha ? 'text' : 'password'} className="login-input"
                placeholder="Sua senha" value={senha} onChange={(e) => setSenha(e.target.value)} autoComplete="current-password"
              />
              <button
                type="button" className="login-olho" onClick={() => setMostrarSenha((v) => !v)}
                aria-label={mostrarSenha ? 'Ocultar senha' : 'Mostrar senha'}
              >
                {mostrarSenha ? <EyeOff size={16} strokeWidth={1.8} /> : <Eye size={16} strokeWidth={1.8} />}
              </button>
            </div>
          </div>

          {erro && <div className="login-erro">{erro}</div>}

          <button type="submit" className="login-btn" disabled={carregando}>
            {carregando ? 'Entrando…' : 'Entrar'}
          </button>
        </form>
      </div>
    </div>
  )
}
