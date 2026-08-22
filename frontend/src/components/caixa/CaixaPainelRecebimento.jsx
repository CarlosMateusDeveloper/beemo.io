import { AlertTriangle, Banknote, CreditCard, Printer, QrCode, X } from 'lucide-react'
import { brl, METODOS, formatNum } from './caixaData'

const ICONE_METODO = { dinheiro: Banknote, debito: CreditCard, credito: CreditCard, pix: QrCode }

export default function CaixaPainelRecebimento({
  alvo, pay, total, faltaMotivo,
  onValor, onMetodo, onParcela, onDesconto, onMotivo, onFechar, onRegistrar,
}) {
  if (!alvo) return null

  const linha = `${alvo.hora} · ${alvo.procedimento} · ${alvo.convenio ? `${alvo.convenio} (coparticipação)` : 'Particular'}`

  return (
    <>
      <div className="caixa-overlay" onClick={onFechar} />
      <aside className="caixa-side">
        <div className="caixa-side-head">
          <div>
            <div className="caixa-side-kicker">Receber</div>
            <div className="caixa-side-nome">{alvo.paciente}</div>
            <div className="caixa-side-linha">{linha}</div>
          </div>
          <button type="button" className="caixa-icon-btn" onClick={onFechar} aria-label="Fechar">
            <X size={15} strokeWidth={1.8} />
          </button>
        </div>

        <div>
          <label className="caixa-field-label">Valor</label>
          <div className="caixa-valor-box">
            <span className="caixa-valor-prefix">R$</span>
            <input type="text" className="caixa-valor-input" value={pay.valor} onChange={(e) => onValor(e.target.value)} />
          </div>
        </div>

        <div>
          <label className="caixa-field-label">Forma de pagamento</label>
          <div className="caixa-metodos">
            {METODOS.map((m) => {
              const Icone = ICONE_METODO[m.key]
              return (
                <button
                  key={m.key} type="button"
                  className={`caixa-metodo-btn ${pay.metodo === m.key ? 'sel' : ''}`}
                  onClick={() => onMetodo(m.key)}
                >
                  <Icone size={17} strokeWidth={1.6} />{m.label}
                </button>
              )
            })}
          </div>
        </div>

        {pay.metodo === 'credito' && (
          <div>
            <label className="caixa-field-label">Parcelas</label>
            <div className="caixa-parcelas">
              {[1, 2, 3, 4, 5, 6].map((n) => (
                <button
                  key={n} type="button"
                  className={`caixa-parcela-btn ${pay.parcela === n ? 'sel' : ''}`}
                  onClick={() => onParcela(n)}
                >
                  <span className="caixa-parcela-n">{n}x</span>
                  <span className="caixa-parcela-each">{formatNum(total / n)}</span>
                </button>
              ))}
            </div>
          </div>
        )}

        <div className="caixa-grid2">
          <div>
            <label className="caixa-field-label">Desconto</label>
            <input type="text" className="caixa-input-sm" placeholder="0,00" value={pay.desconto} onChange={(e) => onDesconto(e.target.value)} />
          </div>
          <div>
            <label className="caixa-field-label">Motivo do desconto <span style={{ color: 'var(--text-tertiary)' }}>obrigatório</span></label>
            <input type="text" className="caixa-input-sm" placeholder="Ex.: retorno em cortesia" value={pay.motivo} onChange={(e) => onMotivo(e.target.value)} />
          </div>
        </div>
        {faltaMotivo && (
          <div className="caixa-aviso">
            <AlertTriangle size={15} strokeWidth={1.7} />
            Informe o motivo para registrar o desconto.
          </div>
        )}

        <div className="caixa-total-row">
          <span className="caixa-painel-sub">Total a receber</span>
          <span className="caixa-total-val">{brl(total)}</span>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 9 }}>
          <button type="button" className="caixa-btn-primary" disabled={faltaMotivo} onClick={() => onRegistrar(false)}>Registrar</button>
          <button type="button" className="caixa-btn-secondary" disabled={faltaMotivo} onClick={() => onRegistrar(true)}>
            <Printer size={16} strokeWidth={1.6} />Registrar e imprimir recibo
          </button>
        </div>
      </aside>
    </>
  )
}
