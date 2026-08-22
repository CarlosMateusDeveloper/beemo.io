import { Banknote, CreditCard, QrCode } from 'lucide-react'
import { brl, TURNO } from './caixaData'

export default function CaixaStatusTurno({ onFecharCaixa }) {
  return (
    <section className="caixa-status">
      <div className="caixa-status-left">
        <div className="caixa-status-live">
          <span className="caixa-status-dot" />
          <span>Caixa aberto desde {TURNO.horaAbertura} · {TURNO.operador.split(' ')[0]}</span>
        </div>
        <div className="caixa-status-total">{brl(TURNO.totalHoje)}</div>
        <div className="caixa-status-sub">{TURNO.recebimentosHoje} recebimentos hoje</div>
      </div>

      <div className="caixa-formas">
        <div className="caixa-forma-dinheiro">
          <div className="caixa-forma-lab"><Banknote size={15} strokeWidth={1.6} /><span>Dinheiro</span></div>
          <div className="caixa-forma-val">{brl(TURNO.dinheiro)}</div>
          <div className="caixa-forma-sub">contado na gaveta</div>
        </div>

        <div className="caixa-formas-outras">
          <div className="caixa-forma-simples">
            <div className="caixa-forma-lab"><CreditCard size={15} strokeWidth={1.6} /><span>Cartão</span></div>
            <div className="caixa-forma-val">{brl(TURNO.cartao)}</div>
            <div className="caixa-forma-sub">liquidação automática</div>
          </div>
          <div className="caixa-forma-simples">
            <div className="caixa-forma-lab"><QrCode size={15} strokeWidth={1.6} /><span>Pix</span></div>
            <div className="caixa-forma-val">{brl(TURNO.pix)}</div>
            <div className="caixa-forma-sub">liquidação automática</div>
          </div>
        </div>

        <button type="button" className="caixa-btn-fechar" onClick={onFecharCaixa}>Fechar caixa</button>
      </div>
    </section>
  )
}
