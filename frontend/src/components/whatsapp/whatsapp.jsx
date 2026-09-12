import { useCallback, useEffect, useState } from 'react'
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
  const [status, setStatus] = useState(null)
  const [aguardando, setAguardando] = useState(0)

  const [erroStatus, setErroStatus] = useState(null)
  const [verificacao, setVerificacao] = useState(0)
  const atualizarContador = useCallback((lista) => setAguardando(lista.filter((c) => c.estado === 'aguardando').length), [])

  useEffect(() => {
    let cancelado = false
    let timer
    async function atualizar() {
      try {
        const dados = await fetchStatus()
        if (!cancelado) { setStatus(dados); setErroStatus(null) }
      } catch {
        if (!cancelado) { setStatus(null); setErroStatus('Não foi possível verificar a conexão. Verifique o serviço de WhatsApp.') }
      } finally {
        if (!cancelado) timer = setTimeout(atualizar, 30000)
      }
    }
    atualizar()
    fetchConversas().then((lista) => { if (!cancelado) atualizarContador(lista) }).catch(() => {})
    return () => { cancelado = true; clearTimeout(timer) }
  }, [verificacao, atualizarContador])

  return (
    <div className="whatsapp-page">
      {(erroStatus || (status && !status?.conectado)) && (
        <div className="whatsapp-banner-desconectado">
          <AlertTriangle size={16} strokeWidth={2} />
          <span>{erroStatus || (status.configurado === false
            ? 'O WhatsApp da clínica ainda não foi configurado.'
            : 'Não foi possível conectar ao WhatsApp da clínica. Verifique a configuração da integração.')}</span>
          <button type="button" onClick={() => setVerificacao((n) => n + 1)}>Verificar novamente</button>
        </div>
      )}

      <div className="whatsapp-head">
        <div className="whatsapp-head-titulo">
          <h1>WhatsApp</h1>
          <span className={`whatsapp-status-pill ${status?.conectado ? 'ok' : 'off'}`}>
            <span className="whatsapp-status-dot" />
            {status?.conectado ? 'WhatsApp conectado' : status ? 'Desconectado' : erroStatus ? 'Indisponível' : 'Verificando…'}
          </span>
          {status?.numero && <span className="whatsapp-numero">{status?.numero}</span>}
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

      {aba === 'conversas' && <WhatsappConversas onConversasAtualizadas={atualizarContador} />}
      {aba === 'assistente' && <WhatsappAssistente />}
      {aba === 'desempenho' && <WhatsappDesempenho />}
    </div>
  )
}
