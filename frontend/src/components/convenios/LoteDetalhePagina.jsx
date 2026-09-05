import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ArrowLeft, AlertTriangle } from 'lucide-react'
import { fetchLote, atualizarStatusLote } from './api'
import { brl, statusLoteMeta, TRANSICOES_LOTE } from './conveniosData'
import './convenios.css'

export default function LoteDetalhePagina() {
  const { id } = useParams()
  const navigate = useNavigate()

  const [lote, setLote] = useState(null)
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState(null)
  const [mudandoStatus, setMudandoStatus] = useState(false)

  function carregar() {
    setCarregando(true)
    fetchLote(id)
      .then((d) => { setLote(d); setErro(null) })
      .catch((err) => setErro(err.message))
      .finally(() => setCarregando(false))
  }

  useEffect(carregar, [id])

  async function mudarStatus(novoStatus) {
    setMudandoStatus(true)
    try {
      await atualizarStatusLote(id, novoStatus)
      carregar()
    } catch (err) {
      setErro(err.message)
    } finally {
      setMudandoStatus(false)
    }
  }

  if (carregando) {
    return <div className="convenios-page"><div className="convenios-vazio">Carregando…</div></div>
  }

  if (erro && !lote) {
    return (
      <div className="convenios-page">
        <button type="button" className="convenios-voltar" onClick={() => navigate('/convenios?aba=lotes')}>
          <ArrowLeft size={15} strokeWidth={2} />Voltar para lotes
        </button>
        <div className="convenios-vazio"><div className="convenios-vazio-titulo">Não foi possível abrir este lote</div><div className="convenios-vazio-texto">{erro}</div></div>
      </div>
    )
  }

  const statusMeta = statusLoteMeta(lote.status)
  const acoesDisponiveis = TRANSICOES_LOTE[lote.status] ?? []
  const divergente = lote.valorDivergente > 0 && ['processando', 'pago', 'pago_parcial', 'com_glosas'].includes(lote.status)

  return (
    <div className="convenios-page">
      <button type="button" className="convenios-voltar" onClick={() => navigate('/convenios?aba=lotes')}>
        <ArrowLeft size={15} strokeWidth={2} />Voltar para lotes
      </button>

      <div className="convenios-glosa-header">
        <div>
          <div className="convenios-glosa-header-nome">{lote.codigo} — {lote.convenioNome}</div>
          <div className="convenios-glosa-header-meta">
            criado em {lote.criadoEm} · envio: {lote.dataEnvio}
          </div>
        </div>
        <div className="convenios-glosa-header-valor">
          <span className={`convenios-badge ${statusMeta.cls}`}>{statusMeta.rotulo}</span>
        </div>
      </div>

      {erro && <div className="convenios-modal-erro">{erro}</div>}

      <div className="convenios-sub">
        <div className="convenios-sub-form-grid">
          <div><div className="convenios-label">Valor enviado</div><div className="convenios-valor">{brl(lote.valorTotal)}</div></div>
          <div><div className="convenios-label">Valor pago</div><div className="convenios-valor" style={{ color: 'var(--success)' }}>{brl(lote.valorPago)}</div></div>
          <div><div className="convenios-label">Valor glosado</div><div className="convenios-valor">{brl(lote.valorGlosado)}</div></div>
        </div>

        {divergente && (
          <div className="convenios-sub-vazio" style={{ marginTop: 12, background: 'var(--warning-bg)', borderRadius: 8, padding: 10 }}>
            <strong style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
              <AlertTriangle size={15} strokeWidth={2} style={{ color: 'var(--warning)' }} />
              Divergência de {brl(lote.valorDivergente)}
            </strong>
            <span>O valor pago + glosado não fecha com o valor enviado — pagamento a menor sem glosa formal registrada. Vale conferir com o convênio.</span>
          </div>
        )}

        {acoesDisponiveis.length > 0 && (
          <div className="convenios-modal-footer">
            {acoesDisponiveis.map((acao) => (
              <button
                key={acao.valor} type="button"
                className={acao.valor === 'rascunho' ? 'convenios-btn-ghost' : 'convenios-btn-primario'}
                disabled={mudandoStatus} onClick={() => mudarStatus(acao.valor)}
              >
                {acao.rotulo}
              </button>
            ))}
          </div>
        )}
      </div>

      <div className="convenios-sub">
        <div className="convenios-sub-head"><h3>Atendimentos do lote ({lote.itens.length})</h3></div>
        <div className="convenios-tabela-scroll">
          <table className="convenios-tabela convenios-tabela-sub">
            <thead>
              <tr>
                <th>Paciente</th>
                <th>Data</th>
                <th>Tipo</th>
                <th className="num">Valor</th>
                <th className="num">Pago</th>
                <th className="num">Glosado</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {lote.itens.map((item) => (
                <tr key={item.idFatura}>
                  <td>{item.pacienteNome}</td>
                  <td>{item.dataAtendimento}</td>
                  <td>{item.tipo}</td>
                  <td className="num convenios-valor">{brl(item.valor)}</td>
                  <td className="num convenios-valor">{brl(item.valorPago)}</td>
                  <td className="num convenios-valor">{brl(item.valorGlosado)}</td>
                  <td>{item.statusFatura}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}
