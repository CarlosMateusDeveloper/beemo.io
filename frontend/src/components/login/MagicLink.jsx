import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/AuthContext'
import ThemeToggle from '../../theme/ThemeToggle'
import './login.css'

export default function MagicLink() {
  const [token] = useState(() => new URLSearchParams(window.location.hash.slice(1)).get('token') || '')
  const [erro, setErro] = useState('')
  const [carregando, setCarregando] = useState(false)
  const enviando = useRef(false)
  const { entrarComLink } = useAuth()
  const navigate = useNavigate()
  useEffect(() => { window.history.replaceState(window.history.state, '', window.location.pathname) }, [])
  async function confirmar() {
    if (enviando.current) return
    enviando.current = true; setCarregando(true); setErro('')
    try { await entrarComLink(token); navigate('/', { replace: true }) }
    catch (e) { setErro(e.message) }
    finally { enviando.current = false; setCarregando(false) }
  }
  return <div className="login-page">
    <div className="login-theme"><ThemeToggle /></div>
    <div className="login-card">
      <div className="login-brand">ClinicOS</div>
      <h1 className="login-titulo">Confirme seu acesso</h1>
      <p className="login-subtitulo">{token ? 'Continue para entrar com a conta que recebeu este link.' : 'Abra o link completo recebido por e-mail ou solicite um novo.'}</p>
      {erro && <p className="login-erro" role="alert">{erro}</p>}
      {token && <button className="login-btn login-confirmar" disabled={carregando} onClick={confirmar}>{carregando ? 'Validando link…' : 'Entrar no clinicOS'}</button>}
      <Link className="login-link login-reenviar" to="/login">Voltar ao login</Link>
    </div>
  </div>
}
