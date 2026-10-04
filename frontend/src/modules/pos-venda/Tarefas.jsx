import { useMemo, useState } from 'react'
import {
  AlertCircle, CalendarDays, Check, CheckCircle2, Clock3, Filter,
  ListTodo, Plus, RotateCcw, Search, User, UserPlus, X,
} from 'lucide-react'
import './Tarefas.css'

const DEMO_TASKS = [
  { id: 'task-001', title: 'Confirmar retorno pós-procedimento', patient: 'Maria Silva', initials: 'MS', category: 'Ligação', deadline: 'Hoje, 09:30', bucket: 'today', priority: 'Alta', owner: 'Carla Mendes', status: 'OPEN', origin: 'Jornada Pós-limpeza', notes: 'Confirmar evolução e oferecer os horários disponíveis desta semana.' },
  { id: 'task-002', title: 'Reagendar avaliação clínica', patient: 'Ana Costa', initials: 'AC', category: 'WhatsApp', deadline: 'Ontem, 16:00', bucket: 'overdue', priority: 'Alta', owner: 'Rafael Lima', status: 'OPEN', origin: 'Oportunidade de reativação', notes: 'Paciente respondeu à campanha e pediu opções no período da tarde.' },
  { id: 'task-003', title: 'Validar documentação do convênio', patient: 'Carlos Souza', initials: 'CS', category: 'Administrativo', deadline: 'Hoje, 14:00', bucket: 'today', priority: 'Média', owner: 'Carla Mendes', status: 'OPEN', origin: 'Retorno clínico', notes: 'Conferir autorização antes de confirmar o novo atendimento.' },
  { id: 'task-004', title: 'Realizar segunda tentativa de contato', patient: 'João Almeida', initials: 'JA', category: 'Ligação', deadline: 'Amanhã, 10:00', bucket: 'upcoming', priority: 'Média', owner: 'Você', status: 'OPEN', origin: 'Jornada Pós-Botox', notes: 'Primeira mensagem enviada há dois dias, ainda sem resposta.' },
  { id: 'task-005', title: 'Enviar orientações pós-consulta', patient: 'Beatriz Rocha', initials: 'BR', category: 'WhatsApp', deadline: '07 out, 11:30', bucket: 'upcoming', priority: 'Baixa', owner: 'Você', status: 'OPEN', origin: 'Pós-atendimento', notes: 'Usar o modelo revisado pela equipe clínica.' },
  { id: 'task-006', title: 'Conferir comparecimento na agenda', patient: 'Lucas Fernandes', initials: 'LF', category: 'Agenda', deadline: '09 out, 17:00', bucket: 'later', priority: 'Média', owner: 'Rafael Lima', status: 'OPEN', origin: 'Retorno de procedimento', notes: 'Encerrar a oportunidade apenas após a baixa correta na agenda.' },
  { id: 'task-007', title: 'Registrar resultado do contato', patient: 'Juliana Martins', initials: 'JM', category: 'Administrativo', deadline: 'Concluída hoje', bucket: 'completed', priority: 'Baixa', owner: 'Você', status: 'DONE', origin: 'Pós-tratamento', notes: 'Paciente confirmou que manterá o acompanhamento atual.' },
  { id: 'task-008', title: 'Revisar paciente sem resposta', patient: 'Rafael Oliveira', initials: 'RO', category: 'Ligação', deadline: 'Concluída ontem', bucket: 'completed', priority: 'Alta', owner: 'Carla Mendes', status: 'DONE', origin: 'Reativação 180 dias', notes: 'Caso revisado e mantido em acompanhamento.' },
]

const TABS = [
  ['mine', 'Minhas tarefas'],
  ['all', 'Todas'],
  ['overdue', 'Vencidas'],
  ['today', 'Hoje'],
  ['upcoming', 'Próximas'],
  ['completed', 'Concluídas'],
]

export default function Tarefas() {
  const [tasks, setTasks] = useState(DEMO_TASKS)
  const [tab, setTab] = useState('mine')
  const [search, setSearch] = useState('')
  const [owner, setOwner] = useState('ALL')
  const [priority, setPriority] = useState('ALL')
  const [selectedId, setSelectedId] = useState(null)
  const [creating, setCreating] = useState(false)
  const [notice, setNotice] = useState('')

  const filtered = useMemo(() => tasks.filter((task) => {
    const term = search.trim().toLowerCase()
    const matchesSearch = !term || [task.title, task.patient, task.category, task.origin].some((field) => field.toLowerCase().includes(term))
    const matchesOwner = owner === 'ALL' || task.owner === owner
    const matchesPriority = priority === 'ALL' || task.priority === priority
    const matchesTab = tab === 'all'
      || (tab === 'mine' && task.owner === 'Você' && task.status === 'OPEN')
      || (tab === 'completed' ? task.status === 'DONE' : task.bucket === tab && task.status === 'OPEN')
    return matchesSearch && matchesOwner && matchesPriority && matchesTab
  }), [owner, priority, search, tab, tasks])

  const selected = tasks.find((task) => task.id === selectedId)
  const openTasks = tasks.filter((task) => task.status === 'OPEN')
  const tabCount = (value) => tasks.filter((task) => value === 'all'
    || (value === 'mine' && task.owner === 'Você' && task.status === 'OPEN')
    || (value === 'completed' ? task.status === 'DONE' : task.bucket === value && task.status === 'OPEN')).length

  function showNotice(message) {
    setNotice(message)
    window.setTimeout(() => setNotice(''), 2800)
  }

  function toggleTask(id) {
    const completed = tasks.find((task) => task.id === id)?.status !== 'DONE'
    setTasks((current) => current.map((task) => {
      if (task.id !== id) return task
      return completed
        ? { ...task, status: 'DONE', previousBucket: task.bucket, bucket: 'completed', deadline: 'Concluída agora' }
        : { ...task, status: 'OPEN', bucket: task.previousBucket || 'today', deadline: task.previousBucket === 'overdue' ? 'Ontem, 16:00' : 'Hoje, 17:00' }
    }))
    showNotice(completed ? 'Tarefa concluída.' : 'Tarefa reaberta.')
  }

  function assignToMe() {
    if (!selected) return
    setTasks((current) => current.map((task) => task.id === selected.id ? { ...task, owner: 'Você' } : task))
    showNotice('Tarefa atribuída a você.')
  }

  function createTask(event) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    const patient = data.get('patient').trim()
    const title = data.get('title').trim()
    const initials = patient.split(/\s+/).slice(0, 2).map((part) => part[0]).join('').toUpperCase() || 'NT'
    const next = {
      id: `task-${Date.now()}`,
      title,
      patient,
      initials,
      category: data.get('category'),
      deadline: data.get('deadline') || 'Hoje, 17:00',
      bucket: 'today',
      priority: data.get('priority'),
      owner: data.get('owner'),
      status: 'OPEN',
      origin: 'Criada manualmente',
      notes: data.get('notes').trim() || 'Sem observações adicionais.',
    }
    setTasks((current) => [next, ...current])
    setSelectedId(next.id)
    setCreating(false)
    setTab('all')
    showNotice('Nova tarefa criada.')
  }

  return <main className="tasks-page" aria-labelledby="tasks-title">
    <div className="tasks-wrap">
      <header className="tasks-heading">
        <div><p>Pós-atendimento e retenção</p><h1 id="tasks-title">Tarefas</h1><span>Organize contatos, acompanhamentos e pendências da equipe.</span></div>
        <div className="tasks-heading-actions"><span className="tasks-demo-label">Dados demonstrativos</span><button type="button" className="tasks-primary-button" onClick={() => setCreating(true)}><Plus size={15} /> Nova tarefa</button></div>
      </header>

      <section className="tasks-kpis" aria-label="Resumo das tarefas">
        <Kpi label="Em aberto" value={openTasks.length} note="Todas as tarefas pendentes" icon={<ListTodo size={17} />} />
        <Kpi label="Vencidas" value={openTasks.filter((task) => task.bucket === 'overdue').length} note="Precisam de atenção imediata" tone="danger" icon={<AlertCircle size={17} />} />
        <Kpi label="Para hoje" value={openTasks.filter((task) => task.bucket === 'today').length} note="Prioridades do dia" tone="warning" icon={<Clock3 size={17} />} />
        <Kpi label="Concluídas" value={tasks.filter((task) => task.status === 'DONE').length} note="Nesta prévia operacional" tone="success" icon={<CheckCircle2 size={17} />} />
      </section>

      <div className={`tasks-content${selected ? ' has-detail' : ''}`}>
        <section className="tasks-card tasks-list" aria-label="Lista de tarefas">
          <nav className="tasks-tabs" aria-label="Filtros rápidos de tarefas">
            {TABS.map(([value, label]) => <button key={value} type="button" className={tab === value ? 'active' : ''} onClick={() => setTab(value)}>{label}<span>{tabCount(value)}</span></button>)}
          </nav>
          <div className="tasks-filter-bar">
            <label className="tasks-search"><Search size={15} aria-hidden="true" /><span className="tasks-sr-only">Buscar tarefas</span><input type="search" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Buscar tarefa, paciente ou origem" /></label>
            <label className="tasks-select"><span className="tasks-sr-only">Filtrar por responsável</span><select value={owner} onChange={(event) => setOwner(event.target.value)}><option value="ALL">Todos os responsáveis</option><option>Você</option><option>Carla Mendes</option><option>Rafael Lima</option></select></label>
            <label className="tasks-select"><span className="tasks-sr-only">Filtrar por prioridade</span><select value={priority} onChange={(event) => setPriority(event.target.value)}><option value="ALL">Todas as prioridades</option><option>Alta</option><option>Média</option><option>Baixa</option></select></label>
            <button type="button" className="tasks-filter-button" onClick={() => showNotice('Os filtros disponíveis já estão aplicados à lista.')}><Filter size={14} /> Filtros</button>
          </div>

          <div className="tasks-table-wrap">
            <table className="tasks-table"><thead><tr><th><span className="tasks-sr-only">Concluir</span></th><th>Tarefa / paciente</th><th>Prazo</th><th>Prioridade</th><th>Responsável</th><th>Origem</th></tr></thead>
              <tbody>{filtered.map((task) => <tr key={task.id} className={`${task.status === 'DONE' ? 'completed ' : ''}${selectedId === task.id ? 'selected' : ''}`}>
                <td className="tasks-check-cell"><button type="button" className="tasks-check" aria-label={task.status === 'DONE' ? `Reabrir ${task.title}` : `Concluir ${task.title}`} onClick={() => toggleTask(task.id)}>{task.status === 'DONE' && <Check size={13} />}</button></td>
                <td><div className="tasks-task"><span className="tasks-avatar">{task.initials}</span><div><button type="button" className="tasks-title-button" onClick={() => setSelectedId(task.id)}>{task.title}</button><span>{task.patient}</span><small>{task.category}</small></div></div></td>
                <td><strong className={task.bucket === 'overdue' ? 'tasks-overdue' : ''}>{task.deadline}</strong></td>
                <td><PriorityBadge priority={task.priority} /></td>
                <td><span className="tasks-owner"><User size={13} />{task.owner}</span></td>
                <td><span className="tasks-origin">{task.origin}</span></td>
              </tr>)}</tbody>
            </table>
            {filtered.length === 0 && <div className="tasks-empty"><CheckCircle2 size={28} /><strong>Nenhuma tarefa encontrada</strong><span>Ajuste os filtros ou crie uma tarefa para acompanhar uma próxima ação.</span><button type="button" className="tasks-secondary-button" onClick={() => { setSearch(''); setOwner('ALL'); setPriority('ALL'); setTab('all') }}>Limpar filtros</button></div>}
          </div>
          <footer className="tasks-list-footer"><span>Mostrando {filtered.length} de {tasks.length} tarefas demonstrativas</span><span>Atualizado agora</span></footer>
        </section>

        {selected && <TaskDetail task={selected} onClose={() => setSelectedId(null)} onToggle={() => toggleTask(selected.id)} onAssign={assignToMe} />}
      </div>
      <p className="tasks-data-note">Prévia do MVP com dados de exemplo. As alterações são mantidas apenas nesta sessão até a conexão com a API de tarefas.</p>
    </div>

    {creating && <TaskModal onClose={() => setCreating(false)} onSubmit={createTask} />}
    {notice && <div className="tasks-toast" role="status"><Check size={14} />{notice}</div>}
  </main>
}

function TaskDetail({ task, onClose, onToggle, onAssign }) {
  return <aside className="tasks-card tasks-detail" aria-label={`Detalhes da tarefa ${task.title}`}>
    <header><div><span className="tasks-detail-kicker">{task.status === 'DONE' ? 'Tarefa concluída' : 'Tarefa em aberto'}</span><h2>{task.title}</h2><span>{task.patient}</span></div><button type="button" className="tasks-close" aria-label="Fechar detalhes" onClick={onClose}><X size={17} /></button></header>
    <div className="tasks-detail-status"><PriorityBadge priority={task.priority} /><span className={`tasks-state ${task.status.toLowerCase()}`}><i />{task.status === 'DONE' ? 'Concluída' : 'Em aberto'}</span></div>
    <dl className="tasks-detail-data"><div><dt>Prazo</dt><dd><CalendarDays size={14} />{task.deadline}</dd></div><div><dt>Responsável</dt><dd>{task.owner}</dd></div><div><dt>Canal / tipo</dt><dd>{task.category}</dd></div><div><dt>Origem</dt><dd>{task.origin}</dd></div></dl>
    <section className="tasks-notes"><h3>Orientações</h3><p>{task.notes}</p></section>
    <div className="tasks-detail-actions"><button type="button" className="tasks-primary-button" onClick={onToggle}>{task.status === 'DONE' ? <><RotateCcw size={14} /> Reabrir tarefa</> : <><Check size={15} /> Marcar como concluída</>}</button>{task.owner !== 'Você' && task.status === 'OPEN' && <button type="button" className="tasks-secondary-button" onClick={onAssign}><UserPlus size={15} /> Atribuir a mim</button>}</div>
  </aside>
}

function TaskModal({ onClose, onSubmit }) {
  return <div className="tasks-modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose() }}><section className="tasks-modal" role="dialog" aria-modal="true" aria-labelledby="new-task-title"><header><div><span>Nova atividade</span><h2 id="new-task-title">Criar tarefa</h2></div><button type="button" className="tasks-close" aria-label="Fechar" onClick={onClose}><X size={18} /></button></header><form onSubmit={onSubmit}>
    <label><span>Título</span><input name="title" required maxLength="100" placeholder="Ex.: Confirmar retorno do paciente" /></label>
    <label><span>Paciente</span><input name="patient" required maxLength="80" placeholder="Nome do paciente" /></label>
    <div className="tasks-form-grid"><label><span>Prazo</span><input name="deadline" required placeholder="Hoje, 17:00" /></label><label><span>Responsável</span><select name="owner" defaultValue="Você"><option>Você</option><option>Carla Mendes</option><option>Rafael Lima</option></select></label></div>
    <div className="tasks-form-grid"><label><span>Prioridade</span><select name="priority" defaultValue="Média"><option>Alta</option><option>Média</option><option>Baixa</option></select></label><label><span>Canal / tipo</span><select name="category" defaultValue="Ligação"><option>Ligação</option><option>WhatsApp</option><option>Agenda</option><option>Administrativo</option></select></label></div>
    <label><span>Orientações</span><textarea name="notes" rows="3" maxLength="300" placeholder="Contexto para quem executar a tarefa" /></label>
    <footer><button type="button" className="tasks-secondary-button" onClick={onClose}>Cancelar</button><button type="submit" className="tasks-primary-button"><Plus size={15} /> Criar tarefa</button></footer>
  </form></section></div>
}

function Kpi({ label, value, note, tone = '', icon }) {
  return <article className={`tasks-kpi ${tone ? `tasks-kpi-${tone}` : ''}`}><div><span>{label}</span>{icon}</div><strong>{value}</strong><small>{note}</small></article>
}

function PriorityBadge({ priority }) {
  return <span className={`tasks-priority tasks-priority-${priority.toLowerCase()}`}><i />{priority}</span>
}
