import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ArrowLeft, Check, X } from 'lucide-react'
import { fetchAuditoriaDetalhe } from './api'
import { brl, severidadeMeta, statusAuditoriaMeta } from './conveniosData'
import './convenios.css'

export default function AuditoriaDetalhePagina() {
  const { id } = useParams()
  const navigate = useNavigate()

  const [auditoria, setAuditoria] = useState(null)
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState(null)

  useEffect(() => {
    let cancelado = false
    setCarregando(true)
    fetchAuditoriaDetalhe(id)
      .then((d) => { if (!cancelado) { setAuditoria(d); setErro(null) } })
      .catch((err) => { if (!cancelado) setErro(err.message) })
      .finally(() => { if (!cancelado) setCarregando(false) })
    return () => { cancelado = true }
  }, [id])

  if (erro) {
    return (
      <div className="convenios-page">
        <button type="button" className="convenios-voltar" onClick={() => navigate('/convenios?aba=auditoria')}>
          <ArrowLeft size={15} strokeWidth={2} />Voltar para auditoria
        </button>
        <div className="convenios-vazio"><div className="convenios-vazio-titulo">Não foi possível abrir esta auditoria</div><div className="convenios-vazio-texto">{erro}</div></div>
      </div>
    )
  }

  if (carregando || !auditoria) {
    return <div className="convenios-page"><div className="convenios-vazio">Carregando…</div></div>
  }

  const meta = statusAuditoriaMeta(auditoria.status)

  return (
    <div className="convenios-page">
      <button type="button" className="convenios-voltar" onClick={() => navigate('/convenios?aba=auditoria')}>
        <ArrowLeft size={15} strokeWidth={2} />Voltar para auditoria
      </button>

      <div className="convenios-glosa-header">
        <div>
          <div className="convenios-glosa-header-nome">{auditoria.atendimento?.pacienteNome}{auditoria.atendimento?.procedimento ? ` — ${auditoria.atendimento.procedimento}` : ''}</div>
          <div className="convenios-glosa-header-meta">{auditoria.convenioNome} · atendimento em {auditoria.atendimento?.dataTxt} · {auditoria.atendimento?.profissionalNome}</div>
        </div>
        <div className="convenios-glosa-header-valor">
          <span className={`convenios-badge ${meta.cls}`}>{meta.rotulo}</span>
          {auditoria.valorEmRisco > 0 && <div className="convenios-valor" style={{ fontSize: 20, marginTop: 6 }}>{brl(auditoria.valorEmRisco)}</div>}
        </div>
      </div>

      <div className="convenios-sub">
        <div className="convenios-sub-head"><h3>Auditoria — Atendimento #{auditoria.id}</h3></div>
        {auditoria.itens.length === 0 ? (
          <div className="convenios-sub-vazio">
            <strong>Nenhuma regra avaliável para este atendimento</strong>
            <span>Ou não há regras cadastradas para o convênio, ou nenhuma delas se aplica com o dado disponível hoje.</span>
          </div>
        ) : (
          auditoria.itens.map((item, i) => (
            <div key={i} className="convenios-checklist-item" style={{ alignItems: 'flex-start', padding: '8px 0' }}>
              {item.status === 'ok'
                ? <Check size={15} strokeWidth={2.4} style={{ color: 'var(--success)', marginTop: 2 }} />
                : <X size={15} strokeWidth={2.4} style={{ color: 'var(--danger)', marginTop: 2 }} />}
              <div>
                <div>{item.descricao}</div>
                {item.status === 'falha' && (
                  <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginTop: 4 }}>
                    {item.severidade && <span className={`convenios-badge ${severidadeMeta(item.severidade).cls}`}>{severidadeMeta(item.severidade).rotulo}</span>}
                    {item.acaoRecomendada && <span className="convenios-proc">{item.acaoRecomendada}</span>}
                  </div>
                )}
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  )
}
