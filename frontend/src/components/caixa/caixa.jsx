import { useEffect, useMemo, useRef, useState } from 'react'
import CaixaMovimentoDia from './CaixaMovimentoDia'
import CaixaModalFechamento from './CaixaModalFechamento'
import CaixaPainelRecebimento from './CaixaPainelRecebimento'
import CaixaReceberHoje from './CaixaReceberHoje'
import CaixaStatusTurno from './CaixaStatusTurno'
import { brl, formatNum, parseValorInput, RECEBER_INICIAL, TURNO } from './caixaData'
import './caixa.css'

const PAY_VAZIO = { valor: '', metodo: 'credito', parcela: 3, desconto: '', motivo: '' }

export function Caixa() {
  const [receber, setReceber] = useState(RECEBER_INICIAL)
  const [painel, setPainel] = useState(null) // null | 'pay' | 'close'
  const [payId, setPayId] = useState(null)
  const [pay, setPay] = useState(PAY_VAZIO)
  const [contado, setContado] = useState(formatNum(605))
  const [obs, setObs] = useState('')
  const [toast, setToast] = useState(null)
  const toastTimer = useRef(null)

  useEffect(() => () => clearTimeout(toastTimer.current), [])

  function mostrarToast(texto) {
    setToast(texto)
    clearTimeout(toastTimer.current)
    toastTimer.current = setTimeout(() => setToast(null), 2600)
  }

  const { pendentesQtd, pendentesValor } = useMemo(() => {
    const pendentes = receber.filter((r) => !r.pago)
    return { pendentesQtd: pendentes.length, pendentesValor: pendentes.reduce((acc, r) => acc + r.valor, 0) }
  }, [receber])

  const alvo = useMemo(() => receber.find((r) => r.id === payId) || null, [receber, payId])

  const total = useMemo(() => {
    if (!alvo) return 0
    const bruto = parseValorInput(pay.valor || formatNum(alvo.valor))
    const desconto = parseValorInput(pay.desconto)
    return Math.max(bruto - desconto, 0)
  }, [alvo, pay.valor, pay.desconto])

  const faltaMotivo = parseValorInput(pay.desconto) > 0 && pay.motivo.trim() === ''

  const diferenca = parseValorInput(contado) - TURNO.dinheiro
  const temDiferenca = Math.abs(diferenca) >= 0.005
  const podeConfirmarFechamento = !temDiferenca || obs.trim() !== ''

  function abrirRecebimento(row) {
    setPayId(row.id)
    setPay({ ...PAY_VAZIO, valor: formatNum(row.valor) })
    setPainel('pay')
  }

  function fecharPainel() {
    setPainel(null)
  }

  function registrar(comRecibo) {
    if (faltaMotivo || !alvo) return
    setReceber((prev) => prev.map((r) => (r.id === alvo.id ? { ...r, pago: true } : r)))
    mostrarToast(comRecibo
      ? `Recebimento de ${brl(total)} registrado e recibo enviado para impressão.`
      : `Recebimento de ${brl(total)} registrado.`)
    fecharPainel()
  }

  function confirmarFechamento() {
    if (!podeConfirmarFechamento) return
    mostrarToast(temDiferenca
      ? `Turno fechado. Diferença de ${brl(Math.abs(diferenca))} registrada.`
      : 'Turno fechado sem diferenças na gaveta.')
    fecharPainel()
  }

  return (
    <div className="caixa-page">
      <div className="caixa-head">
        <h1 className="caixa-titulo">Caixa<span className="caixa-data">{TURNO.dataLabel}</span></h1>
      </div>

      <CaixaStatusTurno onFecharCaixa={() => setPainel('close')} />

      <div className="caixa-cols">
        <CaixaReceberHoje
          linhas={receber} pendentesQtd={pendentesQtd} pendentesValor={pendentesValor}
          onReceber={abrirRecebimento}
        />
        <CaixaMovimentoDia />
      </div>

      {painel === 'pay' && (
        <CaixaPainelRecebimento
          alvo={alvo} pay={pay} total={total} faltaMotivo={faltaMotivo}
          onValor={(v) => setPay((p) => ({ ...p, valor: v }))}
          onMetodo={(m) => setPay((p) => ({ ...p, metodo: m }))}
          onParcela={(n) => setPay((p) => ({ ...p, parcela: n }))}
          onDesconto={(v) => setPay((p) => ({ ...p, desconto: v }))}
          onMotivo={(v) => setPay((p) => ({ ...p, motivo: v }))}
          onFechar={fecharPainel}
          onRegistrar={registrar}
        />
      )}

      {painel === 'close' && (
        <CaixaModalFechamento
          contado={contado} onContado={setContado}
          obs={obs} onObs={setObs}
          diferenca={diferenca} temDiferenca={temDiferenca}
          podeConfirmar={podeConfirmarFechamento}
          onFechar={fecharPainel}
          onConfirmar={confirmarFechamento}
        />
      )}

      {toast && (
        <div className="caixa-toast">
          <span className="caixa-toast-dot" />
          {toast}
        </div>
      )}
    </div>
  )
}
