import { useMemo, useState } from 'react'
import {
  CalendarDays, Check, CheckCircle2, Filter,
  Pause, Play, Plus, Search, Target, Users, X, Zap,
} from 'lucide-react'
import './Jornadas.css'

const STATUS_LABELS = { ACTIVE: 'Ativa', DRAFT: 'Rascunho', PAUSED: 'Pausada', ARCHIVED: 'Arquivada' }
const TYPE_LABELS = { RETURN: 'Retorno', FOLLOW_UP: 'Pós-atendimento', REACTIVATION: 'Reativação', PREVENTIVE: 'Preventiva' }

const DEMO_JOURNEYS = [
  { id: 'journey-001', name: 'Pós-limpeza preventiva', description: 'Acompanhamento após profilaxia e convite para o próximo ciclo preventivo.', status: 'ACTIVE', type: 'FOLLOW_UP', audience: 'Pacientes que concluíram limpeza', enrolled: 48, completed: 31, conversion: 27, owner: 'Carla Mendes', updated: 'Hoje, 09:12', nextAction: '12 mensagens programadas para hoje', steps: [{ day: 'D+1', title: 'Orientações e cuidado', channel: 'WhatsApp' }, { day: 'D+7', title: 'Como você está?', channel: 'WhatsApp' }, { day: 'D+150', title: 'Lembrete preventivo', channel: 'Tarefa' }, { day: 'D+180', title: 'Convite para retorno', channel: 'WhatsApp' }] },
  { id: 'journey-002', name: 'Reativação 180 dias', description: 'Recupera pacientes sem atendimento há seis meses ou mais.', status: 'ACTIVE', type: 'REACTIVATION', audience: 'Inativos há 180 dias', enrolled: 36, completed: 18, conversion: 19, owner: 'Rafael Lima', updated: 'Ontem, 17:40', nextAction: '8 tarefas aguardando responsável', steps: [{ day: 'D0', title: 'Mensagem de reaproximação', channel: 'WhatsApp' }, { day: 'D+3', title: 'Segunda tentativa', channel: 'Tarefa' }, { day: 'D+10', title: 'Oferta de avaliação', channel: 'WhatsApp' }] },
  { id: 'journey-003', name: 'Retorno clínico assistido', description: 'Organiza confirmação, lembrete e acompanhamento de retornos indicados.', status: 'ACTIVE', type: 'RETURN', audience: 'Retorno indicado em prontuário', enrolled: 29, completed: 22, conversion: 34, owner: 'Carla Mendes', updated: 'Hoje, 08:35', nextAction: '5 pacientes chegam à etapa de confirmação', steps: [{ day: 'D0', title: 'Confirmar interesse', channel: 'Tarefa' }, { day: 'D+2', title: 'Sugerir horários', channel: 'WhatsApp' }, { day: 'D+5', title: 'Lembrete à equipe', channel: 'Tarefa' }] },
  { id: 'journey-004', name: 'Pós-procedimento 7 dias', description: 'Confere recuperação e identifica necessidade de contato clínico.', status: 'PAUSED', type: 'FOLLOW_UP', audience: 'Procedimentos selecionados', enrolled: 13, completed: 9, conversion: 15, owner: 'Você', updated: '02 out, 14:20', nextAction: 'Pausada para revisão do conteúdo', steps: [{ day: 'D+1', title: 'Orientações iniciais', channel: 'WhatsApp' }, { day: 'D+3', title: 'Verificação de sintomas', channel: 'Tarefa' }, { day: 'D+7', title: 'Pesquisa de evolução', channel: 'WhatsApp' }] },
  { id: 'journey-005', name: 'Manutenção de tratamento', description: 'Modelo para ciclos recorrentes de manutenção.', status: 'DRAFT', type: 'PREVENTIVE', audience: 'Segmento ainda não definido', enrolled: 0, completed: 0, conversion: 0, owner: 'Você', updated: '30 set, 11:05', nextAction: 'Definir público antes de ativar', steps: [{ day: 'D0', title: 'Aviso de manutenção', channel: 'WhatsApp' }, { day: 'D+5', title: 'Criar tarefa de contato', channel: 'Tarefa' }] },
  { id: 'journey-006', name: 'Campanha retorno 2025', description: 'Fluxo histórico de recuperação da base anterior.', status: 'ARCHIVED', type: 'REACTIVATION', audience: 'Base histórica 2025', enrolled: 184, completed: 161, conversion: 23, owner: 'Rafael Lima', updated: '12 jan, 16:00', nextAction: 'Jornada encerrada', steps: [{ day: 'D0', title: 'Convite inicial', channel: 'WhatsApp' }, { day: 'D+7', title: 'Última tentativa', channel: 'Tarefa' }] },
]

const TABS = [['all', 'Todas'], ['ACTIVE', 'Ativas'], ['DRAFT', 'Rascunhos'], ['PAUSED', 'Pausadas'], ['ARCHIVED', 'Arquivadas']]

export default function Jornadas() {
  const [journeys, setJourneys] = useState(DEMO_JOURNEYS)
  const [tab, setTab] = useState('all')
  const [search, setSearch] = useState('')
  const [type, setType] = useState('ALL')
  const [selectedId, setSelectedId] = useState(null)
  const [creating, setCreating] = useState(false)
  const [notice, setNotice] = useState('')

  const filtered = useMemo(() => journeys.filter((journey) => {
    const term = search.trim().toLowerCase()
    const matchesSearch = !term || [journey.name, journey.description, journey.audience, journey.owner].some((field) => field.toLowerCase().includes(term))
    return (tab === 'all' || journey.status === tab) && (type === 'ALL' || journey.type === type) && matchesSearch
  }), [journeys, search, tab, type])

  const selected = journeys.find((journey) => journey.id === selectedId)
  const active = journeys.filter((journey) => journey.status === 'ACTIVE')
  const activePatients = active.reduce((total, journey) => total + journey.enrolled, 0)
  const finishedPatients = active.reduce((total, journey) => total + journey.completed, 0)

  function showNotice(message) {
    setNotice(message)
    window.setTimeout(() => setNotice(''), 2800)
  }

  function changeStatus(id, status) {
    setJourneys((current) => current.map((journey) => journey.id === id ? { ...journey, status, updated: 'Agora', nextAction: status === 'ACTIVE' ? 'Jornada pronta para processar novos pacientes' : 'Pausada manualmente pela equipe' } : journey))
    showNotice(status === 'ACTIVE' ? 'Jornada ativada.' : 'Jornada pausada.')
  }

  function createJourney(event) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    const draft = {
      id: `journey-${Date.now()}`,
      name: data.get('name').trim(),
      description: data.get('description').trim() || 'Jornada em preparação.',
      status: 'DRAFT', type: data.get('type'), audience: data.get('audience').trim(),
      enrolled: 0, completed: 0, conversion: 0, owner: data.get('owner'), updated: 'Agora',
      nextAction: 'Configure as etapas para ativar',
      steps: [{ day: 'D0', title: 'Primeiro contato', channel: data.get('channel') }],
    }
    setJourneys((current) => [draft, ...current])
    setSelectedId(draft.id)
    setTab('all')
    setCreating(false)
    showNotice('Jornada salva como rascunho.')
  }

  return <main className="journeys-page" aria-labelledby="journeys-title">
    <div className="journeys-wrap">
      <header className="journeys-heading"><div><p>Pós-atendimento e retenção</p><h1 id="journeys-title">Jornadas</h1><span>Automatize o acompanhamento sem perder o controle da equipe.</span></div><div className="journeys-heading-actions"><span className="journeys-demo-label">Dados demonstrativos</span><button type="button" className="journeys-primary-button" onClick={() => setCreating(true)}><Plus size={15} /> Nova jornada</button></div></header>

      <section className="journeys-kpis" aria-label="Resumo das jornadas">
        <Kpi label="Jornadas ativas" value={active.length} note={`${journeys.filter((item) => item.status === 'DRAFT').length} rascunho em preparação`} icon={<Zap size={17} />} />
        <Kpi label="Pacientes em jornada" value={activePatients} note="Distribuídos nos fluxos ativos" tone="teal" icon={<Users size={17} />} />
        <Kpi label="Etapas concluídas" value={`${activePatients ? Math.round(finishedPatients / activePatients * 100) : 0}%`} note={`${finishedPatients} pacientes avançaram no fluxo`} tone="success" icon={<CheckCircle2 size={17} />} />
        <Kpi label="Conversão média" value={`${Math.round(active.reduce((total, item) => total + item.conversion, 0) / Math.max(active.length, 1))}%`} note="Agendamentos nas jornadas ativas" tone="teal" icon={<Target size={17} />} />
      </section>

      <div className={`journeys-content${selected ? ' has-detail' : ''}`}>
        <section className="journeys-card journeys-list" aria-label="Lista de jornadas">
          <nav className="journeys-tabs" aria-label="Status das jornadas">{TABS.map(([value, label]) => <button type="button" key={value} className={tab === value ? 'active' : ''} onClick={() => setTab(value)}>{label}<span>{value === 'all' ? journeys.length : journeys.filter((item) => item.status === value).length}</span></button>)}</nav>
          <div className="journeys-filter-bar"><label className="journeys-search"><Search size={15} aria-hidden="true" /><span className="journeys-sr-only">Buscar jornadas</span><input type="search" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Buscar jornada, público ou responsável" /></label><label className="journeys-select"><span className="journeys-sr-only">Filtrar por objetivo</span><select value={type} onChange={(event) => setType(event.target.value)}><option value="ALL">Todos os objetivos</option>{Object.entries(TYPE_LABELS).map(([value, label]) => <option value={value} key={value}>{label}</option>)}</select></label><button type="button" className="journeys-filter-button" onClick={() => showNotice('Os filtros disponíveis já estão aplicados à lista.')}><Filter size={14} /> Filtros</button></div>

          <div className="journeys-grid">{filtered.map((journey) => <article className={`journey-item${selectedId === journey.id ? ' selected' : ''}`} key={journey.id}><header><div><TypeBadge type={journey.type} /><StatusBadge status={journey.status} /></div><button type="button" aria-label={`Abrir detalhes de ${journey.name}`} onClick={() => setSelectedId(journey.id)}>Ver detalhes</button></header><button type="button" className="journey-name" onClick={() => setSelectedId(journey.id)}>{journey.name}</button><p>{journey.description}</p><div className="journey-audience"><Users size={14} /><span><strong>{journey.audience}</strong>{journey.enrolled} pacientes incluídos</span></div><div className="journey-progress"><span><b>{journey.completed}</b> de {journey.enrolled || '—'} concluíram etapas</span><strong>{journey.enrolled ? Math.round(journey.completed / journey.enrolled * 100) : 0}%</strong><div><i style={{ width: `${journey.enrolled ? journey.completed / journey.enrolled * 100 : 0}%` }} /></div></div><footer><span>{journey.owner}</span><span>{journey.updated}</span></footer></article>)}</div>
          {filtered.length === 0 && <div className="journeys-empty"><Zap size={28} /><strong>Nenhuma jornada encontrada</strong><span>Ajuste os filtros ou crie um novo fluxo de acompanhamento.</span><button type="button" className="journeys-secondary-button" onClick={() => { setSearch(''); setType('ALL'); setTab('all') }}>Limpar filtros</button></div>}
        </section>

        {selected && <JourneyDetail journey={selected} onClose={() => setSelectedId(null)} onStatus={(status) => changeStatus(selected.id, status)} onNotice={showNotice} />}
      </div>
      <p className="journeys-data-note">Prévia do MVP com dados de exemplo. Nenhuma mensagem é enviada e nenhuma inscrição é persistida até a conexão da API de jornadas.</p>
    </div>
    {creating && <JourneyModal onClose={() => setCreating(false)} onSubmit={createJourney} />}
    {notice && <div className="journeys-toast" role="status"><Check size={14} />{notice}</div>}
  </main>
}

function JourneyDetail({ journey, onClose, onStatus, onNotice }) {
  return <aside className="journeys-card journey-detail" aria-label={`Detalhes da jornada ${journey.name}`}><header><div><span className="journey-detail-kicker">{TYPE_LABELS[journey.type]}</span><h2>{journey.name}</h2><StatusBadge status={journey.status} /></div><button type="button" className="journeys-close" aria-label="Fechar detalhes" onClick={onClose}><X size={17} /></button></header><p className="journey-detail-description">{journey.description}</p><dl className="journey-detail-data"><div><dt>Público</dt><dd>{journey.audience}</dd></div><div><dt>Responsável</dt><dd>{journey.owner}</dd></div><div><dt>Conversão</dt><dd className="journey-conversion">{journey.conversion}%</dd></div><div><dt>Próxima ação</dt><dd>{journey.nextAction}</dd></div></dl><section className="journey-steps"><h3>Etapas da jornada</h3><ol>{journey.steps.map((step, index) => <li key={`${step.day}-${step.title}`}><span>{index + 1}</span><div><strong>{step.title}</strong><small>{step.day} · {step.channel}</small></div></li>)}</ol></section><div className="journey-detail-actions">{journey.status === 'ACTIVE' ? <button type="button" className="journeys-secondary-button danger" onClick={() => onStatus('PAUSED')}><Pause size={14} /> Pausar jornada</button> : journey.status !== 'ARCHIVED' && <button type="button" className="journeys-primary-button" onClick={() => onStatus('ACTIVE')}><Play size={14} /> Ativar jornada</button>}<button type="button" className="journeys-secondary-button" onClick={() => onNotice('Editor de etapas preparado para a próxima integração.')}><CalendarDays size={14} /> Editar etapas</button></div></aside>
}

function JourneyModal({ onClose, onSubmit }) {
  return <div className="journeys-modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose() }}><section className="journeys-modal" role="dialog" aria-modal="true" aria-labelledby="new-journey-title"><header><div><span>Novo fluxo</span><h2 id="new-journey-title">Criar jornada</h2></div><button type="button" className="journeys-close" aria-label="Fechar" onClick={onClose}><X size={18} /></button></header><form onSubmit={onSubmit}><label><span>Nome da jornada</span><input name="name" required maxLength="90" placeholder="Ex.: Retorno preventivo semestral" /></label><label><span>Descrição</span><textarea name="description" rows="3" maxLength="240" placeholder="Qual resultado este fluxo deve alcançar?" /></label><div className="journeys-form-grid"><label><span>Objetivo</span><select name="type" defaultValue="RETURN">{Object.entries(TYPE_LABELS).map(([value, label]) => <option value={value} key={value}>{label}</option>)}</select></label><label><span>Primeiro canal</span><select name="channel" defaultValue="WhatsApp"><option>WhatsApp</option><option>Tarefa</option><option>Ligação</option></select></label></div><label><span>Público inicial</span><input name="audience" required maxLength="100" placeholder="Ex.: Pacientes inativos há 180 dias" /></label><label><span>Responsável</span><select name="owner" defaultValue="Você"><option>Você</option><option>Carla Mendes</option><option>Rafael Lima</option></select></label><footer><button type="button" className="journeys-secondary-button" onClick={onClose}>Cancelar</button><button type="submit" className="journeys-primary-button"><Plus size={15} /> Salvar rascunho</button></footer></form></section></div>
}

function Kpi({ label, value, note, tone = '', icon }) { return <article className={`journeys-kpi ${tone ? `journeys-kpi-${tone}` : ''}`}><div><span>{label}</span>{icon}</div><strong>{value}</strong><small>{note}</small></article> }
function StatusBadge({ status }) { return <span className={`journey-status journey-status-${status.toLowerCase()}`}><i />{STATUS_LABELS[status]}</span> }
function TypeBadge({ type }) { return <span className="journey-type">{TYPE_LABELS[type]}</span> }
