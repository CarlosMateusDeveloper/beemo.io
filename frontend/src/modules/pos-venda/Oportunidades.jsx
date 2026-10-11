import { useCallback, useEffect, useState } from 'react'
import {
  AlertCircle, CheckCircle2, Clock3, Database, FileSpreadsheet, History,
  RefreshCw, Search, Target, Upload, X,
} from 'lucide-react'
import { fetchImportacoes, fetchOportunidades, importarCsv, previewImportacaoCsv } from '../../components/retorno/api'
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

export default function Oportunidades() {
  const [data, setData] = useState(null)
  const [imports, setImports] = useState([])
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState('abertos')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [selected, setSelected] = useState(null)
  const [showImport, setShowImport] = useState(false)
  const [showHistory, setShowHistory] = useState(false)

  const load = useCallback(async () => {
    setLoading(true); setError('')
    try {
      const [cases, history] = await Promise.all([
        fetchOportunidades({ status, busca: search }), fetchImportacoes(),
      ])
      setData(cases); setImports(history)
    } catch (requestError) { setError(requestError.message) }
    finally { setLoading(false) }
  }, [search, status])

  useEffect(() => {
    const timeout = window.setTimeout(load, 250)
    return () => window.clearTimeout(timeout)
  }, [load])

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

      <section className="op-kpis" aria-label="Resumo das oportunidades">
        <Kpi label="Casos abertos" value={summary.abertos ?? '—'} note="Precisam de acompanhamento" tone="teal" icon={<Target size={17} />} />
        <Kpi label="A contatar" value={summary.a_contatar ?? '—'} note="Ação vencida ou ainda não definida" tone="warning" icon={<Clock3 size={17} />} />
        <Kpi label="Reagendados" value={summary.reagendados ?? '—'} note="Aguardando resultado da consulta" icon={<RefreshCw size={17} />} />
        <Kpi label="Recuperados" value={summary.recuperados ?? '—'} note="Comparecimento confirmado pela fonte" tone="teal" icon={<CheckCircle2 size={17} />} />
      </section>

      <section className="op-card op-list" aria-label="Lista de oportunidades">
        <div className="op-filter-bar">
          <label className="op-search"><Search size={16} aria-hidden="true" /><span className="op-sr-only">Buscar</span><input type="search" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Buscar paciente ou telefone" /></label>
          <label className="op-select"><span className="op-sr-only">Filtrar por status</span><select value={status} onChange={(event) => setStatus(event.target.value)}><option value="abertos">Casos abertos</option><option value="todos">Todos os casos</option>{Object.entries(STATUS_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
          <button type="button" className="op-icon-button" onClick={load} aria-label="Atualizar lista"><RefreshCw size={15} /></button>
        </div>

        {error && <div className="op-feedback error" role="alert"><AlertCircle size={18} /><div><strong>Não foi possível carregar as oportunidades</strong><span>{error}</span></div><button type="button" onClick={load}>Tentar novamente</button></div>}
        {loading && <div className="op-loading"><span className="op-spinner" /> Carregando oportunidades…</div>}
        {!loading && !error && <div className="op-table-wrap">
          <table className="op-table"><thead><tr><th>Paciente / origem</th><th>Falta</th><th>Atendimento</th><th>Status</th><th>Responsável</th><th>Próxima ação</th></tr></thead><tbody>{items.map((item) => <tr key={item.id} onClick={() => setSelected(item)} className={selected?.id === item.id ? 'selected' : ''}>
            <td><div className="op-patient"><span className="op-avatar">{initials(item.paciente)}</span><div><button type="button" className="op-patient-name">{item.paciente}</button><span>{formatPhone(item.telefone)}</span><small>{item.origem === 'importacao_csv' ? `CSV · ${item.fonte || 'fonte externa'}` : 'Agenda Clinisis'}</small></div></div></td>
            <td><strong>{formatDateTime(item.data_falta)}</strong><span className="op-cell-sub">Caso #{item.id}</span></td>
            <td><strong>{item.tipo || 'Não informado'}</strong><span className="op-cell-sub">{item.profissional} · {item.especialidade}</span></td>
            <td><StatusBadge status={item.status} /></td>
            <td><strong>{item.responsavel || 'Sem responsável'}</strong><span className="op-cell-sub">{item.tentativas || 0} tentativa(s)</span></td>
            <td><strong className={!item.proxima_acao ? 'op-overdue' : ''}>{item.proxima_acao ? formatDateTime(item.proxima_acao) : 'Não definida'}</strong></td>
          </tr>)}</tbody></table>
          {items.length === 0 && <div className="op-empty"><Target size={26} /><strong>Nenhuma oportunidade encontrada</strong><span>Importe o CSV da agenda da clínica ou ajuste os filtros.</span><button type="button" className="op-primary-button" onClick={() => setShowImport(true)}><Upload size={15} /> Importar CSV</button></div>}
        </div>}
        <footer className="op-list-footer"><span>{data?.total ?? 0} caso(s) encontrado(s)</span><span>Até 50 casos por página</span></footer>
      </section>

      {selected && <aside className="op-case-detail" aria-label={`Detalhes do caso de ${selected.paciente}`}><button type="button" className="op-close" onClick={() => setSelected(null)} aria-label="Fechar"><X size={17} /></button><span className="op-detail-kicker">Caso #{selected.id}</span><h2>{selected.paciente}</h2><p>{formatPhone(selected.telefone)}</p><StatusBadge status={selected.status} /><dl><div><dt>Origem</dt><dd>{selected.origem === 'importacao_csv' ? selected.fonte : 'Agenda Clinisis'}</dd></div><div><dt>Atendimento externo</dt><dd>{selected.atendimento_externo || '—'}</dd></div><div><dt>Profissional</dt><dd>{selected.profissional}</dd></div><div><dt>Especialidade</dt><dd>{selected.especialidade}</dd></div></dl><p className="op-detail-note">Dados importados permanecem vinculados à fonte e ao identificador externo. Um reagendamento não é tratado como recuperação até o comparecimento ser conciliado.</p></aside>}
    </div>
    {showImport && <ImportCsvModal onClose={() => setShowImport(false)} onImported={() => { setShowImport(false); load() }} />}
  </main>
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

function Kpi({ label, value, note, tone = '', icon }) { return <article className={`op-kpi ${tone ? `op-kpi-${tone}` : ''}`}><div className="op-kpi-label"><span>{label}</span>{icon}</div><strong>{value}</strong><small>{note}</small></article> }
function StatusBadge({ status }) { return <span className={`op-status op-status-${status || 'pendente'}`}><i aria-hidden="true" />{STATUS_LABELS[status] || status}</span> }
function initials(name = '') { return name.split(/\s+/).slice(0, 2).map((part) => part[0]).join('').toUpperCase() || '—' }
function formatPhone(value = '') { const digits = String(value).replace(/\D/g, ''); return digits.length === 11 ? `(${digits.slice(0, 2)}) ${digits.slice(2, 7)}-${digits.slice(7)}` : value || 'Sem telefone' }
function formatDateTime(value) { if (!value) return '—'; const date = new Date(value); return Number.isNaN(date.getTime()) ? String(value) : new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' }).format(date) }
function importStatus(value) { return value === 'CONCLUIDA' ? 'Concluída' : value === 'CONCLUIDA_COM_ERROS' ? 'Concluída com pendências' : value }
