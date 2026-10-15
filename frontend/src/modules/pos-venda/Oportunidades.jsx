import { useCallback, useEffect, useState } from 'react'
import {
  AlertCircle, AlertTriangle, CalendarCheck2, CheckCircle2, Clock3, Database, FileSpreadsheet, History,
  MessageCircle, Phone, RefreshCw, Search, Target, Upload, UserPlus, X,
} from 'lucide-react'
import {
  fetchImportacoes, fetchOportunidade, fetchOportunidades, fetchResponsaveisPosVenda,
  importarCsv, previewImportacaoCsv, salvarAcaoOportunidade,
} from '../../components/retorno/api'
import './Oportunidades.css'

const STATUS_LABELS = {
  pendente: 'Pendente', em_contato: 'Em contato', sem_resposta: 'Sem resposta',
  reagendado: 'Reagendado', nova_falta: 'Nova falta', recuperado: 'Recuperado',
  nao_deseja: 'Não deseja', nao_contatar: 'Não contatar', registro_corrigido: 'Registro corrigido',
}
const FIELD_LABELS = {
  pacienteId: 'ID externo do paciente', pacienteNome: 'Nome do paciente', telefone: 'Telefone',
  atendimentoId: 'ID externo do atendimento', dataHora: 'Data e hora', profissional: 'Profissional',
  especialidade: 'Especialidade', tipo: 'Tipo/procedimento', situacao: 'Situação', atualizadoEm: 'Atualizado em (opcional)',
}
const SITUACOES = [
  ['AGENDADO', 'Agendado'], ['CONFIRMADO', 'Confirmado'], ['FALTOU', 'Faltou'],
  ['REALIZADO', 'Realizado/compareceu'], ['CANCELADO', 'Cancelado'],
  ['DESCONHECIDO', 'Não representa situação clínica'],
]
const CLOSED = ['recuperado', 'nao_deseja', 'nao_contatar', 'registro_corrigido']
const QUEUES = [
  ['prioridades', 'Prioridades'], ['organizar', 'Organizar'], ['primeira_tentativa', 'Primeira tentativa'],
  ['nova_tentativa', 'Nova tentativa'], ['respostas', 'Respostas'], ['conferir_agenda', 'Conferir presença'],
  ['todos', 'Todos os casos'],
]

export default function Oportunidades() {
  const [data, setData] = useState(null)
  const [imports, setImports] = useState([])
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState('abertos')
  const [queue, setQueue] = useState('prioridades')
  const [responsible, setResponsible] = useState('')
  const [responsibles, setResponsibles] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [selected, setSelected] = useState(null)
  const [showImport, setShowImport] = useState(false)
  const [showHistory, setShowHistory] = useState(false)

  const load = useCallback(async () => {
    setLoading(true); setError('')
    try {
      const [cases, history] = await Promise.all([
        fetchOportunidades({ status, busca: search, fila: queue, responsavel: responsible }), fetchImportacoes(),
      ])
      setData(cases); setImports(history)
    } catch (requestError) { setError(requestError.message) }
    finally { setLoading(false) }
  }, [queue, responsible, search, status])

  useEffect(() => {
    const timeout = window.setTimeout(load, 250)
    return () => window.clearTimeout(timeout)
  }, [load])
  useEffect(() => {
    let active = true
    fetchResponsaveisPosVenda().then((items) => { if (active) setResponsibles(items) }).catch(() => {})
    return () => { active = false }
  }, [])

  const summary = data?.resumo || {}
  const items = data?.itens || []
  const lastImport = imports[0]

  return <main className="opportunities-page" aria-labelledby="opportunities-title">
    <div className="opportunities-wrap">
      <header className="opportunities-heading">
        <div><p className="op-eyebrow">Pós-atendimento e retenção</p><h1 id="opportunities-title">Oportunidades</h1><span className="op-subtitle">Faltas reais da agenda interna e das fontes importadas, sem duplicar casos.</span></div>
        <div className="op-heading-actions">
          <button type="button" className="op-secondary-button" onClick={() => setShowHistory((current) => !current)}><History size={15} /> Histórico</button>
          <button type="button" className="op-primary-button" onClick={() => setShowImport(true)}><Upload size={15} /> Importar CSV</button>
        </div>
      </header>

      {lastImport && <div className="op-source-strip"><Database size={15} /><span><strong>{lastImport.fonte}</strong> atualizada em {formatDateTime(lastImport.ultimaSincronizacao)}</span><span className={`op-import-status ${lastImport.status.toLowerCase()}`}>{importStatus(lastImport.status)}</span></div>}
      {showHistory && <ImportHistory items={imports} />}

      <section className="op-kpis op-daily-kpis" aria-label="Prioridades da fila diária">
        <Kpi label="Organizar" value={summary.pendencias_organizacao ?? '—'} note="Sem responsável ou próximo passo" tone="warning" icon={<UserPlus size={17} />} active={queue === 'organizar'} onClick={() => setQueue('organizar')} />
        <Kpi label="Primeira tentativa" value={summary.primeira_tentativa ?? '—'} note="Pacientes ainda não contatados" icon={<Phone size={17} />} active={queue === 'primeira_tentativa'} onClick={() => setQueue('primeira_tentativa')} />
        <Kpi label="Nova tentativa" value={summary.nova_tentativa ?? '—'} note="Prazo de contato vencido" tone="warning" icon={<Clock3 size={17} />} active={queue === 'nova_tentativa'} onClick={() => setQueue('nova_tentativa')} />
        <Kpi label="Respostas" value={summary.respostas_pendentes ?? '—'} note="Paciente aguarda a equipe" tone="teal" icon={<MessageCircle size={17} />} active={queue === 'respostas'} onClick={() => setQueue('respostas')} />
        <Kpi label="Conferir presença" value={summary.conferir_presenca ?? '—'} note="Consulta reagendada já ocorreu" icon={<CalendarCheck2 size={17} />} active={queue === 'conferir_agenda'} onClick={() => setQueue('conferir_agenda')} />
      </section>

      <section className="op-card op-list" aria-label="Lista de oportunidades">
        <nav className="op-queue-tabs" aria-label="Etapas da fila diária">{QUEUES.map(([value, label]) => <button type="button" key={value} className={queue === value ? 'active' : ''} onClick={() => { setQueue(value); setSelected(null) }}>{label}</button>)}</nav>
        <div className="op-filter-bar">
          <label className="op-search"><Search size={16} aria-hidden="true" /><span className="op-sr-only">Buscar</span><input type="search" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Buscar paciente ou telefone" /></label>
          <label className="op-select"><span className="op-sr-only">Filtrar por status</span><select value={status} onChange={(event) => setStatus(event.target.value)}><option value="abertos">Casos abertos</option><option value="todos">Todos os casos</option>{Object.entries(STATUS_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
          <label className="op-select"><span className="op-sr-only">Filtrar por responsável</span><select value={responsible} onChange={(event) => setResponsible(event.target.value)}><option value="">Toda a equipe</option>{responsibles.map((item) => <option value={item.id} key={item.id}>{item.nome}</option>)}</select></label>
          <button type="button" className="op-icon-button" onClick={load} aria-label="Atualizar lista"><RefreshCw size={15} /></button>
        </div>

        {error && <div className="op-feedback error" role="alert"><AlertCircle size={18} /><div><strong>Não foi possível carregar as oportunidades</strong><span>{error}</span></div><button type="button" onClick={load}>Tentar novamente</button></div>}
        {loading && <div className="op-loading"><span className="op-spinner" /> Carregando oportunidades…</div>}
        {!loading && !error && <div className="op-table-wrap">
          <table className="op-table"><thead><tr><th>Paciente / origem</th><th>Atendimento</th><th>Etapa da fila</th><th>Última interação</th><th>Responsável / próximo passo</th></tr></thead><tbody>{items.map((item) => <tr key={item.id} onClick={() => setSelected(item.id)} className={selected === item.id ? 'selected' : ''}>
            <td><div className="op-patient"><span className="op-avatar">{initials(item.paciente)}</span><div><button type="button" className="op-patient-name">{item.paciente}</button><span>{formatPhone(item.telefone)}</span><small>{item.origem === 'importacao_csv' ? `CSV · ${item.fonte || 'fonte externa'}` : 'Agenda Clinisis'}</small></div></div></td>
            <td><strong>{formatDateTime(item.data_falta)}</strong><span className="op-cell-sub">{item.tipo || 'Atendimento'} · {item.profissional}</span><span className="op-cell-sub">{item.especialidade}</span></td>
            <td><QueueBadge item={item} /><span className="op-cell-sub"><StatusBadge status={item.status} /></span></td>
            <td><strong>{item.ultima_interacao ? formatDateTime(item.ultima_interacao) : 'Nenhuma interação'}</strong><span className="op-cell-sub">{item.ultima_interacao_descricao || `${item.tentativas || 0} tentativa(s)`}</span></td>
            <td><strong className={item.alerta_sem_responsavel ? 'op-overdue' : ''}>{item.responsavel || 'Sem responsável'}</strong><span className={`op-cell-sub ${item.alerta_prazo_vencido || item.alerta_sem_proxima_acao ? 'op-overdue' : ''}`}>{nextStep(item)}</span></td>
          </tr>)}</tbody></table>
          {items.length === 0 && <div className="op-empty"><Target size={26} /><strong>Nenhuma oportunidade encontrada</strong><span>Importe o CSV da agenda da clínica ou ajuste os filtros.</span><button type="button" className="op-primary-button" onClick={() => setShowImport(true)}><Upload size={15} /> Importar CSV</button></div>}
        </div>}
        <footer className="op-list-footer"><span>{data?.total ?? 0} caso(s) encontrado(s)</span><span>Até 50 casos por página</span></footer>
      </section>

      {selected && <CaseDetail id={selected} responsibles={responsibles} onClose={() => setSelected(null)} onChanged={load} />}
    </div>
    {showImport && <ImportCsvModal onClose={() => setShowImport(false)} onImported={() => { setShowImport(false); load() }} />}
  </main>
}

function QueueBadge({ item }) {
  const labels = { resposta_pendente: 'Responder paciente', revisar_recorrencia: 'Revisar recorrência', conferir_presenca: 'Conferir presença', aguardando_consulta: 'Aguardar consulta', primeira_tentativa: 'Primeira tentativa', nova_tentativa: 'Nova tentativa' }
  return <span className={`op-queue-badge ${item.etapa_fila}`}>{labels[item.etapa_fila] || 'Acompanhar'}</span>
}

function nextStep(item) {
  if (item.alerta_resposta_pendente) return 'Responder paciente agora'
  if (item.alerta_revisar_recorrencia) return 'Revisar nova falta recorrente'
  if (item.alerta_sem_responsavel) return 'Definir responsável'
  if (item.alerta_sem_proxima_acao) return 'Definir próxima ação'
  if (item.alerta_conferir_presenca) return 'Conferir resultado da consulta'
  if (item.status === 'reagendado' && item.reagendado_em) return `Atendimento externo: ${formatDateTime(item.reagendado_em)}`
  if (item.status === 'reagendado') return `Consulta: ${formatDateTime(`${item.data_reagendada}T${item.hora_reagendada}`)}`
  return item.proxima_acao ? formatDateTime(item.proxima_acao) : 'Aguardando organização'
}

function CaseDetail({ id, responsibles, onClose, onChanged }) {
  const [details, setDetails] = useState(null)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [busy, setBusy] = useState(false)
  const loadDetail = useCallback(async () => {
    try { setDetails(await fetchOportunidade(id)); setError('') } catch (reason) { setError(reason.message) }
  }, [id])
  useEffect(() => {
    let active = true
    fetchOportunidade(id)
      .then((result) => { if (active) { setDetails(result); setError('') } })
      .catch((reason) => { if (active) setError(reason.message) })
    return () => { active = false }
  }, [id])
  const current = details?.caso

  async function save(event) {
    event.preventDefault(); setBusy(true); setError(''); setNotice('')
    const form = new FormData(event.currentTarget)
    const action = form.get('acao')
    const payload = {
      versao: current.versao, acao: action,
      idResponsavel: form.get('idResponsavel') ? Number(form.get('idResponsavel')) : null,
      proximaAcao: form.get('proximaAcao') ? new Date(form.get('proximaAcao')).toISOString() : null,
      motivoFalta: form.get('motivoFalta')?.trim() || null,
      recorrente: form.get('recorrente') === 'on', canal: form.get('canal') || null,
      resultado: form.get('resultado') || null, observacao: form.get('observacao')?.trim() || null,
      idConsulta: form.get('idConsulta') ? Number(form.get('idConsulta')) : null,
      idAtendimentoExterno: form.get('idAtendimentoExterno') ? Number(form.get('idAtendimentoExterno')) : null,
    }
    try { await salvarAcaoOportunidade(id, payload); setNotice('Acompanhamento atualizado.'); await Promise.all([loadDetail(), onChanged()]) }
    catch (reason) { setError(reason.message) } finally { setBusy(false) }
  }

  return <aside className="op-case-detail op-daily-detail" aria-label="Detalhes da oportunidade"><button type="button" className="op-close" onClick={onClose} aria-label="Fechar"><X size={17} /></button>
    {!current && !error && <div className="op-loading"><span className="op-spinner" /> Carregando…</div>}
    {error && <div className="op-feedback error" role="alert"><AlertCircle size={16} /><span>{error}</span></div>}
    {current && <><span className="op-detail-kicker">Caso #{current.id}</span><h2>{current.paciente}</h2><p>{formatPhone(current.telefone)}</p><div className="op-detail-status"><StatusBadge status={current.status} /><QueueBadge item={current} /></div>
      {details.alertas.length > 0 && <section className="op-alert-list"><strong>Pendências ativas</strong>{details.alertas.map((alert) => <div key={alert.id}><AlertTriangle size={14} /><span>{alert.mensagem}</span></div>)}</section>}
      <dl><div><dt>Motivo da falta</dt><dd>{current.motivo_falta || 'Ainda não informado'}</dd></div><div><dt>Última interação</dt><dd>{current.ultima_interacao ? formatDateTime(current.ultima_interacao) : 'Nenhuma'}</dd></div><div><dt>Responsável</dt><dd>{current.responsavel || 'Sem responsável'}</dd></div><div><dt>Próximo passo</dt><dd>{nextStep(current)}</dd></div><div><dt>Origem</dt><dd>{current.id_atendimento_externo_origem ? current.fonte : 'Agenda Clinisis'}</dd></div>{current.atendimento_externo_reagendado && <div><dt>Reagendamento externo</dt><dd>{current.atendimento_externo_reagendado} · {current.situacao_reagendada_normalizada}</dd></div>}</dl>
      {!CLOSED.includes(current.status) && <CaseActionForm key={current.versao} current={current} details={details} responsibles={responsibles} onSubmit={save} busy={busy} />}
      {notice && <div className="op-feedback success" role="status"><CheckCircle2 size={16} /><span>{notice}</span></div>}
      <section className="op-case-history"><strong>Histórico</strong>{details.historico.slice(0, 8).map((event) => <article key={event.id}><time>{formatDateTime(event.criado_em)}</time><p>{event.descricao}</p><small>{event.autor || 'Sistema'}{event.canal ? ` · ${event.canal}` : ''}</small></article>)}</section>
    </>}
  </aside>
}

function CaseActionForm({ current, details, responsibles, onSubmit, busy }) {
  const initial = current.alerta_resposta_pendente ? 'contato' : current.alerta_sem_responsavel || current.alerta_sem_proxima_acao || current.alerta_revisar_recorrencia ? 'organizar' : 'contato'
  const [action, setAction] = useState(initial)
  const organized = action === 'organizar' || action === 'contato'
  const contact = action === 'contato'
  const response = action === 'resposta_recebida'
  const closing = action === 'nao_deseja' || action === 'nao_contatar'
  return <form className="op-case-form" onSubmit={onSubmit}><strong>Registrar próxima ação</strong><label>Ação<select name="acao" value={action} onChange={(event) => setAction(event.target.value)}><option value="organizar">Definir responsável e prazo</option><option value="contato">Registrar contato realizado</option><option value="resposta_recebida">Registrar resposta recebida</option>{current.status !== 'reagendado' && <option value="reagendar">{current.id_atendimento_externo_origem ? 'Vincular reagendamento externo' : 'Vincular reagendamento'}</option>}<option value="nao_deseja">Encerrar: não deseja</option><option value="nao_contatar">Encerrar: não contatar</option></select></label>
    {(organized || action === 'reagendar') && <label>Responsável<select name="idResponsavel" required defaultValue={current.id_responsavel || ''}><option value="">Selecione</option>{responsibles.map((item) => <option value={item.id} key={item.id}>{item.nome}</option>)}</select></label>}
    {organized && <><label>Próxima ação<input type="datetime-local" name="proximaAcao" required defaultValue={dateInput(current.proxima_acao)} /></label><label>Motivo da falta<input name="motivoFalta" maxLength="500" defaultValue={current.motivo_falta || ''} /></label><label className="op-check"><input type="checkbox" name="recorrente" defaultChecked={current.recorrente} /> Atendimento recorrente</label></>}
    {(contact || response) && <label>Canal<select name="canal" defaultValue="whatsapp"><option value="whatsapp">WhatsApp</option><option value="telefone">Telefone</option><option value="email">E-mail</option><option value="presencial">Presencial</option></select></label>}
    {contact && <label>Resultado<select name="resultado" defaultValue="em_contato"><option value="em_contato">Conversou / em acompanhamento</option><option value="sem_resposta">Sem resposta</option></select></label>}
    {action === 'reagendar' && !current.id_atendimento_externo_origem && <label>Nova consulta<select name="idConsulta" required defaultValue=""><option value="">Selecione</option>{details.consultas.map((item) => <option key={item.id} value={item.id}>#{item.id} · {formatDateTime(`${item.data}T${item.hora}`)}</option>)}</select></label>}
    {action === 'reagendar' && current.id_atendimento_externo_origem && <label>Novo atendimento da fonte<select name="idAtendimentoExterno" required defaultValue=""><option value="">Selecione</option>{(details.atendimentosExternos || []).map((item) => <option key={item.id} value={item.id}>{item.identificador} · {formatDateTime(item.agendado_em)} · {item.situacao_normalizada}</option>)}</select><small>Somente horários futuros do mesmo paciente, fonte, tipo e especialidade aparecem aqui.</small></label>}
    {(contact || response || closing) && <label>Registro<textarea name="observacao" required rows="3" maxLength="1500" /></label>}
    <button type="submit" className="op-primary-button" disabled={busy}>{busy ? 'Salvando…' : 'Salvar acompanhamento'}</button></form>
}

function ImportCsvModal({ onClose, onImported }) {
  const [file, setFile] = useState(null)
  const [sourceCode, setSourceCode] = useState('agenda-clinica')
  const [sourceName, setSourceName] = useState('Agenda da clínica')
  const [timezone, setTimezone] = useState('America/Fortaleza')
  const [preview, setPreview] = useState(null)
  const [mapping, setMapping] = useState({})
  const [statuses, setStatuses] = useState({})
  const [result, setResult] = useState(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  async function prepare() {
    if (!file) return
    setBusy(true); setError('')
    try {
      const response = await previewImportacaoCsv(file, timezone)
      setPreview(response); setMapping(response.mapeamento)
      setStatuses(Object.fromEntries(response.situacoesDetectadas.map((item) => [item.origem, item.sugestao])))
    } catch (requestError) { setError(requestError.message) }
    finally { setBusy(false) }
  }
  const ready = preview && preview.camposObrigatorios.every((field) => mapping[field]) && Object.values(statuses).every(Boolean)
  async function submit() {
    setBusy(true); setError('')
    try {
      const response = await importarCsv(file, { fonteCodigo: sourceCode.trim(), fonteNome: sourceName.trim(), fusoHorario: timezone, colunas: mapping, situacoes: statuses })
      setResult(response)
    } catch (requestError) { setError(requestError.message) }
    finally { setBusy(false) }
  }

  return <div className="op-modal-backdrop" role="presentation"><section className="op-import-modal" role="dialog" aria-modal="true" aria-labelledby="import-title">
    <header><div><span className="op-detail-kicker">Entrada de dados</span><h2 id="import-title">Importar pacientes e atendimentos</h2><p>O arquivo é validado antes de alterar os casos de pós-venda.</p></div><button type="button" className="op-close" onClick={onClose} aria-label="Fechar"><X size={18} /></button></header>
    {!result && <>
      <div className="op-import-source"><label><span>Arquivo CSV</span><input type="file" accept=".csv,text/csv" onChange={(event) => { setFile(event.target.files?.[0] || null); setPreview(null) }} /></label><label><span>Código da fonte</span><input value={sourceCode} pattern="[a-z0-9][a-z0-9_-]+" onChange={(event) => setSourceCode(event.target.value.toLowerCase().replace(/[^a-z0-9_-]/g, ''))} /></label><label><span>Nome da fonte</span><input value={sourceName} onChange={(event) => setSourceName(event.target.value)} /></label><label><span>Fuso horário</span><input value={timezone} onChange={(event) => setTimezone(event.target.value)} /></label></div>
      {!preview && <div className="op-import-drop"><FileSpreadsheet size={30} /><strong>{file?.name || 'Selecione o CSV exportado pela agenda'}</strong><span>UTF-8, até 5 MB e 20.000 linhas.</span><button type="button" className="op-primary-button" disabled={!file || busy} onClick={prepare}>{busy ? 'Lendo arquivo…' : 'Validar e visualizar'}</button></div>}
      {preview && <><div className="op-import-summary"><span><strong>{preview.totalLinhas}</strong> linhas</span><span className="success"><strong>{preview.linhasValidas}</strong> válidas</span><span className={preview.linhasComErro ? 'danger' : ''}><strong>{preview.linhasComErro}</strong> com erro</span><span>Separador <strong>{preview.separador}</strong></span></div>
        <section className="op-mapping"><h3>Mapeamento das colunas</h3><p>Confira qual coluna do arquivo representa cada dado.</p><div className="op-mapping-grid">{Object.entries(FIELD_LABELS).map(([field, label]) => <label key={field}><span>{label}{preview.camposObrigatorios.includes(field) ? ' *' : ''}</span><select value={mapping[field] || ''} onChange={(event) => setMapping((current) => ({ ...current, [field]: event.target.value }))}><option value="">Não mapear</option>{preview.colunas.map((column) => <option key={column} value={column}>{column}</option>)}</select></label>)}</div></section>
        <section className="op-mapping"><h3>Mapeamento das situações</h3><p>Autorização, faturamento e pagamento não comprovam presença.</p><div className="op-status-mapping">{preview.situacoesDetectadas.map((item) => <label key={item.origem}><span><strong>{item.origem}</strong><small>{item.quantidade} linha(s)</small></span><select value={statuses[item.origem] || ''} onChange={(event) => setStatuses((current) => ({ ...current, [item.origem]: event.target.value }))}><option value="">Selecione…</option>{SITUACOES.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>)}</div></section>
        <section className="op-preview-table"><h3>Prévia</h3><div><table><thead><tr><th>Paciente</th><th>Atendimento</th><th>Data</th><th>Situação</th><th>Validação</th></tr></thead><tbody>{preview.amostra.map((row, index) => <tr key={`${row.atendimentoId}-${index}`}><td>{row.paciente || '—'}<small>{row.pacienteId}</small></td><td>{row.atendimentoId || '—'}</td><td>{formatDateTime(row.dataHora)}</td><td>{row.situacao}<small>{row.situacaoNormalizada || 'Sem mapeamento'}</small></td><td className={row.valida ? 'success' : 'danger'}>{row.valida ? 'Válida' : row.mensagem}</td></tr>)}</tbody></table></div></section></>}
      {error && <div className="op-feedback error" role="alert"><AlertCircle size={17} /><span>{error}</span></div>}
      {preview && <footer><button type="button" className="op-secondary-button" onClick={() => setPreview(null)}>Voltar</button><button type="button" className="op-primary-button" disabled={!ready || busy || !sourceCode || !sourceName} onClick={submit}>{busy ? 'Importando…' : 'Confirmar importação'}</button></footer>}
    </>}
    {result && <ImportResult result={result} onDone={onImported} />}
  </section></div>
}

function ImportResult({ result, onDone }) {
  const data = result.importacao
  return <div className="op-import-result"><CheckCircle2 size={38} /><h3>Importação processada</h3><p>{data.arquivo} · {data.fonte}</p><div className="op-import-summary"><span><strong>{data.importadas}</strong> novas</span><span><strong>{data.atualizadas}</strong> atualizadas</span><span><strong>{data.semAlteracao}</strong> sem alteração</span><span className={data.revisao ? 'danger' : ''}><strong>{data.revisao}</strong> em revisão</span><span className={data.erros ? 'danger' : ''}><strong>{data.erros}</strong> erros</span></div>{result.pendencias.length > 0 && <div className="op-result-issues"><strong>Pendências para corrigir e reprocessar</strong>{result.pendencias.slice(0, 12).map((item) => <span key={item.linha}>Linha {item.linha}: {item.mensagem}</span>)}</div>}<button type="button" className="op-primary-button" onClick={onDone}>Ver oportunidades atualizadas</button></div>
}

function ImportHistory({ items }) {
  return <section className="op-card op-import-history"><header><div><h2>Importações recentes</h2><p>A origem, o responsável e o resultado ficam auditáveis.</p></div></header>{items.length === 0 ? <p>Nenhum arquivo importado.</p> : <div>{items.map((item) => <article key={item.id}><FileSpreadsheet size={17} /><div><strong>{item.arquivo}</strong><span>{item.fonte} · {item.responsavel} · {formatDateTime(item.iniciadaEm)}</span></div><span className={`op-import-status ${item.status.toLowerCase()}`}>{item.importadas} novas · {item.atualizadas} atualizadas · {item.revisao + item.erros} pendências</span></article>)}</div>}</section>
}

function Kpi({ label, value, note, tone = '', icon, active, onClick }) { return <button type="button" className={`op-kpi ${tone ? `op-kpi-${tone}` : ''} ${active ? 'active' : ''}`} onClick={onClick}><div className="op-kpi-label"><span>{label}</span>{icon}</div><strong>{value}</strong><small>{note}</small></button> }
function StatusBadge({ status }) { return <span className={`op-status op-status-${status || 'pendente'}`}><i aria-hidden="true" />{STATUS_LABELS[status] || status}</span> }
function initials(name = '') { return name.split(/\s+/).slice(0, 2).map((part) => part[0]).join('').toUpperCase() || '—' }
function formatPhone(value = '') { const digits = String(value).replace(/\D/g, ''); return digits.length === 11 ? `(${digits.slice(0, 2)}) ${digits.slice(2, 7)}-${digits.slice(7)}` : value || 'Sem telefone' }
function formatDateTime(value) { if (!value) return '—'; const date = new Date(value); return Number.isNaN(date.getTime()) ? String(value) : new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' }).format(date) }
function dateInput(value) { if (!value) return ''; const date = new Date(value); date.setMinutes(date.getMinutes() - date.getTimezoneOffset()); return date.toISOString().slice(0, 16) }
function importStatus(value) { return value === 'CONCLUIDA' ? 'Concluída' : value === 'CONCLUIDA_COM_ERROS' ? 'Concluída com pendências' : value }
