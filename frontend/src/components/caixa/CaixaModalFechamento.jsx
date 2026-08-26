import { AlertTriangle, Banknote, Check, CreditCard, QrCode } from 'lucide-react'
import { brl } from './caixaData'

export default function CaixaModalFechamento({
  turno, contado, onContado, obs, onObs, diferenca, temDiferenca, podeConfirmar, onFechar, onConfirmar,
}) {
  return (
    <>
      <div className="caixa-overlay" onClick={onFechar} />
      <div className="caixa-modal">
        <div className="caixa-modal-head">
          <div>
            <div className="caixa-modal-titulo">Fechamento de turno</div>
            <div className="caixa-modal-sub">Turno {turno.horaAbertura} · {turno.operador} · {turno.recebimentosHoje} recebimentos</div>
          </div>
          <button type="button" className="caixa-icon-btn" onClick={onFechar} aria-label="Fechar">✕</button>
        </div>

        <div className="caixa-tabela">
          <div className="caixa-tabela-head">
            <span>Forma</span>
            <span style={{ textAlign: 'right' }}>Esperado</span>
            <span style={{ textAlign: 'right' }}>Contado na gaveta</span>
          </div>

          <div className="caixa-tabela-row">
            <span className="caixa-tabela-forma"><Banknote size={16} strokeWidth={1.6} />Dinheiro</span>
            <span className="caixa-tabela-esperado">{brl(turno.dinheiro)}</span>
            <div className="caixa-tabela-contado">
              <span style={{ fontSize: 13, color: 'var(--text-tertiary)' }}>R$</span>
              <input type="text" value={contado} onChange={(e) => onContado(e.target.value)} />
            </div>
          </div>

          <div className="caixa-tabela-row">
            <span className="caixa-tabela-forma"><CreditCard size={16} strokeWidth={1.6} />Cartão</span>
            <span className="caixa-tabela-esperado">{brl(turno.cartao)}</span>
            <span className="caixa-tabela-auto">conferência automática</span>
          </div>

          <div className="caixa-tabela-row">
            <span className="caixa-tabela-forma"><QrCode size={16} strokeWidth={1.6} />Pix</span>
            <span className="caixa-tabela-esperado">{brl(turno.pix)}</span>
            <span className="caixa-tabela-auto">conferência automática</span>
          </div>

          <div className="caixa-tabela-row total">
            <span>Total do turno</span>
            <span className="caixa-tabela-esperado" style={{ fontWeight: 600, fontSize: 17 }}>{brl(turno.totalHoje)}</span>
            <span className="caixa-tabela-auto">apenas dinheiro é contado</span>
          </div>
        </div>

        {temDiferenca ? (
          <div className="caixa-diferenca tem">
            <div className="caixa-diferenca-lab"><AlertTriangle size={18} strokeWidth={1.6} /><span>Diferença</span></div>
            <div style={{ display: 'flex', alignItems: 'baseline', gap: 14 }}>
              <span className="caixa-painel-sub">{diferenca < 0 ? 'falta na gaveta' : 'sobra na gaveta'}</span>
              <span className="caixa-diferenca-val">{diferenca < 0 ? '− ' : '+ '}{brl(Math.abs(diferenca))}</span>
            </div>
          </div>
        ) : (
          <div className="caixa-diferenca sem">
            <div className="caixa-diferenca-lab"><Check size={18} strokeWidth={1.8} /><span>Gaveta confere com o esperado</span></div>
            <span className="caixa-diferenca-val">{brl(0)}</span>
          </div>
        )}

        <div>
          <label className="caixa-field-label">Observação <span style={{ color: 'var(--text-tertiary)' }}>{temDiferenca ? 'obrigatória — há diferença' : 'opcional'}</span></label>
          <textarea
            rows={2} className="caixa-textarea" value={obs} onChange={(e) => onObs(e.target.value)}
            placeholder="Ex.: troco devolvido a paciente sem registro no sistema."
          />
        </div>

        <div className="caixa-modal-footer">
          <button type="button" className="caixa-btn-secondary" onClick={onFechar}>Cancelar</button>
          <button type="button" className="caixa-btn-primary" style={{ width: 'auto', padding: '12px 24px' }} disabled={!podeConfirmar} onClick={onConfirmar}>
            Confirmar fechamento
          </button>
        </div>
      </div>
    </>
  )
}
