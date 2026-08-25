import { ChevronRight } from 'lucide-react'
import { statusLabel } from './prontuarioData'

export default function ProntuarioHistorico({ atendimentos, onVerAtendimento, onContinuarAtendimento }) {
  if (atendimentos.length === 0) {
    return (
      <div className="prontuario-vazio">
        <div className="prontuario-vazio-titulo">Sem atendimentos registrados</div>
        <div className="prontuario-vazio-texto">Os atendimentos aparecem aqui assim que forem documentados.</div>
      </div>
    )
  }

  return (
    <div className="prontuario-historico">
      {atendimentos.map((a) => (
        <div key={a.consultaId} className="prontuario-item">
          <div className="prontuario-item-data">{a.dataTxt}</div>
          <div className="prontuario-item-corpo">
            <div className="prontuario-item-top">
              <span className="prontuario-item-profissional">{a.profissional}</span>
              <span className="prontuario-item-tipo">{a.tipo}</span>
              <span className="prontuario-item-hora">{a.hora}</span>
              <span className={`prontuario-badge prontuario-badge-${a.status}`}>
                <span className="prontuario-dot" />{statusLabel(a.status)}
              </span>
            </div>
            {a.resumo && <div className="prontuario-item-resumo">{a.resumo}</div>}
          </div>
          <div className="prontuario-item-acao">
            {a.status === 'finalizado' && (
              <button type="button" className="prontuario-btn-secundario" onClick={() => onVerAtendimento(a.prontuarioId)}>
                Ver atendimento <ChevronRight size={13} strokeWidth={2} />
              </button>
            )}
            {a.status === 'pendente' && (
              <button type="button" className="prontuario-btn-secundario" onClick={() => onContinuarAtendimento(a.prontuarioId)}>
                Continuar atendimento
              </button>
            )}
          </div>
        </div>
      ))}
    </div>
  )
}
