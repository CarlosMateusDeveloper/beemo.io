import { useState } from 'react'
import { AlertTriangle } from 'lucide-react'
import WhatsappConversas from './WhatsappConversas'
import WhatsappAssistente from './WhatsappAssistente'
import WhatsappDesempenho from './WhatsappDesempenho'
import { NUMERO_CLINICA, CONECTADO, CONVERSAS } from './whatsappData'
import './whatsapp.css'

const ABAS = [
  { id: 'conversas', label: 'Conversas' },
  { id: 'assistente', label: 'Assistente' },
  { id: 'desempenho', label: 'Desempenho' },
]

export function Whatsapp() {
  const [aba, setAba] = useState('conversas')
  const aguardando = CONVERSAS.filter((c) => c.estado === 'aguardando').length

  return (
    <div className="whatsapp-page">
      {!CONECTADO && (
        <div className="whatsapp-banner-desconectado">
          <AlertTriangle size={16} strokeWidth={2} />
          <span>Assistente desconectado do WhatsApp — a clínica está recebendo mensagens manualmente, sem o bot.</span>
          <button type="button">Reconectar</button>
        </div>
      )}

      <div className="whatsapp-head">
        <div className="whatsapp-head-titulo">
          <h1>WhatsApp</h1>
          <span className={`whatsapp-status-pill ${CONECTADO ? 'ok' : 'off'}`}>
            <span className="whatsapp-status-dot" />
            {CONECTADO ? 'Assistente ativo' : 'Desconectado'}
          </span>
          <span className="whatsapp-numero">{NUMERO_CLINICA}</span>
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

      {aba === 'conversas' && <WhatsappConversas />}
      {aba === 'assistente' && <WhatsappAssistente />}
      {aba === 'desempenho' && <WhatsappDesempenho />}
    </div>
  )
}
