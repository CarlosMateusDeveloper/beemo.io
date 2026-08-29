import { useRef, useState } from 'react'
import { ArrowLeft, Mail } from 'lucide-react'
import ThemeToggle from '../../theme/ThemeToggle'
import './login.css'

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const TAMANHO_CODIGO = 6

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

// Sem senha: SSO ou código de 6 dígitos por e-mail (estilo "magic code").
// Nenhum dos dois envia/valida de verdade ainda — não há provedor OAuth nem
// serviço de e-mail configurado no backend. O login por senha continua
// funcionando via POST /api/auth/login (ver AuthContext), só não tem mais
// campo nesta tela.
export default function Login() {
  const [etapa, setEtapa] = useState('email') // 'email' | 'codigo'
  const [email, setEmail] = useState('')
  const [codigo, setCodigo] = useState(Array(TAMANHO_CODIGO).fill(''))
  const [erro, setErro] = useState(null)
  const [carregando, setCarregando] = useState(false)
  const inputsRef = useRef([])

  function entrarComSso(provedor) {
    setErro(`Login com ${provedor} ainda não implementado.`)
  }

  function handleEmailSubmit(e) {
    e.preventDefault()
    if (!email.trim() || !EMAIL_RE.test(email.trim())) {
      setErro('Informe um e-mail válido.')
      return
    }
    setErro(null)
    setCarregando(true)
    // Sem serviço de e-mail configurado ainda — a troca de tela é só visual,
    // nenhum código é enviado de fato.
    setTimeout(() => {
      setCarregando(false)
      setEtapa('codigo')
      requestAnimationFrame(() => inputsRef.current[0]?.focus())
    }, 500)
  }

  function voltarParaEmail() {
    setEtapa('email')
    setErro(null)
    setCodigo(Array(TAMANHO_CODIGO).fill(''))
  }

  function handleCodigoChange(indice, valor) {
    const digito = valor.replace(/\D/g, '').slice(-1)
    setCodigo((prev) => {
      const proximo = [...prev]
      proximo[indice] = digito
      return proximo
    })
    if (digito && indice < TAMANHO_CODIGO - 1) inputsRef.current[indice + 1]?.focus()
  }

  function handleCodigoKeyDown(indice, e) {
    if (e.key === 'Backspace' && !codigo[indice] && indice > 0) {
      inputsRef.current[indice - 1]?.focus()
    }
  }

  function handleCodigoPaste(e) {
    const digitos = e.clipboardData.getData('text').replace(/\D/g, '').slice(0, TAMANHO_CODIGO)
    if (!digitos) return
    e.preventDefault()
    const proximo = digitos.split('').concat(Array(TAMANHO_CODIGO).fill('')).slice(0, TAMANHO_CODIGO)
    setCodigo(proximo)
    inputsRef.current[Math.min(digitos.length, TAMANHO_CODIGO - 1)]?.focus()
  }

  function handleVerificar(e) {
    e.preventDefault()
    setErro('Verificação por e-mail ainda não implementada — não há serviço de e-mail configurado.')
  }

  function reenviarCodigo() {
    setErro('Reenvio ainda não implementado — não há serviço de e-mail configurado.')
  }

  const codigoCompleto = codigo.every((d) => d !== '')

  return (
    <div className="login-page">
      <div className="login-theme"><ThemeToggle /></div>

      <div className="login-card">
        <div className="login-brand">ClinicOS</div>

        {etapa === 'email' ? (
          <>
            <h1 className="login-titulo">Entrar</h1>
            <p className="login-subtitulo">Use o e-mail da sua conta na clínica.</p>

            <form className="login-form" onSubmit={handleEmailSubmit} noValidate>
              <div className="login-campo">
                <div className="login-input-wrap">
                  <Mail size={16} strokeWidth={1.8} className="login-input-icon" />
                  <input
                    type="email" className="login-input" placeholder="voce@clinica.com.br"
                    value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="username" autoFocus
                  />
                </div>
              </div>

              {erro && <div className="login-erro">{erro}</div>}

              <button type="submit" className="login-btn" disabled={carregando}>
                {carregando ? 'Enviando…' : 'Continuar com e-mail'}
              </button>
            </form>

            <div className="login-divisor"><span>ou</span></div>

            <div className="login-sso">
              <button type="button" className="login-sso-btn" onClick={() => entrarComSso('Google')}>
                <GoogleIcon />Continuar com Google
              </button>
              <button type="button" className="login-sso-btn" onClick={() => entrarComSso('Microsoft')}>
                <MicrosoftIcon />Continuar com Microsoft
              </button>
            </div>
          </>
        ) : (
          <>
            <button type="button" className="login-voltar" onClick={voltarParaEmail}>
              <ArrowLeft size={14} strokeWidth={2.2} />
              Usar outro e-mail
            </button>

            <h1 className="login-titulo">Verifique seu e-mail</h1>
            <p className="login-subtitulo">Enviamos um código de {TAMANHO_CODIGO} dígitos para <strong>{email}</strong></p>

            <form className="login-form" onSubmit={handleVerificar} noValidate>
              <div className="login-codigo-row" onPaste={handleCodigoPaste}>
                {codigo.map((digito, i) => (
                  <input
                    key={i} ref={(el) => { inputsRef.current[i] = el }}
                    type="text" inputMode="numeric" pattern="[0-9]*" maxLength={1}
                    className="login-codigo-input" aria-label={`Dígito ${i + 1} do código`}
                    value={digito}
                    onChange={(e) => handleCodigoChange(i, e.target.value)}
                    onKeyDown={(e) => handleCodigoKeyDown(i, e)}
                  />
                ))}
              </div>

              {erro && <div className="login-erro">{erro}</div>}

              <button type="submit" className="login-btn" disabled={!codigoCompleto}>
                Verificar código
              </button>
            </form>

            <button type="button" className="login-link login-reenviar" onClick={reenviarCodigo}>
              Reenviar código
            </button>
          </>
        )}
      </div>
    </div>
  )
}
