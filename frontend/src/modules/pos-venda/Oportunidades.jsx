import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { AlertCircle, BarChart3, CalendarCheck2, Clock3, MessageCircle, Plus, Search, Target, UserCheck, X } from 'lucide-react'
import {
  atualizarCrmOportunidade, buscarPacientesCrm, converterCrmOportunidade, criarCrmOportunidade,
  fetchCrmIndicadores, fetchCrmOportunidade, fetchCrmOportunidades, fetchCrmResponsaveis,
  fetchMedicosCrm, registrarCrmInteracao, transicionarCrmOportunidade,
} from './crmApi'
import './Oportunidades.css'

const STAGES = {
  novo: 'Novo', em_contato: 'Em contato', qualificado: 'Qualificado',
  aguardando_agendamento: 'Aguardando agenda', agendado: 'Agendado', perdido: 'Perdido',
}
const NEXT = {
  novo: ['em_contato', 'qualificado', 'perdido'], em_contato: ['qualificado', 'aguardando_agendamento', 'perdido'],
  qualificado: ['em_contato', 'aguardando_agendamento', 'perdido'],
  aguardando_agendamento: ['em_contato', 'qualificado', 'perdido'], agendado: [], perdido: [],
}
const CHANNELS = [['whatsapp', 'WhatsApp'], ['telefone', 'Telefone'], ['site', 'Site'], ['indicacao', 'Indicação'], ['instagram', 'Instagram'], ['presencial', 'Presencial'], ['outro', 'Outro']]

export default function Oportunidades() {
  const [data, setData] = useState(null)
  const [responsibles, setResponsibles] = useState([])
  const [filters, setFilters] = useState({ etapa: 'abertas', busca: '', origem: '', responsavel: '' })
  const [selected, setSelected] = useState(null)
  const [create, setCreate] = useState(false)
  const [metrics, setMetrics] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    setLoading(true); setError('')
    try { setData(await fetchCrmOportunidades(filters)) } catch (reason) { setError(reason.message) }
    finally { setLoading(false) }
  }, [filters])
  useEffect(() => { const timer = setTimeout(load, 250); return () => clearTimeout(timer) }, [load])
  useEffect(() => { fetchCrmResponsaveis().then(setResponsibles).catch(() => {}) }, [])
  const summary = data?.resumo || {}

  async function showMetrics() {
    try { setMetrics(await fetchCrmIndicadores()) } catch (reason) { setError(reason.message) }
  }

  return <main className="opportunities-page crm-page" aria-labelledby="crm-title"><div className="opportunities-wrap">
    <header className="opportunities-heading"><div><p className="op-eyebrow">CRM de novos pacientes</p><h1 id="crm-title">Oportunidades</h1><span className="op-subtitle">Do primeiro contato até uma consulta realmente agendada, sem misturar interessado com prontuário.</span></div>
      <div className="op-heading-actions"><Link className="op-secondary-button" to="/pos-venda/oportunidades/retencao"><Target size={15} /> Recuperação de faltas</Link><button className="op-secondary-button" onClick={showMetrics}><BarChart3 size={15} /> Indicadores</button><button className="op-primary-button" onClick={() => setCreate(true)}><Plus size={15} /> Nova oportunidade</button></div>
    </header>
    <section className="op-kpis crm-kpis" aria-label="Resumo do CRM">
      <Kpi icon={<Target size={17} />} label="Em aberto" value={summary.abertas} note="Oportunidades ativas" />
      <Kpi icon={<Clock3 size={17} />} label="Precisam de ação" value={summary.paradas} note="Prazo vencido ou ausente" tone="warning" />
      <Kpi icon={<MessageCircle size={17} />} label="Sem resposta humana" value={summary.semRespostaHumana} note="Bot não conta como atendente" tone="warning" />
      <Kpi icon={<CalendarCheck2 size={17} />} label="Agendadas" value={summary.agendadas} note="Consultas reais no período" tone="teal" />
      <Kpi icon={<UserCheck size={17} />} label="1ª resposta" value={summary.tempoRespostaMin == null ? '—' : `${summary.tempoRespostaMin} min`} note="Média da equipe" />
    </section>
    <section className="op-card op-list crm-list"><div className="op-filter-bar">
      <label className="op-search"><Search size={16} /><span className="op-sr-only">Buscar</span><input value={filters.busca} onChange={event => setFilters({ ...filters, busca: event.target.value })} placeholder="Nome, telefone ou serviço" /></label>
      <select value={filters.etapa} onChange={event => setFilters({ ...filters, etapa: event.target.value })}><option value="abertas">Em aberto</option><option value="todas">Todas</option>{Object.entries(STAGES).map(([value, label]) => <option value={value} key={value}>{label}</option>)}</select>
      <select value={filters.origem} onChange={event => setFilters({ ...filters, origem: event.target.value })}><option value="">Todas as origens</option>{CHANNELS.map(([value, label]) => <option value={value} key={value}>{label}</option>)}</select>
      <select value={filters.responsavel} onChange={event => setFilters({ ...filters, responsavel: event.target.value })}><option value="">Toda a equipe</option>{responsibles.map(user => <option value={user.id} key={user.id}>{user.nome}</option>)}</select>
    </div>
    {error && <Feedback error={error} />}{loading && <div className="op-loading"><span className="op-spinner" /> Carregando oportunidades…</div>}
    {!loading && !error && <div className="op-table-wrap"><table className="op-table crm-table"><thead><tr><th>Interessado</th><th>Interesse / origem</th><th>Etapa</th><th>Próxima ação</th><th>Responsável</th></tr></thead><tbody>
      {(data?.itens || []).map(item => <tr key={item.id} className={selected === item.id ? 'selected' : ''} onClick={() => setSelected(item.id)}>
        <td><strong>{item.nome}</strong><span className="op-cell-sub">{item.telefone || item.email}</span></td><td><strong>{item.servicoInteresse}</strong><span className="op-cell-sub">{item.origem} · {channel(item.canalOrigem)}</span></td>
        <td><Stage value={item.etapa} /></td><td><strong className={item.parada ? 'op-overdue' : ''}>{item.proximaAcao ? dateTime(item.proximaAcao) : 'Não definida'}</strong><span className="op-cell-sub">{item.primeiraRespostaHumanaEm ? `Respondido em ${responseTime(item)}` : 'Aguardando resposta humana'}</span></td>
        <td><strong>{item.responsavel || 'Sem responsável'}</strong><span className="op-cell-sub">{item.orcamento == null ? 'Sem orçamento' : money(item.orcamento)}</span></td>
      </tr>)}</tbody></table>{data?.itens?.length === 0 && <div className="op-empty"><Target size={26} /><strong>Nenhuma oportunidade encontrada</strong><span>Cadastre um interessado ou ajuste os filtros.</span></div>}</div>}
    <footer className="op-list-footer"><span>{data?.total || 0} oportunidade(s)</span><span>Interessados só viram pacientes ao confirmar um agendamento</span></footer></section>
  </div>
  {selected && <Detail id={selected} responsibles={responsibles} onClose={() => setSelected(null)} onChanged={load} />}
  {create && <CreateModal responsibles={responsibles} onClose={() => setCreate(false)} onCreated={item => { setCreate(false); setSelected(item.oportunidade.id); load() }} />}
  {metrics && <Metrics data={metrics} onClose={() => setMetrics(null)} />}
  </main>
}

function Detail({ id, responsibles, onClose, onChanged }) {
  const [data, setData] = useState(null); const [error, setError] = useState(''); const [busy, setBusy] = useState(false); const [convert, setConvert] = useState(false)
  const load = useCallback(() => fetchCrmOportunidade(id).then(setData).catch(reason => setError(reason.message)), [id])
  useEffect(() => { load() }, [load])
  const current = data?.oportunidade
  async function save(event) { event.preventDefault(); const form = new FormData(event.currentTarget); await run(async () => atualizarCrmOportunidade(id, { servicoInteresse: form.get('servico'), responsavelId: number(form.get('responsavel')), proximaAcao: iso(form.get('proxima')), orcamento: number(form.get('orcamento')), versao: current.versao })) }
  async function interact(event) { event.preventDefault(); const form = new FormData(event.currentTarget); await run(async () => registrarCrmInteracao(id, { tipo: 'resposta_humana', canal: form.get('canal'), resumo: form.get('resumo'), ocorridoEm: new Date().toISOString() })); event.currentTarget.reset() }
  async function move(stage) { let reason = null; if (stage === 'perdido') { reason = window.prompt('Qual foi o motivo da perda?')?.trim(); if (!reason) return } await run(() => transicionarCrmOportunidade(id, { etapa: stage, motivoPerda: reason, proximaAcao: current.proximaAcao, responsavelId: current.responsavelId, versao: current.versao })) }
  async function run(action) { setBusy(true); setError(''); try { await action(); await Promise.all([load(), onChanged()]) } catch (reason) { setError(reason.message) } finally { setBusy(false) } }
  return <aside className="op-case-detail crm-detail"><button className="op-close" onClick={onClose} aria-label="Fechar"><X size={17} /></button>{!current && !error && <div className="op-loading"><span className="op-spinner" /></div>}{error && <Feedback error={error} />}
    {current && <><span className="op-detail-kicker">Oportunidade #{current.id}</span><h2>{current.nome}</h2><p>{current.telefone || current.email}</p><div className="op-detail-status"><Stage value={current.etapa} /> <span className="crm-origin">{current.origem}</span></div>
      {data.revisoes.some(review => review.status === 'pendente') && <div className="crm-review"><AlertCircle size={16} /><div><strong>Identidade precisa de revisão</strong><span>Há cadastros semelhantes. Nenhum prontuário foi associado automaticamente.</span></div></div>}
      {!['agendado', 'perdido'].includes(current.etapa) && <form className="op-case-form" onSubmit={save}><strong>Organização</strong><label>Serviço de interesse<input name="servico" defaultValue={current.servicoInteresse} required /></label><label>Responsável<select name="responsavel" defaultValue={current.responsavelId || ''}><option value="">Sem responsável</option>{responsibles.map(user => <option key={user.id} value={user.id}>{user.nome}</option>)}</select></label><label>Próxima ação<input name="proxima" type="datetime-local" defaultValue={localInput(current.proximaAcao)} /></label><label>Orçamento<input name="orcamento" type="number" min="0" step="0.01" defaultValue={current.orcamento || ''} /></label><button className="op-secondary-button" disabled={busy}>Salvar organização</button></form>}
      {!['agendado', 'perdido'].includes(current.etapa) && <form className="op-case-form" onSubmit={interact}><strong>Registrar contato humano</strong><label>Canal<select name="canal" defaultValue="whatsapp"><option value="whatsapp">WhatsApp</option><option value="telefone">Telefone</option><option value="email">E-mail</option><option value="presencial">Presencial</option></select></label><label>Resumo<textarea name="resumo" required maxLength="2000" /></label><button className="op-secondary-button" disabled={busy}>Registrar interação</button></form>}
      {!['agendado', 'perdido'].includes(current.etapa) && <div className="crm-stage-actions"><strong>Avançar etapa</strong>{NEXT[current.etapa].filter(stage => stage !== 'perdido').map(stage => <button key={stage} disabled={busy} onClick={() => move(stage)}>{STAGES[stage]}</button>)}<button className="danger" disabled={busy} onClick={() => move('perdido')}>Marcar como perdida</button><button className="op-primary-button" disabled={busy} onClick={() => setConvert(true)}><CalendarCheck2 size={15} /> Converter em agendamento</button></div>}
      {current.etapa === 'agendado' && <div className="crm-converted"><CalendarCheck2 size={20} /><div><strong>Consulta #{current.consultaId} agendada</strong><span>Paciente #{current.pacienteId}. Cancelamentos e reagendamentos permanecem no histórico.</span></div></div>}
      <section className="op-case-history"><strong>Histórico auditável</strong>{data.historico.map(item => <article key={item.id}><time>{dateTime(item.ocorridoEm)}</time><p>{eventLabel(item)}</p><small>{item.automatico ? 'Automação' : item.autor || 'Sistema'}</small></article>)}</section>
    </>}
    {convert && <ConvertModal opportunity={current} onClose={() => setConvert(false)} onConverted={async () => { setConvert(false); await Promise.all([load(), onChanged()]) }} />}
  </aside>
}

function CreateModal({ responsibles, onClose, onCreated }) {
  const [busy, setBusy] = useState(false); const [error, setError] = useState('')
  async function submit(event) { event.preventDefault(); const form = new FormData(event.currentTarget); setBusy(true); setError(''); try { onCreated(await criarCrmOportunidade({ nome: form.get('nome'), telefone: form.get('telefone') || null, email: form.get('email') || null, canalPreferido: form.get('canalPreferido'), contatoPermitido: form.get('permitido') === 'on', canalOrigem: form.get('origem'), origem: form.get('detalheOrigem'), servicoInteresse: form.get('servico'), responsavelId: number(form.get('responsavel')), proximaAcao: iso(form.get('proxima')), orcamento: number(form.get('orcamento')), idempotencyKey: crypto.randomUUID(), conversaExternaId: null })) } catch (reason) { setError(reason.message) } finally { setBusy(false) } }
  return <Modal title="Nova oportunidade" subtitle="Cadastre o interesse sem criar prontuário ou exigir CPF." onClose={onClose}><form className="crm-modal-form" onSubmit={submit}><label>Nome<input name="nome" required maxLength="160" autoFocus /></label><label>Telefone<input name="telefone" placeholder="(85) 99999-9999" /></label><label>E-mail<input name="email" type="email" /></label><label>Canal preferido<select name="canalPreferido"><option value="whatsapp">WhatsApp</option><option value="telefone">Telefone</option><option value="email">E-mail</option><option value="presencial">Presencial</option></select></label><label>Origem<select name="origem">{CHANNELS.map(([value, label]) => <option value={value} key={value}>{label}</option>)}</select></label><label>Detalhe da origem<input name="detalheOrigem" required placeholder="Ex.: indicação Dra. Ana" /></label><label className="wide">Serviço de interesse<input name="servico" required /></label><label>Responsável<select name="responsavel"><option value="">Sem responsável</option>{responsibles.map(user => <option key={user.id} value={user.id}>{user.nome}</option>)}</select></label><label>Próxima ação<input name="proxima" type="datetime-local" /></label><label>Orçamento<input name="orcamento" type="number" min="0" step="0.01" /></label><label className="crm-check wide"><input name="permitido" type="checkbox" defaultChecked /> Contato autorizado para esta finalidade de atendimento</label>{error && <Feedback error={error} />}<footer><button type="button" className="op-secondary-button" onClick={onClose}>Cancelar</button><button className="op-primary-button" disabled={busy}>{busy ? 'Salvando…' : 'Criar oportunidade'}</button></footer></form></Modal>
}

function ConvertModal({ opportunity, onClose, onConverted }) {
  const [mode, setMode] = useState('existente'); const [patients, setPatients] = useState([]); const [doctors, setDoctors] = useState([]); const [review, setReview] = useState(null); const [busy, setBusy] = useState(false); const [error, setError] = useState('')
  useEffect(() => { fetchMedicosCrm().then(setDoctors).catch(() => {}) }, [])
  async function search(value) { if (value.trim().length < 2) return setPatients([]); try { const result = await buscarPacientesCrm(value); setPatients(result.content || []) } catch { setPatients([]) } }
  async function submit(event, confirm = false) { event?.preventDefault(); const form = new FormData(event?.currentTarget || document.querySelector('#crm-convert-form')); setBusy(true); setError(''); try { const phone = String(form.get('telefone') || '').replace(/\D/g, ''); const payload = { pacienteId: mode === 'existente' ? number(form.get('paciente')) : null, novoPaciente: mode === 'novo' ? { nome: form.get('nome'), cpf: String(form.get('cpf')).replace(/\D/g, ''), dataNascimento: form.get('nascimento'), telefone: phone, email: form.get('email') || null } : null, confirmarNovoPaciente: confirm, agendaId: null, medicoId: number(form.get('medico')), data: form.get('data'), hora: form.get('hora'), tipo: 'Consulta', duracaoMinutos: number(form.get('duracao')) || 30, versao: opportunity.versao }; const result = await converterCrmOportunidade(opportunity.id, payload); if (result.status === 'revisao_necessaria') setReview(result); else onConverted() } catch (reason) { setError(reason.message) } finally { setBusy(false) } }
  return <Modal title="Converter em consulta" subtitle="A conversão cria um agendamento real e mantém a origem rastreável." onClose={onClose}><form id="crm-convert-form" className="crm-modal-form" onSubmit={submit}><div className="crm-mode wide"><button type="button" className={mode === 'existente' ? 'active' : ''} onClick={() => setMode('existente')}>Paciente existente</button><button type="button" className={mode === 'novo' ? 'active' : ''} onClick={() => setMode('novo')}>Novo paciente</button></div>{mode === 'existente' ? <><label className="wide">Buscar paciente<input onChange={event => search(event.target.value)} placeholder="Nome, CPF ou telefone" /></label><label className="wide">Paciente<select name="paciente" required><option value="">Selecione após buscar</option>{patients.map(patient => <option value={patient.id} key={patient.id}>{patient.nome} · {patient.cpfMascarado || ''}</option>)}</select></label></> : <><label>Nome completo<input name="nome" defaultValue={opportunity.nome} required /></label><label>CPF<input name="cpf" required /></label><label>Data de nascimento<input name="nascimento" type="date" required /></label><label>Telefone<input name="telefone" defaultValue={opportunity.telefone || ''} required /></label><label className="wide">E-mail<input name="email" type="email" defaultValue={opportunity.email || ''} /></label></>}<label>Profissional<select name="medico" required><option value="">Selecione</option>{doctors.map(doctor => <option value={doctor.id} key={doctor.id}>{doctor.nome}</option>)}</select></label><label>Data<input name="data" type="date" min={new Date().toISOString().slice(0, 10)} required /></label><label>Horário<input name="hora" type="time" required /></label><label>Duração<select name="duracao"><option value="30">30 min</option><option value="45">45 min</option><option value="60">60 min</option></select></label>{review && <div className="crm-review wide"><AlertCircle size={16} /><div><strong>Possível cadastro duplicado</strong><span>{review.mensagem}</span>{review.candidatos.map(candidate => <small key={candidate.id}>{candidate.nome} · {candidate.cpf || candidate.telefone}</small>)}</div></div>}{error && <Feedback error={error} />}<footer><button type="button" className="op-secondary-button" onClick={onClose}>Cancelar</button>{review?.podeCriarNovo && mode === 'novo' && <button type="button" className="op-secondary-button" disabled={busy} onClick={() => submit(null, true)}>Confirmar novo cadastro</button>}<button className="op-primary-button" disabled={busy}>{busy ? 'Agendando…' : 'Criar consulta'}</button></footer></form></Modal>
}

function Metrics({ data, onClose }) { return <Modal title="Conversão do CRM" subtitle="O denominador considera oportunidades criadas no período; só consulta real entra como conversão." onClose={onClose}><div className="crm-metrics"><section><h3>Por origem</h3>{data.porOrigem.map(item => <Metric key={item.origem} label={item.origem} item={item} />)}</section><section><h3>Por responsável</h3>{data.porResponsavel.map(item => <Metric key={item.responsavel} label={item.responsavel} item={item} />)}</section><details><summary>Definições dos indicadores</summary>{Object.entries(data.definicoes).map(([key, value]) => <p key={key}><strong>{key}</strong> — {value}</p>)}</details></div></Modal> }
function Metric({ label, item }) { return <div className="crm-metric"><strong>{label}</strong><span>{item.agendamentos}/{item.oportunidades} agendadas</span><b>{item.conversaoPct || 0}%</b></div> }
function Modal({ title, subtitle, onClose, children }) { return <div className="op-modal-backdrop" onMouseDown={event => event.target === event.currentTarget && onClose()}><section className="op-import-modal crm-modal"><header><div><span className="op-detail-kicker">CRM</span><h2>{title}</h2><p>{subtitle}</p></div><button className="op-close" onClick={onClose}><X size={17} /></button></header>{children}</section></div> }
function Kpi({ icon, label, value, note, tone = '' }) { return <article className={`op-kpi ${tone}`}><div className="op-kpi-icon">{icon}</div><div><span>{label}</span><strong>{value ?? '—'}</strong><small>{note}</small></div></article> }
function Stage({ value }) { return <span className={`crm-stage ${value}`}>{STAGES[value] || value}</span> }
function Feedback({ error }) { return <div className="op-feedback error" role="alert"><AlertCircle size={16} /><span>{error}</span></div> }
function channel(value) { return CHANNELS.find(([key]) => key === value)?.[1] || value }
function number(value) { return value === '' || value == null ? null : Number(value) }
function iso(value) { return value ? new Date(value).toISOString() : null }
function localInput(value) { if (!value) return ''; const date = new Date(value); date.setMinutes(date.getMinutes() - date.getTimezoneOffset()); return date.toISOString().slice(0, 16) }
function dateTime(value) { return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value)) }
function money(value) { return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value) }
function responseTime(item) { const minutes = Math.max(0, Math.round((new Date(item.primeiraRespostaHumanaEm) - new Date(item.primeiroContatoEm)) / 60000)); return minutes < 60 ? `${minutes} min` : `${Math.floor(minutes / 60)}h ${minutes % 60}min` }
function eventLabel(item) { if (item.tipo === 'criada') return 'Oportunidade criada'; if (item.tipo === 'etapa') return `${STAGES[item.etapaAnterior]} → ${STAGES[item.etapaNova]}`; if (item.tipo === 'conversao') return 'Convertida em consulta agendada'; if (item.tipo === 'resposta_humana') return 'Primeira resposta humana registrada'; if (item.tipo === 'resposta_automatica') return 'Resposta automática enviada'; if (item.tipo === 'mensagem_entrada') return 'Mensagem recebida'; if (item.tipo === 'cancelamento') return 'Consulta cancelada'; if (item.tipo === 'reagendamento') return 'Consulta reagendada'; return 'Oportunidade atualizada' }
