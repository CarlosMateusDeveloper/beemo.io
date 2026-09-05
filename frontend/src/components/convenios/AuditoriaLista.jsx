import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { AlertTriangle, CheckCircle2, ShieldAlert } from 'lucide-react'
import { fetchAuditoriaLista, fetchAuditoriaResumo } from './api'
import { brl, statusAuditoriaMeta, STATUS_AUDITORIA } from './conveniosData'

export default function AuditoriaLista() {
  const navigate = useNavigate()
  const [status, setStatus] = useState('')
  const [linhas, setLinhas] = useState([])
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState(null)
  const [pagina, setPagina] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [resumo, setResumo] = useState(null)

  useEffect(() => { setPagina(0) }, [status])

  useEffect(() => {
    let cancelado = false
    setCarregando(true)
    fetchAuditoriaLista({ status: status || undefined, page: pagina })
      .then((dados) => {
        if (cancelado) return
        setLinhas(dados.content)
        setTotalPages(dados.totalPages)
        setTotalElements(dados.totalElements)
        setErro(null)
      })
      .catch((err) => { if (!cancelado) setErro(err.message) })
      .finally(() => { if (!cancelado) setCarregando(false) })
    return () => { cancelado = true }
  }, [status, pagina])

  useEffect(() => {
    fetchAuditoriaResumo().then(setResumo).catch(() => {})
  }, [])

  const vazio = !carregando && linhas.length === 0 && !erro

  return (
    <div className="convenios-painel">
      {resumo && (
        <div className="convenios-painel-head" style={{ gap: 24, flexWrap: 'wrap' }}>
          <span className="convenios-indicador"><ShieldAlert size={14} strokeWidth={2} /> {resumo.totalAnalisados} atendimentos analisados</span>
          <span className="convenios-indicador"><CheckCircle2 size={14} strokeWidth={2} style={{ color: 'var(--success)' }} /> {resumo.aprovados} aprovados</span>
          <span className="convenios-indicador"><AlertTriangle size={14} strokeWidth={2} style={{ color: 'var(--warning)' }} /> {resumo.atencao} em atenção</span>
          <span className="convenios-indicador"><AlertTriangle size={14} strokeWidth={2} style={{ color: 'var(--danger)' }} /> {resumo.bloqueados} bloqueados</span>
          <span className="convenios-indicador">Em risco <strong>{brl(resumo.valorEmRisco)}</strong></span>
        </div>
      )}

      <div className="convenios-painel-head">
        <div className="convenios-filtros">
          <button type="button" className={`convenios-pill ${status === '' ? 'active' : ''}`} onClick={() => setStatus('')}>Todos</button>
          {STATUS_AUDITORIA.map((s) => (
            <button
              key={s.valor} type="button" className={`convenios-pill ${status === s.valor ? 'active' : ''}`}
              onClick={() => setStatus(s.valor)}
            >
              {s.rotulo}
            </button>
          ))}
        </div>
      </div>

      {erro && <div className="convenios-modal-erro" style={{ margin: '0 16px 16px' }}>{erro}</div>}

      {vazio ? (
        <div className="convenios-vazio">
          <div className="convenios-vazio-titulo">Nenhum atendimento auditado ainda</div>
          <div className="convenios-vazio-texto">A auditoria roda automaticamente quando um atendimento de paciente com convênio é finalizado.</div>
        </div>
      ) : (
        <div className="convenios-tabela-scroll">
          <table className="convenios-tabela convenios-tabela-lista">
            <thead>
              <tr>
                <th>Paciente / procedimento</th>
                <th>Convênio</th>
                <th>Status</th>
                <th className="num">Valor em risco</th>
                <th>Avaliado em</th>
              </tr>
            </thead>
            <tbody>
              {carregando && Array.from({ length: 6 }).map((_, i) => (
                <tr key={`sk-${i}`}>
                  <td colSpan={5} style={{ padding: '14px 16px' }}>
                    <div style={{ height: 12, width: '70%', borderRadius: 6, background: 'var(--surface-muted)' }} />
                  </td>
                </tr>
              ))}
              {!carregando && linhas.map((a) => {
                const meta = statusAuditoriaMeta(a.status)
                return (
                  <tr key={a.id} onClick={() => navigate(`/convenios/auditoria/${a.id}`)}>
                    <td>
                      <div className="convenios-pac">{a.pacienteNome}</div>
                      <div className="convenios-proc">{a.procedimento}</div>
                    </td>
                    <td>{a.convenioNome ?? '—'}</td>
                    <td><span className={`convenios-badge ${meta.cls}`}>{meta.rotulo}</span></td>
                    <td className="num convenios-valor">{a.valorEmRisco > 0 ? brl(a.valorEmRisco) : '—'}</td>
                    <td>{a.avaliadoEmTxt}</td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}

      {!vazio && (
        <div className="convenios-foot">
          <span>{totalElements} {totalElements === 1 ? 'atendimento analisado' : 'atendimentos analisados'}</span>
          {totalPages > 1 && (
            <div className="convenios-filtros">
              <button type="button" className="convenios-btn-ghost sm" disabled={pagina === 0} onClick={() => setPagina((p) => p - 1)}>‹ Anterior</button>
              <span style={{ fontSize: 12.5, color: 'var(--text-tertiary)' }}>Página {pagina + 1} de {totalPages}</span>
              <button type="button" className="convenios-btn-ghost sm" disabled={pagina >= totalPages - 1} onClick={() => setPagina((p) => p + 1)}>Próxima ›</button>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
