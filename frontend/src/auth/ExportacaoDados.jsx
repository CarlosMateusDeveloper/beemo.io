import { useCallback, useEffect, useState } from 'react'
import { apiDownload, apiRequest } from '../lib/apiClient'

const rotulo = { PENDENTE: 'Na fila', PROCESSANDO: 'Gerando', PRONTA: 'Pronta', FALHA: 'Falhou', REVOGADA: 'Revogada', EXPIRADA: 'Expirada' }

export default function ExportacaoDados() {
  const [jobs, setJobs] = useState([])
  const [tokens, setTokens] = useState({})
  const [erro, setErro] = useState('')
  const [busy, setBusy] = useState(false)
  const carregar = useCallback(() => apiRequest('/api/exportacoes').then(setJobs).catch(e => setErro(e.message)), [])
  useEffect(() => { carregar(); const timer = setInterval(carregar, 8000); return () => clearInterval(timer) }, [carregar])
  async function solicitar() {
    setBusy(true); setErro('')
    try {
      const job = await apiRequest('/api/exportacoes', { method: 'POST' })
      setTokens(current => ({ ...current, [job.id]: job.urlDownload }))
      await carregar()
    } catch (e) { setErro(e.message) } finally { setBusy(false) }
  }
  async function baixar(job) {
    const url = tokens[job.id]
    if (!url) { setErro('Por segurança, o link temporário só é exibido na sessão que solicitou a exportação. Solicite uma nova exportação.'); return }
    try {
      const blob = await apiDownload(url)
      const link = document.createElement('a'); link.href = URL.createObjectURL(blob); link.download = `exportacao-clinica-${job.id}.zip`; link.click(); URL.revokeObjectURL(link.href)
    } catch (e) { setErro(e.message) }
  }
  async function revogar(id) { try { await apiRequest('/api/exportacoes/' + id, { method: 'DELETE' }); await carregar() } catch (e) { setErro(e.message) } }
  return <section className="access-audit exportacoes">
    <h2>Exportar dados da clínica</h2>
    <p>Gera um arquivo ZIP cifrado, com os dados isolados desta clínica. O link expira em 24 horas e cada solicitação fica registrada na auditoria.</p>
    {erro && <p role="alert">{erro}</p>}
    <button type="button" disabled={busy} onClick={solicitar}>{busy ? 'Solicitando…' : 'Solicitar exportação'}</button>
    {jobs.length > 0 && <div className="access-audit-table-wrap"><table><thead><tr><th>Solicitada em</th><th>Status</th><th>Progresso</th><th>Ação</th></tr></thead><tbody>
      {jobs.map(job => <tr key={job.id}><td>{new Date(job.criado_em || job.criadoEm).toLocaleString('pt-BR')}</td><td>{rotulo[job.status] || job.status}</td><td>{job.progresso}%</td><td>
        {job.status === 'PRONTA' && <button type="button" onClick={() => baixar(job)}>Baixar</button>}
        {['PENDENTE', 'PROCESSANDO', 'PRONTA'].includes(job.status) && <button type="button" className="exportacao-revogar" onClick={() => revogar(job.id)}>Revogar</button>}
      </td></tr>)}
    </tbody></table></div>}
  </section>
}
