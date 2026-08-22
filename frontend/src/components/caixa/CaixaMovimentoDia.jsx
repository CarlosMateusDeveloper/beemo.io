import { Banknote, CreditCard, QrCode } from 'lucide-react'
import { brl, MOVIMENTO } from './caixaData'

const ICONE_METODO = { dinheiro: Banknote, debito: CreditCard, credito: CreditCard, pix: QrCode }

export default function CaixaMovimentoDia() {
  return (
    <section className="caixa-painel">
      <div className="caixa-painel-head">
        <div className="caixa-painel-titulo">
          <h2>Movimento do dia</h2>
          <span className="caixa-painel-sub">entradas e saídas</span>
        </div>
        <span className="caixa-painel-meta">últimos {MOVIMENTO.length} de 26 lançamentos</span>
      </div>

      <div>
        {MOVIMENTO.map((m) => {
          const Icone = ICONE_METODO[m.metodo]
          return (
            <div key={m.id} className="caixa-row">
              <span className="caixa-row-hora">{m.hora}</span>
              <div className="caixa-row-info">
                <span className="caixa-row-nome">{m.desc}</span>
                <span className="caixa-row-sub">{m.sub}</span>
              </div>
              <div className="caixa-mov-metodo">
                <Icone size={15} strokeWidth={1.6} />
                <span>{m.metodoLabel}</span>
              </div>
              <div className="caixa-mov-valor-wrap">
                <span className={`caixa-mov-valor ${m.tipo}`}>{m.tipo === 'in' ? '+ ' : '− '}{brl(m.valor)}</span>
              </div>
              <span className="caixa-mov-id">{m.id}</span>
              <span className="caixa-mov-quem">{m.quem}</span>
            </div>
          )
        })}
      </div>
    </section>
  )
}
