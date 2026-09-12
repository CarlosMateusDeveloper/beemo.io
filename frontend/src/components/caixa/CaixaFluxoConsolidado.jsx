import { useEffect, useState } from 'react'
import { fetchCaixaFluxoConsolidado } from './api'
import { brl, PERIODOS } from './caixaData'
import './caixa.css'

export function CaixaFluxoConsolidado() {
  const [periodo, setPeriodo] = useState('Últimos 30 dias')
  const [fluxo, setFluxo] = useState(null)
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState(null)

  useEffect(() => {
    let cancelado = false
    setCarregando(true)
    fetchCaixaFluxoConsolidado(periodo)
      .then((d) => { if (!cancelado) { setFluxo(d); setErro(null) } })
      .catch((err) => { if (!cancelado) setErro(err.message) })
      .finally(() => { if (!cancelado) setCarregando(false) })
    return () => { cancelado = true }
  }, [periodo])

  const vazio = !carregando && fluxo && fluxo.serie.length === 0

  return (
    <div className="caixa-page caixa-financeiro">
      <div className="caixa-head" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 16, flexWrap: 'wrap' }}>
        <h1 className="caixa-titulo">Fluxo de caixa consolidado</h1>
        <select className="caixa-input-sm" style={{ width: 'auto' }} value={periodo} onChange={(e) => setPeriodo(e.target.value)}>
          {PERIODOS.map((p) => <option key={p.valor} value={p.valor}>{p.rotulo}</option>)}
        </select>
      </div>

      {carregando && <div className="dashboard-card-skel" style={{ height: 200 }} />}
      {erro && <div className="dashboard-erro">Não foi possível carregar o fluxo de caixa ({erro}).</div>}

      {!carregando && fluxo && (
        <>
          <div className="caixa-resumos caixa-resumos-tres">
            <div className="caixa-painel" style={{ padding: '16px 18px' }}>
              <div style={{ fontSize: 13, color: 'var(--text-secondary)', marginBottom: 8 }}>Entradas no período</div>
              <div className="caixa-mono caixa-resumo-valor" style={{ color: 'var(--success)' }}>{brl(fluxo.totalEntradas)}</div>
              <div style={{ fontSize: 12, color: 'var(--text-tertiary)', marginTop: 4 }}>{fluxo.diasComMovimento} dias com movimento</div>
            </div>
            <div className="caixa-painel" style={{ padding: '16px 18px' }}>
              <div style={{ fontSize: 13, color: 'var(--text-secondary)', marginBottom: 8 }}>Saídas no período</div>
              <div className="caixa-mono caixa-resumo-valor" style={{ color: 'var(--danger)' }}>{brl(fluxo.totalSaidas)}</div>
              <div style={{ fontSize: 12, color: 'var(--text-tertiary)', marginTop: 4 }}>despesas pagas (Caixa → Despesas)</div>
            </div>
            <div className="caixa-painel" style={{ padding: '16px 18px' }}>
              <div style={{ fontSize: 13, color: 'var(--text-secondary)', marginBottom: 8 }}>Saldo do período</div>
              <div className="caixa-mono caixa-resumo-valor" style={{ }}>{brl(fluxo.saldoPeriodo)}</div>
              <div style={{ fontSize: 12, color: 'var(--text-tertiary)', marginTop: 4 }}>média de {brl(fluxo.ticketMedioDiario)}/dia em entradas</div>
            </div>
          </div>

          <div className="caixa-painel">
            <div className="caixa-painel-head"><div className="caixa-painel-titulo"><h2>Por dia</h2></div></div>
            {vazio ? (
              <div className="caixa-vazio">
                <div className="caixa-vazio-titulo">Nenhum movimento no período</div>
                <div className="caixa-vazio-texto">Assim que houver pagamentos recebidos ou despesas pagas, eles aparecem aqui.</div>
              </div>
            ) : (
              <div className="caixa-tabela" style={{ border: 'none', borderRadius: 0 }}>
                <div className="caixa-tabela-head" style={{ gridTemplateColumns: '1fr 140px 140px 160px' }}>
                  <span>Data</span><span style={{ textAlign: 'right' }}>Entradas</span><span style={{ textAlign: 'right' }}>Saídas</span><span style={{ textAlign: 'right' }}>Saldo acumulado</span>
                </div>
                {[...fluxo.serie].reverse().map((p) => (
                  <div key={p.dataTxt} className="caixa-tabela-row" style={{ gridTemplateColumns: '1fr 140px 140px 160px' }}>
                    <span>{p.dataTxt}</span>
                    <span className="caixa-tabela-esperado" style={{ color: 'var(--success)' }}>{p.entradas > 0 ? brl(p.entradas) : '—'}</span>
                    <span className="caixa-tabela-esperado" style={{ color: 'var(--danger)' }}>{p.saidas > 0 ? brl(p.saidas) : '—'}</span>
                    <span className="caixa-tabela-esperado" style={{ fontWeight: 600 }}>{brl(p.saldoAcumulado)}</span>
                  </div>
                ))}
              </div>
            )}
          </div>
        </>
      )}
    </div>
  )
}
