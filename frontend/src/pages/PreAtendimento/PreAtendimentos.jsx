import { useEffect, useState } from 'react'
import { CheckCircle2, ClipboardList, ExternalLink, FileText, Link2, RefreshCw } from 'lucide-react'
import { baixarAnexoPreAtendimento, criarLinkPreAtendimento, listarPreAtendimentos, obterPreAtendimento, revisarPreAtendimento } from './api'
import './preAtendimento.css'

export default function PreAtendimentos() {
  const [items, setItems] = useState([])
  const [selected, setSelected] = useState(null)
  const [consultation, setConsultation] = useState('')
  const [generated, setGenerated] = useState(null)
  const [pending, setPending] = useState('')
  const [error, setError] = useState('')

  async function load() { try { setItems(await listarPreAtendimentos()); setError('') } catch (err) { setError(err.message) } }
  useEffect(() => {
    let active = true
    listarPreAtendimentos().then(result => { if (active) setItems(result) }).catch(err => { if (active) setError(err.message) })
    return () => { active = false }
  }, [])
  async function open(id) { try { setSelected(await obterPreAtendimento(id)); setPending('') } catch (err) { setError(err.message) } }
  async function create(event) {
    event.preventDefault(); try { setGenerated(await criarLinkPreAtendimento(consultation, { validadeHoras: 48, canal: 'whatsapp' })); await load() } catch (err) { setError(err.message) }
  }
  async function review(status) {
    try {
      const camposPendentes = pending.split(',').map(value => value.trim()).filter(Boolean)
      const camposAprovados = Object.keys(selected.dadosPropostos || {}).filter(key => !camposPendentes.includes(key))
      const result = await revisarPreAtendimento(selected.id, { status, camposAprovados, camposPendentes })
      setSelected(result); await load()
    } catch (err) { setError(err.message) }
  }
  async function download(file) {
    try {
      const blob = await baixarAnexoPreAtendimento(selected.id, file.id)
      const url = URL.createObjectURL(blob); const link = document.createElement('a')
      link.href = url; link.download = file.nome; link.click(); URL.revokeObjectURL(url)
    } catch (err) { setError(err.message) }
  }

  return <div className="pre-staff-page">
    <header><div><p className="pre-eyebrow">Atendimento</p><h1>Pré-atendimentos</h1><p>Envie o acesso e revise os dados antes da consulta.</p></div><button className="pre-secondary" onClick={load}><RefreshCw size={16} />Atualizar</button></header>
    {error && <div className="pre-alert error">{error}</div>}
    <section className="pre-create"><form onSubmit={create}><label>ID da consulta<input type="number" min="1" required value={consultation} onChange={e => setConsultation(e.target.value)} placeholder="Ex.: 128" /></label><button className="pre-primary"><Link2 size={17} />Criar link seguro</button></form>
      {generated && <div className="pre-created"><span>Link válido por {generated.expiraEmHoras} horas</span><div><button onClick={() => navigator.clipboard.writeText(generated.url)}>Copiar link</button><a href={generated.whatsappUrl} target="_blank" rel="noreferrer">Abrir WhatsApp <ExternalLink size={14} /></a></div></div>}
    </section>
    <div className="pre-staff-grid">
      <section className="pre-queue"><div className="pre-section-title"><ClipboardList size={18} /><h2>Fila de revisão</h2><span>{items.filter(item => ['enviado','pendente'].includes(item.status)).length}</span></div>
        {items.length === 0 ? <div className="pre-empty">Nenhum pré-atendimento iniciado.</div> : items.map(item => <button key={item.id} className={`pre-row ${selected?.id === item.id ? 'active' : ''}`} onClick={() => open(item.id)}><div><strong>{item.paciente}</strong><small>Consulta #{item.consultaId} · {item.anexos} anexo(s)</small></div><span className={`pre-status ${item.status}`}>{item.status}</span></button>)}
      </section>
      <section className="pre-review">
        {!selected ? <div className="pre-empty">Selecione um pré-atendimento para revisar.</div> : <>
          <div className="pre-section-title"><h2>{selected.paciente}</h2><span className={`pre-status ${selected.status}`}>{selected.status}</span></div>
          <h3>Alterações cadastrais</h3><dl>{Object.entries(selected.dadosPropostos || {}).map(([key,value]) => <div key={key}><dt>{key}</dt><dd>{String(value || '—')}</dd></div>)}</dl>
          <h3>Respostas</h3><dl>{Object.entries(selected.respostas || {}).map(([key,value]) => <div key={key}><dt>{key}</dt><dd>{String(value || '—')}</dd></div>)}</dl>
          <h3>Documentos</h3>{selected.anexos?.length ? selected.anexos.map(file => <button className="pre-file" key={file.id} onClick={() => download(file)}><FileText size={16} />{file.nome}</button>) : <p className="pre-muted">Nenhum documento.</p>}
          {['enviado','pendente'].includes(selected.status) && <div className="pre-review-actions"><label>Campos pendentes (separe por vírgula)<input value={pending} onChange={e => setPending(e.target.value)} placeholder="Ex.: cpf, alergias" /></label><button className="pre-secondary" onClick={() => review('pendente')}>Solicitar correção</button><button className="pre-primary" onClick={() => review('aprovado')}><CheckCircle2 size={17} />Aprovar</button></div>}
        </>}
      </section>
    </div>
  </div>
}
