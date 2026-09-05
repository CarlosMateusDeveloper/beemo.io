import { useEffect, useState } from 'react'
import { Plus, AlertTriangle } from 'lucide-react'
import LoteWizard from './LoteWizard'
import { fetchLotes } from './api'
import { brl, statusLoteMeta } from './conveniosData'

export default function LotesLista({ onAbrirLote }) {
  const [modo, setModo] = useState('lista') // 'lista' | 'wizard'
  const [lotes, setLotes] = useState([])
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState(null)
  const [sinal, setSinal] = useState(0)

  useEffect(() => {
    if (modo !== 'lista') return
    setCarregando(true)
    fetchLotes()
      .then((pagina) => { setLotes(pagina.content); setErro(null) })
      .catch((err) => setErro(err.message))
      .finally(() => setCarregando(false))
  }, [modo, sinal])

  function aoCriarLote(lote) {
    setModo('lista')
    setSinal((n) => n + 1)
    onAbrirLote(lote.id)
  }

  if (modo === 'wizard') {
    return <LoteWizard onCancelar={() => setModo('lista')} onCriado={aoCriarLote} />
  }

  return (
    <div className="convenios-painel">
      <div className="convenios-painel-head">
        <div className="convenios-painel-titulo">Lotes de faturamento <span>({lotes.length})</span></div>
        <button type="button" className="convenios-btn-primario" onClick={() => setModo('wizard')}>
          <Plus size={15} strokeWidth={2} />Novo lote
        </button>
      </div>

      {erro && <div className="convenios-modal-erro" style={{ margin: '0 16px 16px' }}>{erro}</div>}

      {!carregando && lotes.length === 0 && !erro ? (
        <div className="convenios-vazio">
          <div className="convenios-vazio-titulo">Nenhum lote criado ainda</div>
          <div className="convenios-vazio-texto">Clique em "Novo lote" pra deixar o ClinicOS sugerir o agrupamento dos atendimentos pendentes por convênio.</div>
        </div>
      ) : carregando ? (
        <div className="convenios-vazio">Carregando…</div>
      ) : (
        <div className="convenios-tabela-scroll">
          <table className="convenios-tabela convenios-tabela-lista">
            <thead>
              <tr>
                <th>Código</th>
                <th>Convênio</th>
                <th>Status</th>
                <th className="num">Itens</th>
                <th className="num">Valor enviado</th>
                <th className="num">Valor pago</th>
                <th>Data de envio</th>
              </tr>
            </thead>
            <tbody>
              {lotes.map((l) => (
                <tr key={l.id} onClick={() => onAbrirLote(l.id)}>
                  <td className="convenios-mono">{l.codigo}</td>
                  <td>{l.convenioNome}</td>
                  <td>
                    <span className={`convenios-badge ${statusLoteMeta(l.status).cls}`}>{statusLoteMeta(l.status).rotulo}</span>
                    {l.divergencia && (
                      <span title="Pagamento a menor sem glosa formal registrada" style={{ marginLeft: 6, color: 'var(--warning)', verticalAlign: 'middle' }}>
                        <AlertTriangle size={13} strokeWidth={2} />
                      </span>
                    )}
                  </td>
                  <td className="num">{l.quantidadeItens}</td>
                  <td className="num convenios-valor">{brl(l.valorTotal)}</td>
                  <td className="num convenios-valor">{brl(l.valorPago)}</td>
                  <td>{l.dataEnvio}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
