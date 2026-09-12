import { useEffect, useState } from 'react'
import { AlertTriangle } from 'lucide-react'
import { fetchCaixaDre } from './api'
import { brl, PERIODOS, categoriaDespesaLabel } from './caixaData'
import './caixa.css'

export function CaixaDre() {
  const [periodo, setPeriodo] = useState('Últimos 30 dias')
  const [dre, setDre] = useState(null)
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState(null)

  useEffect(() => {
    let cancelado = false
    setCarregando(true)
    fetchCaixaDre(periodo)
      .then((d) => { if (!cancelado) { setDre(d); setErro(null) } })
      .catch((err) => { if (!cancelado) setErro(err.message) })
      .finally(() => { if (!cancelado) setCarregando(false) })
    return () => { cancelado = true }
  }, [periodo])

  return (
    <div className="caixa-page">
      <div className="caixa-head" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 16, flexWrap: 'wrap' }}>
        <h1 className="caixa-titulo">DRE</h1>
        <select className="caixa-input-sm" style={{ width: 'auto' }} value={periodo} onChange={(e) => setPeriodo(e.target.value)}>
          {PERIODOS.map((p) => <option key={p.valor} value={p.valor}>{p.rotulo}</option>)}
        </select>
      </div>

      {carregando && <div className="dashboard-card-skel" style={{ height: 200 }} />}
      {erro && <div className="dashboard-erro">Não foi possível carregar o DRE ({erro}).</div>}

      {!carregando && dre && (
        <>
          <div className="caixa-aviso" style={{ background: 'var(--warning-bg)', padding: '12px 16px', borderRadius: 10, marginTop: 0, marginBottom: 20 }}>
            <AlertTriangle size={16} strokeWidth={2} />
            <span>{dre.avisoDespesas}</span>
          </div>

          <div className="caixa-painel" style={{ marginBottom: 14 }}>
            <div className="caixa-painel-head"><div className="caixa-painel-titulo"><h2>Resultado do período</h2></div></div>
            <div style={{ padding: '4px 18px 18px' }}>
              <LinhaDre label="Receita bruta — convênio" valor={dre.receitaBrutaConvenio} />
              <LinhaDre label="Receita bruta — particular" valor={dre.receitaBrutaParticular} />
              <LinhaDre label="Receita bruta total" valor={dre.receitaBruta} forte />
              <LinhaDre label="(–) Deduções (cancelamentos/estornos)" valor={dre.deducoes} negativo />
              <LinhaDre label="= Receita líquida" valor={dre.receitaLiquida} forte separador />
              <LinhaDre label="(–) Repasse a médicos" valor={dre.totalRepasses} negativo />
              <LinhaDre label="(–) Despesas" valor={dre.totalDespesas} negativo />
              <LinhaDre label="= Resultado" valor={dre.resultado} forte destaque separador />
            </div>
          </div>

          {dre.despesasPorCategoria.length > 0 && (
            <div className="caixa-painel" style={{ marginBottom: 14 }}>
              <div className="caixa-painel-head"><div className="caixa-painel-titulo"><h2>Despesas por categoria</h2></div></div>
              <div className="caixa-tabela" style={{ border: 'none', borderRadius: 0 }}>
                <div className="caixa-tabela-head" style={{ gridTemplateColumns: '1fr 160px' }}>
                  <span>Categoria</span><span style={{ textAlign: 'right' }}>Valor</span>
                </div>
                {dre.despesasPorCategoria.map((d) => (
                  <div key={d.categoria} className="caixa-tabela-row" style={{ gridTemplateColumns: '1fr 160px' }}>
                    <span>{categoriaDespesaLabel(d.categoria)}</span>
                    <span className="caixa-tabela-esperado">{brl(d.valor)}</span>
                  </div>
                ))}
              </div>
            </div>
          )}

          {dre.repasses.length > 0 && (
            <div className="caixa-painel" style={{ marginBottom: 14 }}>
              <div className="caixa-painel-head"><div className="caixa-painel-titulo"><h2>Repasse por médico</h2></div></div>
              <div className="caixa-tabela" style={{ border: 'none', borderRadius: 0 }}>
                <div className="caixa-tabela-head" style={{ gridTemplateColumns: '1fr 140px 100px 140px' }}>
                  <span>Médico</span><span style={{ textAlign: 'right' }}>Receita</span><span style={{ textAlign: 'right' }}>%</span><span style={{ textAlign: 'right' }}>Repasse</span>
                </div>
                {dre.repasses.map((r) => (
                  <div key={r.medicoNome} className="caixa-tabela-row" style={{ gridTemplateColumns: '1fr 140px 100px 140px' }}>
                    <span>{r.medicoNome}</span>
                    <span className="caixa-tabela-esperado">{brl(r.receitaBruta)}</span>
                    <span className="caixa-tabela-esperado">{r.percentual}%</span>
                    <span className="caixa-tabela-esperado">{brl(r.valorRepasse)}</span>
                  </div>
                ))}
              </div>
            </div>
          )}

          {dre.medicosSemRepasseConfigurado.length > 0 && (
            <div className="caixa-aviso" style={{ background: 'var(--surface-muted)', padding: '10px 16px', borderRadius: 10 }}>
              <AlertTriangle size={14} strokeWidth={2} />
              <span>Sem repasse configurado (não entram no total): {dre.medicosSemRepasseConfigurado.join(', ')}</span>
            </div>
          )}
        </>
      )}
    </div>
  )
}

function LinhaDre({ label, valor, forte, negativo, destaque, separador }) {
  return (
    <div
      style={{
        display: 'flex', justifyContent: 'space-between', padding: '10px 0',
        borderTop: separador ? '1px solid var(--border)' : 'none',
        fontWeight: forte ? 600 : 400,
        fontSize: forte ? 15 : 13.5,
        color: destaque ? 'var(--acc-deep)' : 'var(--text-primary)',
      }}
    >
      <span style={{ color: forte ? 'inherit' : 'var(--text-secondary)' }}>{label}</span>
      <span className="caixa-mono">{negativo ? `– ${brl(valor)}` : brl(valor)}</span>
    </div>
  )
}
