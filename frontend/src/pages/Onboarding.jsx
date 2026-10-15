import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { AlertCircle, Check, CheckCircle2, FileSpreadsheet, RotateCcw, Save, Upload, Users } from 'lucide-react'
import { fetchResponsaveisPosVenda, fetchImportacoes, importarCsv, previewImportacaoCsv } from '../components/retorno/api'
import { completeOnboarding, fetchOnboarding, reopenOnboarding, rollbackImport, saveOnboarding, validateMappedCsv } from './onboardingApi'
import './Onboarding.css'

const DAYS = [[1, 'Seg'], [2, 'Ter'], [3, 'Qua'], [4, 'Qui'], [5, 'Sex'], [6, 'Sáb'], [7, 'Dom']]
const FIELDS = {
  pacienteId: 'ID do paciente', pacienteNome: 'Nome do paciente', telefone: 'Telefone', atendimentoId: 'ID do atendimento',
  dataHora: 'Data e hora', profissional: 'Profissional', especialidade: 'Especialidade', tipo: 'Procedimento', situacao: 'Situação',
}
const STATUSES = [['AGENDADO', 'Agendado'], ['CONFIRMADO', 'Confirmado'], ['FALTOU', 'Faltou'], ['REALIZADO', 'Realizado'], ['CANCELADO', 'Cancelado'], ['DESCONHECIDO', 'Revisar']]
const LABELS = { responsável: 'Responsável', agenda: 'Agenda', importação: 'Dados', canais: 'Canais', consentimento: 'Consentimento', 'primeiro fluxo': 'Primeiro fluxo' }

const empty = { responsavelId: '', fusoHorario: 'America/Fortaleza', agendaInicio: '08:00', agendaFim: '18:00', diasAtendimento: [1, 2, 3, 4, 5], canais: ['whatsapp'], politicaConsentimentoConfirmada: false, primeiroFluxo: 'faltas', versao: 0 }

export default function Onboarding() {
  const [state, setState] = useState(null)
  const [form, setForm] = useState(empty)
  const [responsibles, setResponsibles] = useState([])
  const [imports, setImports] = useState([])
  const [file, setFile] = useState(null)
  const [preview, setPreview] = useState(null)
  const [mapping, setMapping] = useState({})
  const [statuses, setStatuses] = useState({})
  const [sourceCode, setSourceCode] = useState('sistema-anterior')
  const [sourceName, setSourceName] = useState('Sistema anterior')
  const [busy, setBusy] = useState('')
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  async function load() {
    const [onboarding, users, history] = await Promise.all([fetchOnboarding(), fetchResponsaveisPosVenda(), fetchImportacoes()])
    setState(onboarding); setResponsibles(users); setImports(history)
    setForm({ ...empty, ...onboarding, responsavelId: onboarding.responsavelId || '' })
  }
  useEffect(() => {
    let active = true
    Promise.all([fetchOnboarding(), fetchResponsaveisPosVenda(), fetchImportacoes()])
      .then(([onboarding, users, history]) => {
        if (!active) return
        setState(onboarding); setResponsibles(users); setImports(history)
        setForm({ ...empty, ...onboarding, responsavelId: onboarding.responsavelId || '' })
      })
      .catch(reason => { if (active) setError(reason.message) })
    return () => { active = false }
  }, [])
  const configuration = useMemo(() => ({ fonteCodigo: sourceCode, fonteNome: sourceName, fusoHorario: form.fusoHorario, colunas: mapping, situacoes: statuses }), [sourceCode, sourceName, form.fusoHorario, mapping, statuses])

  async function save(event) {
    event.preventDefault(); setBusy('save'); setError(''); setNotice('')
    try {
      const next = await saveOnboarding({ ...form, responsavelId: Number(form.responsavelId), versao: state.versao })
      setState(next); setForm(current => ({ ...current, ...next, responsavelId: next.responsavelId || '' })); setNotice('Configuração inicial salva.')
    } catch (reason) { setError(reason.message) } finally { setBusy('') }
  }
  function toggleDay(day) { setForm(current => ({ ...current, diasAtendimento: current.diasAtendimento.includes(day) ? current.diasAtendimento.filter(item => item !== day) : [...current.diasAtendimento, day].sort() })) }
  function toggleChannel(channel) { setForm(current => ({ ...current, canais: current.canais.includes(channel) ? current.canais.filter(item => item !== channel) : [...current.canais, channel] })) }

  async function inspect() {
    if (!file) return
    setBusy('preview'); setError(''); setNotice('')
    try {
      const data = await previewImportacaoCsv(file, form.fusoHorario)
      setPreview(data); setMapping(data.mapeamento)
      setStatuses(Object.fromEntries(data.situacoesDetectadas.map(item => [item.origem, item.sugestao])))
    } catch (reason) { setError(reason.message) } finally { setBusy('') }
  }
  async function revalidate() {
    setBusy('validate'); setError('')
    try { setPreview(await validateMappedCsv(file, configuration)); setNotice('Arquivo revalidado com o mapeamento corrigido.') }
    catch (reason) { setError(reason.message) } finally { setBusy('') }
  }
  async function applyImport() {
    setBusy('import'); setError(''); setNotice('')
    try {
      const result = await importarCsv(file, configuration)
      setNotice(`${result.importacao.importadas} registro(s) importado(s), ${result.importacao.semAlteracao} já existente(s).`)
      setFile(null); setPreview(null); await load()
    } catch (reason) { setError(reason.message) } finally { setBusy('') }
  }
  async function rollback(item) {
    setBusy(`rollback-${item.id}`); setError(''); setNotice('')
    try {
      const result = await rollbackImport(item.id)
      const report = result.importacao.rollbackRelatorio ? JSON.parse(result.importacao.rollbackRelatorio) : {}
      setNotice(result.importacao.rollbackStatus === 'CONCLUIDO' ? `${report.atendimentosRemovidos || 0} atendimento(s) removido(s) com segurança.` : 'Rollback parcial concluído. Os itens alterados depois da importação foram separados para correção assistida.')
      await load()
    } catch (reason) { setError(reason.message) } finally { setBusy('') }
  }
  async function finish() {
    setBusy('finish'); setError('')
    try { const next = await completeOnboarding(); setState(next); setNotice('Implantação marcada como concluída.') }
    catch (reason) { setError(reason.message) } finally { setBusy('') }
  }
  async function reopen() {
    setBusy('reopen'); setError('')
    try { const next = await reopenOnboarding(); setState(next); setNotice('Implantação reaberta para ajustes.') }
    catch (reason) { setError(reason.message) } finally { setBusy('') }
  }

  if (!state) return <main className="onboarding-page"><p>Carregando implantação…</p>{error && <p role="alert">{error}</p>}</main>
  const complete = state.progresso === state.totalEtapas
  const readyToImport = preview && preview.linhasComErro === 0 && preview.camposObrigatorios.every(field => mapping[field]) && Object.values(statuses).every(Boolean)
  return <main className="onboarding-page">
    <header className="onboarding-header"><div><span>Configuração da clínica</span><h1>Implantação guiada</h1><p>Organize a entrada da equipe, dos dados e do primeiro fluxo sem perder a origem de cada decisão.</p></div><div className="onboarding-progress"><strong>{state.progresso}/{state.totalEtapas}</strong><span>etapas concluídas</span><i><b style={{ width: `${(state.progresso / state.totalEtapas) * 100}%` }} /></i></div></header>
    <ol className="onboarding-checklist">{Object.entries(state.checklist).map(([key, done]) => <li className={done ? 'done' : ''} key={key}><span>{done ? <Check size={14} /> : Object.keys(state.checklist).indexOf(key) + 1}</span>{LABELS[key] || key}</li>)}</ol>
    {error && <div className="onboarding-feedback error" role="alert"><AlertCircle size={18} />{error}</div>}
    {notice && <div className="onboarding-feedback success" role="status"><CheckCircle2 size={18} />{notice}</div>}

    <form className="onboarding-card onboarding-config" onSubmit={save}>
      <header><div><span>ETAPAS 1, 2, 4 E 5</span><h2>Operação inicial</h2><p>Defina quem responde pela implantação, como a agenda funciona e qual fluxo será ativado primeiro.</p></div><Link to="/conta"><Users size={16} /> Usuários e permissões</Link></header>
      <div className="onboarding-form-grid"><label><span>Responsável pela implantação</span><select required value={form.responsavelId} onChange={event => setForm({ ...form, responsavelId: event.target.value })}><option value="">Selecione</option>{responsibles.map(user => <option value={user.id} key={user.id}>{user.nome}</option>)}</select></label><label><span>Fuso horário</span><input required value={form.fusoHorario} onChange={event => setForm({ ...form, fusoHorario: event.target.value })} /></label><label><span>Início da agenda</span><input type="time" required value={form.agendaInicio || ''} onChange={event => setForm({ ...form, agendaInicio: event.target.value })} /></label><label><span>Fim da agenda</span><input type="time" required value={form.agendaFim || ''} onChange={event => setForm({ ...form, agendaFim: event.target.value })} /></label></div>
      <fieldset><legend>Dias de atendimento</legend><div className="onboarding-options">{DAYS.map(([day, label]) => <label key={day}><input type="checkbox" checked={form.diasAtendimento.includes(day)} onChange={() => toggleDay(day)} /><span>{label}</span></label>)}</div></fieldset>
      <div className="onboarding-form-grid"><fieldset><legend>Canais preparados</legend><div className="onboarding-options">{[['whatsapp', 'WhatsApp'], ['email', 'E-mail'], ['telefone', 'Telefone']].map(([value, label]) => <label key={value}><input type="checkbox" checked={form.canais.includes(value)} onChange={() => toggleChannel(value)} /><span>{label}</span></label>)}</div></fieldset><label><span>Primeiro fluxo guiado</span><select value={form.primeiroFluxo} onChange={event => setForm({ ...form, primeiroFluxo: event.target.value })}><option value="faltas">Recuperação de faltas</option><option value="pos_atendimento">Pós-atendimento</option><option value="retorno_preventivo">Retorno preventivo</option></select></label></div>
      <label className="onboarding-consent"><input type="checkbox" checked={form.politicaConsentimentoConfirmada} onChange={event => setForm({ ...form, politicaConsentimentoConfirmada: event.target.checked })} /><span><strong>Política de consentimento revisada</strong>Pacientes sem evidência permanecem bloqueados para campanhas até a regularização.</span></label>
      <footer><button className="onboarding-primary" disabled={busy === 'save'}><Save size={16} />{busy === 'save' ? 'Salvando…' : 'Salvar configuração'}</button></footer>
    </form>

    <section className="onboarding-card onboarding-import"><header><div><span>ETAPA 3</span><h2>Migração de dados</h2><p>Pacientes, profissionais, agenda, procedimentos e histórico mínimo entram pelo mesmo contrato auditável.</p></div><FileSpreadsheet size={28} /></header>
      <div className="onboarding-source"><label><span>Arquivo CSV</span><input type="file" accept=".csv,text/csv" onChange={event => { setFile(event.target.files?.[0] || null); setPreview(null) }} /></label><label><span>Código da origem</span><input value={sourceCode} onChange={event => setSourceCode(event.target.value.toLowerCase().replace(/[^a-z0-9_-]/g, ''))} /></label><label><span>Nome da origem</span><input value={sourceName} onChange={event => setSourceName(event.target.value)} /></label><button type="button" className="onboarding-secondary" disabled={!file || Boolean(busy)} onClick={inspect}><Upload size={16} />{busy === 'preview' ? 'Validando…' : 'Validar sem importar'}</button></div>
      {preview && <><div className="onboarding-import-summary"><span><strong>{preview.totalLinhas}</strong> linhas</span><span><strong>{preview.linhasValidas}</strong> válidas</span><span className={preview.linhasComErro ? 'danger' : ''}><strong>{preview.linhasComErro}</strong> pendências</span><span>O original será criptografado e vinculado ao SHA-256.</span></div>
        <div className="onboarding-mapping"><div><h3>Mapeamento</h3><p>Corrija e revalide sem sair da implantação.</p></div>{Object.entries(FIELDS).map(([field, label]) => <label key={field}><span>{label}</span><select value={mapping[field] || ''} onChange={event => setMapping(current => ({ ...current, [field]: event.target.value }))}><option value="">Não mapear</option>{preview.colunas.map(column => <option key={column}>{column}</option>)}</select></label>)}</div>
        <div className="onboarding-statuses"><h3>Situações encontradas</h3>{preview.situacoesDetectadas.map(item => <label key={item.origem}><span>{item.origem}<small>{item.quantidade} linha(s)</small></span><select value={statuses[item.origem] || ''} onChange={event => setStatuses(current => ({ ...current, [item.origem]: event.target.value }))}><option value="">Selecione</option>{STATUSES.map(([value, label]) => <option value={value} key={value}>{label}</option>)}</select></label>)}</div>
        {preview.amostra.some(row => !row.valida) && <div className="onboarding-errors"><strong>Pendências explicadas</strong>{preview.amostra.filter(row => !row.valida).map((row, index) => <span key={`${row.atendimentoId}-${index}`}>Linha da amostra {index + 1}: {row.mensagem} <em>{row.campo}</em></span>)}</div>}
        <div className="onboarding-import-actions"><button type="button" className="onboarding-secondary" onClick={revalidate} disabled={Boolean(busy)}>Revalidar correções</button><button type="button" className="onboarding-primary" onClick={applyImport} disabled={!readyToImport || Boolean(busy)}>{busy === 'import' ? 'Importando…' : 'Importar base validada'}</button></div></>}
      <div className="onboarding-history"><h3>Histórico e rollback</h3>{imports.length === 0 ? <p>Nenhuma importação concluída.</p> : imports.slice(0, 5).map(item => <article key={item.id}><div><strong>{item.arquivo}</strong><span>{item.fonte} · {item.responsavel}</span><small>{item.importadas} novas · {item.atualizadas} atualizadas · {item.revisao + item.erros} pendências{item.arquivoProtegido ? ' · original protegido' : ''}</small></div>{item.rollbackStatus ? <span className="onboarding-rollback-status">{item.rollbackStatus === 'CONCLUIDO' ? 'Desfeita' : 'Correção assistida'}</span> : <button type="button" onClick={() => rollback(item)} disabled={Boolean(busy)}><RotateCcw size={14} />{busy === `rollback-${item.id}` ? 'Desfazendo…' : 'Desfazer com segurança'}</button>}</article>)}</div>
    </section>
    <section className="onboarding-finish"><div><CheckCircle2 size={24} /><div><h2>{state.status === 'CONCLUIDO' ? 'Implantação concluída' : complete ? 'Pronto para começar' : 'Conclua o checklist'}</h2><p>{state.status === 'CONCLUIDO' ? 'A configuração permanece auditável e pode ser reaberta para ajustes.' : 'O Clinisis só conclui quando responsável, agenda, dados, canais, consentimento e fluxo estão definidos.'}</p></div></div>{state.status === 'CONCLUIDO' ? <button onClick={reopen} disabled={Boolean(busy)}>Reabrir implantação</button> : <button className="onboarding-primary" onClick={finish} disabled={!complete || Boolean(busy)}>Concluir implantação</button>}</section>
  </main>
}
