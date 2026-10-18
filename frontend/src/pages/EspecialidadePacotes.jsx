import { useEffect, useMemo, useState } from 'react'
import { AlertCircle, Check, CheckCircle2, Clock3, FileCheck2, Layers3, RotateCcw, ShieldCheck, Stethoscope, Workflow } from 'lucide-react'
import {
  aplicarPacoteEspecialidade, criarVersaoPacoteEspecialidade, desativarPacoteEspecialidade, fetchPacotesEspecialidade,
  enviarVersaoParaRevisao, fetchRevisoresEspecialidade, previewPacoteEspecialidade, publicarPacoteEspecialidade,
  reverterPacoteEspecialidade,
} from './especialidadePacotesApi'
import './EspecialidadePacotes.css'

const TYPE_ICONS = { MODELO_ATENDIMENTO: Clock3, DOCUMENTO: FileCheck2, FORMULARIO: Layers3, FLUXO_ACOMPANHAMENTO: Workflow }
const TYPE_LABELS = { MODELO_ATENDIMENTO: 'Modelo de atendimento', DOCUMENTO: 'Documento', FORMULARIO: 'Formulário', FLUXO_ACOMPANHAMENTO: 'Fluxo de acompanhamento' }
const ACTION_LABELS = { CRIAR: 'Novo', ATUALIZAR: 'Atualizar', SEM_ALTERACAO: 'Já aplicado', PRESERVAR_PERSONALIZACAO: 'Ajuste local preservado' }

export default function EspecialidadePacotes() {
  const [packages, setPackages] = useState([])
  const [reviewers, setReviewers] = useState([])
  const [activeId, setActiveId] = useState(null)
  const [selected, setSelected] = useState([])
  const [preview, setPreview] = useState(null)
  const [review, setReview] = useState({ revisorId: '', registroProfissional: '' })
  const [approved, setApproved] = useState(false)
  const [policy, setPolicy] = useState(false)
  const [busy, setBusy] = useState('')
  const [feedback, setFeedback] = useState(null)

  const active = useMemo(() => packages.find((item) => item.id === activeId) || packages[0], [packages, activeId])

  async function load(preferredId) {
    const [items, doctors] = await Promise.all([fetchPacotesEspecialidade(), fetchRevisoresEspecialidade()])
    setPackages(items); setReviewers(doctors)
    const next = items.find((item) => item.id === (preferredId || activeId)) || items[0]
    setActiveId(next?.id || null)
    setSelected(next?.componentesSelecionados?.length ? next.componentesSelecionados : (next?.componentes || []).map((component) => component.chave))
    setPreview(null)
  }

  useEffect(() => {
    Promise.resolve().then(() => load()).catch((error) => setFeedback({ type: 'error', text: error.message }))
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  function choose(item) {
    setActiveId(item.id); setSelected(item.componentesSelecionados?.length ? item.componentesSelecionados : item.componentes.map((component) => component.chave))
    setPreview(null); setApproved(false); setPolicy(false); setFeedback(null)
  }
  function toggle(key) { setSelected((current) => current.includes(key) ? current.filter((item) => item !== key) : [...current, key]); setPreview(null) }
  async function inspect() {
    setBusy('preview'); setFeedback(null)
    try { setPreview(await previewPacoteEspecialidade(active.versaoId, selected)) }
    catch (error) { setFeedback({ type: 'error', text: error.message }) }
    finally { setBusy('') }
  }
  async function publish() {
    setBusy('publish'); setFeedback(null)
    try {
      await publicarPacoteEspecialidade(active.versaoId, { revisorId: Number(review.revisorId), registroProfissional: review.registroProfissional })
      await load(active.id); setFeedback({ type: 'success', text: 'Versão revisada e publicada para a clínica.' })
    } catch (error) { setFeedback({ type: 'error', text: error.message }) } finally { setBusy('') }
  }
  async function apply() {
    setBusy('apply'); setFeedback(null)
    try {
      await aplicarPacoteEspecialidade(active.versaoId, { componentes: selected, aprovadoPelaClinica: approved, politicaComunicacaoAprovada: policy, versaoLock: active.versaoLock })
      await load(active.id); setFeedback({ type: 'success', text: 'Configuração aplicada sem duplicar componentes e com as personalizações preservadas.' })
    } catch (error) { setFeedback({ type: 'error', text: error.message }) } finally { setBusy('') }
  }
  async function createVersion() {
    setBusy('version'); setFeedback(null)
    try {
      await criarVersaoPacoteEspecialidade(active.id, active.componentes)
      await load(active.id); setFeedback({ type: 'success', text: 'Nova versão criada para revisão. Edite os componentes pela API antes da aprovação quando precisar mudar o conteúdo.' })
    } catch (error) { setFeedback({ type: 'error', text: error.message }) } finally { setBusy('') }
  }
  async function submitReview() {
    setBusy('review'); setFeedback(null)
    try {
      await enviarVersaoParaRevisao(active.versaoId)
      await load(active.id); setFeedback({ type: 'success', text: 'Versão enviada para revisão profissional.' })
    } catch (error) { setFeedback({ type: 'error', text: error.message }) } finally { setBusy('') }
  }
  async function status(action) {
    setBusy(action); setFeedback(null)
    try {
      if (action === 'deactivate') await desativarPacoteEspecialidade(active.aplicacaoId, active.versaoLock)
      else await reverterPacoteEspecialidade(active.aplicacaoId, active.versaoLock)
      await load(active.id); setFeedback({ type: 'success', text: action === 'deactivate' ? 'Configuração desativada. O histórico foi preservado.' : 'Aplicação revertida sem apagar o histórico.' })
    } catch (error) { setFeedback({ type: 'error', text: error.message }) } finally { setBusy('') }
  }

  if (!active) return <main className="specialty-page"><p>Carregando pacotes por especialidade…</p>{feedback && <p role="alert">{feedback.text}</p>}</main>
  const hasFlow = active.componentes.some((item) => selected.includes(item.chave) && item.tipo === 'FLUXO_ACOMPANHAMENTO')

  return <main className="specialty-page">
    <header className="specialty-header"><div><span>CONFIGURAÇÃO DA CLÍNICA</span><h1>Pacotes por especialidade</h1><p>Revise, compare e aplique somente o que faz sentido para sua operação. Atualizações nunca apagam ajustes locais ou registros históricos.</p></div><div className="specialty-trust"><ShieldCheck size={20} /><span><strong>Aplicação controlada</strong>Revisão profissional e aprovação da clínica</span></div></header>
    {feedback && <div className={`specialty-feedback ${feedback.type}`} role={feedback.type === 'error' ? 'alert' : 'status'}>{feedback.type === 'error' ? <AlertCircle size={17} /> : <CheckCircle2 size={17} />}{feedback.text}</div>}
    <div className="specialty-layout">
      <aside className="specialty-catalog"><h2>Pacotes disponíveis</h2>{packages.map((item) => <button type="button" className={item.id === active.id ? 'active' : ''} key={item.id} onClick={() => choose(item)}><span><Stethoscope size={17} />{item.especialidade}</span><strong>{item.nome}</strong><small>Versão {item.versao} · {item.status === 'PUBLICADA' ? 'revisada' : item.status === 'RASCUNHO' ? 'rascunho' : 'aguardando revisão'}</small></button>)}</aside>
      <section className="specialty-workspace">
        <div className="specialty-title"><div><span>{active.especialidade}</span><h2>{active.nome}</h2><p>{active.descricao}</p></div><div className="specialty-title-actions"><span className={`specialty-status ${active.status.toLowerCase()}`}>{active.status === 'PUBLICADA' ? <Check size={13} /> : <Clock3 size={13} />}{active.status === 'PUBLICADA' ? 'Publicada' : active.status === 'RASCUNHO' ? 'Rascunho' : 'Em revisão'}</span>{active.status === 'PUBLICADA' && <button type="button" onClick={createVersion} disabled={busy === 'version'}>{busy === 'version' ? 'Criando…' : 'Criar nova versão'}</button>}{active.status === 'RASCUNHO' && <button type="button" onClick={submitReview} disabled={busy === 'review'}>{busy === 'review' ? 'Enviando…' : 'Enviar para revisão'}</button>}</div></div>
        <div className="specialty-meta"><span>Versão <strong>{active.versao}</strong></span><span>Aplicação <strong>{active.aplicacaoStatus || 'Ainda não aplicada'}</strong></span><span>Ajustes locais <strong>{active.ajustesLocais}</strong></span>{active.revisor && <span>Revisão <strong>{active.revisor} · {active.registroRevisor}</strong></span>}</div>
        <div className="specialty-components"><header><div><h3>Selecione os componentes</h3><p>A prévia mostra exatamente o que será criado, atualizado ou preservado.</p></div><button type="button" onClick={inspect} disabled={!selected.length || busy === 'preview'}>{busy === 'preview' ? 'Comparando…' : 'Comparar alterações'}</button></header>
          {active.componentes.map((item) => { const Icon = TYPE_ICONS[item.tipo] || Layers3; const change = preview?.itens?.find((entry) => entry.chave === item.chave); return <label className={selected.includes(item.chave) ? 'selected' : ''} key={item.chave}><input type="checkbox" checked={selected.includes(item.chave)} onChange={() => toggle(item.chave)} /><span className="specialty-component-icon"><Icon size={18} /></span><span><strong>{item.nome}</strong><small>{TYPE_LABELS[item.tipo]}{item.duracaoMinutos ? ` · ${item.duracaoMinutos} min` : ''}</small></span>{change && <em className={change.acao.toLowerCase()}>{ACTION_LABELS[change.acao]}</em>}</label> })}
        </div>
        {preview?.avisos?.length > 0 && <div className="specialty-warnings">{preview.avisos.map((warning) => <span key={warning}><AlertCircle size={14} />{warning}</span>)}</div>}
        {active.status !== 'PUBLICADA' ? <div className="specialty-review"><div><h3>{active.status === 'RASCUNHO' ? 'Versão em preparação' : 'Revisão profissional'}</h3><p>{active.status === 'RASCUNHO' ? 'Atualize os componentes pela API e envie a versão para revisão quando estiver pronta.' : 'A versão só poderá ser aplicada depois de aprovada por um médico ativo da clínica.'}</p></div>{active.status === 'EM_REVISAO' && <><label><span>Profissional revisor</span><select value={review.revisorId} onChange={(event) => setReview({ ...review, revisorId: event.target.value })}><option value="">Selecione</option>{reviewers.map((doctor) => <option key={doctor.id} value={doctor.id}>{doctor.nome}</option>)}</select></label><label><span>Conselho e registro</span><input placeholder="Ex.: CRM-CE 12345" value={review.registroProfissional} onChange={(event) => setReview({ ...review, registroProfissional: event.target.value })} /></label><button type="button" onClick={publish} disabled={!review.revisorId || !review.registroProfissional.trim() || busy === 'publish'}>{busy === 'publish' ? 'Publicando…' : 'Aprovar e publicar'}</button></>}</div>
          : <div className="specialty-approval"><label><input type="checkbox" checked={approved} onChange={(event) => setApproved(event.target.checked)} /><span><strong>Aprovação da clínica</strong>Revisei os componentes e autorizo a aplicação desta versão.</span></label>{hasFlow && <label><input type="checkbox" checked={policy} onChange={(event) => setPolicy(event.target.checked)} /><span><strong>Política de comunicação</strong>Confirmo que o fluxo seguirá consentimento, canal permitido e opt-out.</span></label>}<div><button className="specialty-primary" type="button" onClick={apply} disabled={!approved || (hasFlow && !policy) || !selected.length || preview?.avisos?.length > 0 || busy === 'apply'}>{busy === 'apply' ? 'Aplicando…' : active.aplicacaoId ? 'Aplicar seleção e atualização' : 'Aplicar pacote'}</button>{active.aplicacaoId && active.aplicacaoStatus === 'ATIVA' && <button type="button" onClick={() => status('deactivate')} disabled={Boolean(busy)}>Desativar</button>}{active.aplicacaoId && <button type="button" onClick={() => status('revert')} disabled={Boolean(busy)}><RotateCcw size={14} />Reverter aplicação</button>}</div></div>}
      </section>
    </div>
  </main>
}
