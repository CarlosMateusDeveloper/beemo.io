import { useEffect, useMemo, useRef, useState } from 'react'
import CaixaMovimentoDia from './CaixaMovimentoDia'
import CaixaModalFechamento from './CaixaModalFechamento'
import CaixaPainelRecebimento from './CaixaPainelRecebimento'
import CaixaReceberHoje from './CaixaReceberHoje'
import CaixaStatusTurno from './CaixaStatusTurno'
import { brl, formatNum, parseValorInput } from './caixaData'
import { fetchTurnoAtual, registrarPagamento, fecharTurno } from './api'
import './caixa.css'

const PAY_VAZIO = { valor: '', metodo: 'credito', parcela: 3, desconto: '', motivo: '' }

export function Caixa() {
  const [dados, setDados] = useState(null)
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState(null)
  const [painel, setPainel] = useState(null) // null | 'pay' | 'close'
  const [payId, setPayId] = useState(null)
  const [pay, setPay] = useState(PAY_VAZIO)
  const [contado, setContado] = useState('0,00')
  const [obs, setObs] = useState('')
  const [toast, setToast] = useState(null)
  const toastTimer = useRef(null)

  useEffect(() => () => clearTimeout(toastTimer.current), [])

  function mostrarToast(texto) {
    setToast(texto)
    clearTimeout(toastTimer.current)
    toastTimer.current = setTimeout(() => setToast(null), 2600)
  }

  function recarregar() {
    return fetchTurnoAtual().then((resp) => {
      setDados(resp)
      setContado(formatNum(resp.turno.dinheiro))
      return resp
    })
  }

  useEffect(() => {
    setCarregando(true)
    setErro(null)
    recarregar()
      .catch((err) => setErro(err.message))
      .finally(() => setCarregando(false))
  }, [])

  const receber = useMemo(() => dados?.receber || [], [dados])
  const movimento = dados?.movimento || []
  const turno = dados?.turno

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

  const diferenca = turno ? parseValorInput(contado) - turno.dinheiro : 0
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
    registrarPagamento({
      idFatura: alvo.id,
      valor: parseValorInput(pay.valor || formatNum(alvo.valor)),
      metodo: pay.metodo,
      parcelas: pay.metodo === 'credito' ? pay.parcela : null,
      desconto: parseValorInput(pay.desconto),
      motivoDesconto: pay.motivo.trim() || null,
    }).then(() => {
      mostrarToast(comRecibo
        ? `Recebimento de ${brl(total)} registrado e recibo enviado para impressão.`
        : `Recebimento de ${brl(total)} registrado.`)
      fecharPainel()
      return recarregar()
    }).catch((err) => mostrarToast(`Não foi possível registrar (${err.message}).`))
  }

  function confirmarFechamento() {
    if (!podeConfirmarFechamento) return
    fecharTurno({
      dinheiroContado: parseValorInput(contado),
      observacao: obs.trim() || null,
    }).then((resp) => {
      mostrarToast(resp.temDiferenca
        ? `Turno fechado. Diferença de ${brl(Math.abs(resp.diferenca))} registrada.`
        : 'Turno fechado sem diferenças na gaveta.')
      fecharPainel()
      setObs('')
      return recarregar()
    }).catch((err) => mostrarToast(`Não foi possível fechar o turno (${err.message}).`))
  }

  if (carregando) {
    return (
      <div className="caixa-page">
        <div className="caixa-head"><h1 className="caixa-titulo">Caixa</h1></div>
        <div className="dashboard-card-skel" style={{ height: 120 }} />
      </div>
    )
  }

  if (erro || !turno) {
    return (
      <div className="caixa-page">
        <div className="caixa-head"><h1 className="caixa-titulo">Caixa</h1></div>
        <div className="dashboard-erro">Não foi possível carregar o caixa ({erro || 'sem dados'}).</div>
      </div>
    )
  }

  return (
    <div className="caixa-page">
      <div className="caixa-head">
        <h1 className="caixa-titulo">Caixa<span className="caixa-data">{turno.dataLabel}</span></h1>
      </div>

      <CaixaStatusTurno turno={turno} onFecharCaixa={() => setPainel('close')} />

      <div className="caixa-cols">
        <CaixaReceberHoje
          linhas={receber} pendentesQtd={pendentesQtd} pendentesValor={pendentesValor}
          onReceber={abrirRecebimento}
        />
        <CaixaMovimentoDia linhas={movimento} />
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
          turno={turno}
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
