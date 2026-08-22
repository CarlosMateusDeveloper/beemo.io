import { Check } from 'lucide-react'
import { brl } from './caixaData'

export default function CaixaReceberHoje({ linhas, pendentesQtd, pendentesValor, onReceber }) {
  return (
    <section className="caixa-painel">
      <div className="caixa-painel-head">
        <div className="caixa-painel-titulo">
          <h2>A receber hoje</h2>
          <span className="caixa-painel-sub">da agenda</span>
        </div>
        <span className="caixa-painel-meta">{pendentesQtd} pendentes · <strong>{brl(pendentesValor)}</strong></span>
      </div>

      <div>
        {linhas.map((r) => (
          <div key={r.id} className="caixa-row">
            <span className="caixa-row-hora">{r.hora}</span>
            <div className="caixa-row-info">
              <span className="caixa-row-nome">{r.paciente}</span>
              <span className="caixa-row-sub">{r.procedimento}</span>
            </div>
            <div className="caixa-row-badge">
              {r.convenio
                ? <span className="caixa-badge-conv">{r.convenio}</span>
                : <span className="caixa-badge-part">Particular</span>}
            </div>
            <div className="caixa-row-valor-wrap">
              {r.convenio && <span className="caixa-row-nota">coparticipação</span>}
              <span className="caixa-row-valor">{brl(r.valor)}</span>
            </div>
            {r.pago && <span className="caixa-row-overlay" />}
            <div className="caixa-row-acao">
              {r.pago ? (
                <span className="caixa-row-recebido"><Check size={14} strokeWidth={2} />recebido</span>
              ) : (
                <button type="button" className="caixa-btn-receber" onClick={() => onReceber(r)}>Receber</button>
              )}
            </div>
          </div>
        ))}
      </div>
    </section>
  )
}
