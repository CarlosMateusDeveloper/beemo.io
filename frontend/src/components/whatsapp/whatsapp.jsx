import { useEffect, useState } from 'react'
import { AlertTriangle } from 'lucide-react'
import WhatsappConversas from './WhatsappConversas'
import WhatsappAssistente from './WhatsappAssistente'
import WhatsappDesempenho from './WhatsappDesempenho'
import { fetchStatus, fetchConversas } from './api'
import './whatsapp.css'

const ABAS = [
  { id: 'conversas', label: 'Conversas' },
  { id: 'assistente', label: 'Assistente' },
  { id: 'desempenho', label: 'Desempenho' },
]

export function Whatsapp() {
  const [aba, setAba] = useState('conversas')
  const [status, setStatus] = useState({ conectado: false, numero: null })
  const [aguardando, setAguardando] = useState(0)

  useEffect(() => {
    let cancelado = false
    fetchStatus()
      .then((s) => { if (!cancelado) setStatus(s) })
      .catch(() => {})
    fetchConversas()
      .then((lista) => { if (!cancelado) setAguardando(lista.filter((c) => c.estado === 'aguardando').length) })
      .catch(() => {})
    return () => { cancelado = true }
  }, [aba])

  return (
    <div className="whatsapp-page">
      {!status.conectado && (
        <div className="whatsapp-banner-desconectado">
          <AlertTriangle size={16} strokeWidth={2} />
          <span>Assistente desconectado do WhatsApp — nenhum provedor (Meta Cloud API, Twilio etc.) foi configurado ainda.</span>
        </div>
      )}

      <div className="whatsapp-head">
        <div className="whatsapp-head-titulo">
          <h1>WhatsApp</h1>
          <span className={`whatsapp-status-pill ${status.conectado ? 'ok' : 'off'}`}>
            <span className="whatsapp-status-dot" />
            {status.conectado ? 'Assistente ativo' : 'Desconectado'}
          </span>
          {status.numero && <span className="whatsapp-numero">{status.numero}</span>}
        </div>

        <nav className="whatsapp-tabs" aria-label="Seções do WhatsApp">
          {ABAS.map((t) => (
            <button
              key={t.id} type="button"
              className={`whatsapp-tab${aba === t.id ? ' active' : ''}`}
              onClick={() => setAba(t.id)}
            >
              {t.label}
              {t.id === 'conversas' && aguardando > 0 && <span className="whatsapp-tab-badge">{aguardando}</span>}
            </button>
          ))}
        </nav>
      </div>

      {aba === 'conversas' && <WhatsappConversas onConversasAtualizadas={(lista) => setAguardando(lista.filter((c) => c.estado === 'aguardando').length)} />}
      {aba === 'assistente' && <WhatsappAssistente />}
      {aba === 'desempenho' && <WhatsappDesempenho />}
    </div>
  )
}
