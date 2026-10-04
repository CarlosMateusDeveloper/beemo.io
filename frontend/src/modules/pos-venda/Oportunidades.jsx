import { useMemo, useState } from 'react'
import {
  ArrowUpRight, CalendarDays, Filter, ListChecks, MessageCircle,
  MoreHorizontal, Play, Plus, Search, SlidersHorizontal, Target, Users, X, Zap,
} from 'lucide-react'
import './Oportunidades.css'

const STATUS_LABELS = {
  OPEN: 'Aberta',
  CONTACTED: 'Contatada',
  RESPONDED: 'Respondeu',
  SCHEDULED: 'Agendada',
  ATTENDED: 'Compareceu',
  CONVERTED: 'Convertida',
  LOST: 'Perdida',
  CANCELED: 'Cancelada',
}

const TYPE_LABELS = {
  RETURN: 'Retorno',
  REACTIVATION: 'Reativação',
  FOLLOW_UP: 'Pós-atendimento',
}

const DEMO_OPPORTUNITIES = [
  { id: 'opp-001', patient: 'Maria Silva', initials: 'MS', phone: '(85) 99821-4321', type: 'RETURN', title: 'Limpeza preventiva', expected: 'Hoje', window: 'today', value: 250, status: 'OPEN', reason: 'Retorno previsto', owner: 'Sem responsável', journey: 'Pós-limpeza', priority: 'Alta', noResponse: false, lastContact: 'Ainda não contatada' },
  { id: 'opp-002', patient: 'João Almeida', initials: 'JA', phone: '(85) 99710-2048', type: 'FOLLOW_UP', title: 'Acompanhamento pós-procedimento', expected: '+5 dias', window: 'next7', value: 900, status: 'CONTACTED', reason: 'Acompanhamento pendente', owner: 'Carla Mendes', journey: 'Pós-Botox', priority: 'Média', noResponse: true, lastContact: 'Mensagem enviada há 2 dias' },
  { id: 'opp-003', patient: 'Ana Costa', initials: 'AC', phone: '(85) 99142-7770', type: 'REACTIVATION', title: 'Consulta de reativação', expected: '−12 dias', window: 'overdue', value: 300, status: 'RESPONDED', reason: 'Paciente inativa há 180 dias', owner: 'Rafael Lima', journey: 'Reativação 180 dias', priority: 'Alta', noResponse: false, lastContact: 'Respondeu há 3 horas' },
  { id: 'opp-004', patient: 'Carlos Souza', initials: 'CS', phone: '(85) 99653-1102', type: 'RETURN', title: 'Retorno de avaliação', expected: '18 out', window: 'next7', value: 650, status: 'SCHEDULED', reason: 'Retorno agendado', owner: 'Carla Mendes', journey: 'Retorno clínico', priority: 'Baixa', noResponse: false, lastContact: 'Agendamento confirmado' },
  { id: 'opp-005', patient: 'Beatriz Rocha', initials: 'BR', phone: '(85) 99804-5681', type: 'RETURN', title: 'Manutenção de tratamento', expected: 'Hoje', window: 'today', value: 480, status: 'OPEN', reason: 'Data de manutenção atingida', owner: 'Sem responsável', journey: 'Manutenção', priority: 'Média', noResponse: false, lastContact: 'Ainda não contatada' },
  { id: 'opp-006', patient: 'Rafael Oliveira', initials: 'RO', phone: '(85) 99222-0901', type: 'REACTIVATION', title: 'Paciente inativo', expected: '−30 dias', window: 'overdue', value: 420, status: 'LOST', reason: 'Sem resposta após 3 tentativas', owner: 'Rafael Lima', journey: 'Reativação 180 dias', priority: 'Baixa', noResponse: true, lastContact: 'Última tentativa há 8 dias' },
  { id: 'opp-007', patient: 'Juliana Martins', initials: 'JM', phone: '(85) 99931-4406', type: 'FOLLOW_UP', title: 'Revisão de tratamento', expected: '20 out', window: 'next7', value: 540, status: 'ATTENDED', reason: 'Comparecimento registrado', owner: 'Carla Mendes', journey: 'Pós-tratamento', priority: 'Baixa', noResponse: false, lastContact: 'Atendimento realizado' },
  { id: 'opp-008', patient: 'Lucas Fernandes', initials: 'LF', phone: '(85) 99541-8830', type: 'RETURN', title: 'Retorno de procedimento', expected: '+7 dias', window: 'next7', value: 780, status: 'OPEN', reason: 'Retorno previsto', owner: 'Sem responsável', journey: 'Retorno clínico', priority: 'Alta', noResponse: false, lastContact: 'Ainda não contatada' },
]

const TABS = [
  ['all', 'Todas', 171],
  ['overdue', 'Vencidas', 28],
  ['today', 'Hoje', 42],
  ['next7', 'Próximos 7 dias', 67],
  ['noResponse', 'Sem resposta', 34],
]

export default function Oportunidades() {
  const [opportunities, setOpportunities] = useState(DEMO_OPPORTUNITIES)
  const [search, setSearch] = useState('')
  const [tab, setTab] = useState('all')
  const [status, setStatus] = useState('ALL')
  const [type, setType] = useState('ALL')
  const [selectedIds, setSelectedIds] = useState(new Set())
  const [selectedId, setSelectedId] = useState(null)
  const [notice, setNotice] = useState('')

  const filtered = useMemo(() => opportunities.filter((item) => {
    const term = search.trim().toLowerCase()
    const matchesSearch = !term || [item.patient, item.title, item.phone, item.journey].some((field) => field.toLowerCase().includes(term))
    const matchesTab = tab === 'all' || (tab === 'noResponse' ? item.noResponse : item.window === tab)
    const matchesStatus = status === 'ALL' || item.status === status
    const matchesType = type === 'ALL' || item.type === type
    return matchesSearch && matchesTab && matchesStatus && matchesType
  }), [opportunities, search, status, tab, type])

  const selected = opportunities.find((item) => item.id === selectedId)
  const allVisibleSelected = filtered.length > 0 && filtered.every((item) => selectedIds.has(item.id))

  function toggleSelection(id) {
    setSelectedIds((current) => {
      const next = new Set(current)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  function toggleAllVisible() {
    setSelectedIds((current) => {
      const next = new Set(current)
      if (allVisibleSelected) filtered.forEach((item) => next.delete(item.id))
      else filtered.forEach((item) => next.add(item.id))
      return next
    })
  }

  function showNotice(message) {
    setNotice(message)
    window.setTimeout(() => setNotice(''), 2800)
  }

  function startJourney() {
    if (!selected) return
    setOpportunities((current) => current.map((item) => item.id === selected.id ? { ...item, status: 'CONTACTED', journey: item.journey || 'Jornada de retorno' } : item))
    showNotice(`Jornada iniciada para ${selected.patient}.`)
  }

  function createTask() {
    if (!selected) return
    showNotice(`Tarefa criada para ${selected.patient}.`)
  }

  function markLost() {
    if (!selected) return
    setOpportunities((current) => current.map((item) => item.id === selected.id ? { ...item, status: 'LOST', reason: 'Encerrada manualmente' } : item))
    showNotice('Oportunidade marcada como perdida.')
  }

  return <main className="opportunities-page" aria-labelledby="opportunities-title">
    <div className="opportunities-wrap">
      <header className="opportunities-heading">
        <div><p className="op-eyebrow">Pós-atendimento e retenção</p><h1 id="opportunities-title">Oportunidades</h1><span className="op-subtitle">Encontre quem deveria voltar e organize a próxima ação.</span></div>
        <div className="op-heading-actions"><span className="op-demo-label">Dados demonstrativos</span><button type="button" className="op-primary-button" onClick={() => showNotice('O cadastro de oportunidades será conectado aos dados da clínica.')}><Plus size={15} /> Nova oportunidade</button></div>
      </header>

      <section className="op-kpis" aria-label="Resumo das oportunidades">
        <Kpi label="Oportunidades abertas" value="171" note="28 vencidas precisam de atenção" tone="teal" icon={<Target size={17} />} />
        <Kpi label="Receita potencial" value="R$ 29.680" note="Valor estimado da base aberta" tone="teal" icon={<ArrowUpRight size={17} />} />
        <Kpi label="Em contato" value="67" note="34 aguardando resposta" icon={<MessageCircle size={17} />} />
        <Kpi label="Sem responsável" value="42" note="Prontas para distribuição" tone="warning" icon={<Users size={17} />} />
      </section>

      <div className={`op-content${selected ? ' has-detail' : ''}`}>
        <section className="op-card op-list" aria-label="Lista de oportunidades">
          <nav className="op-tabs" aria-label="Filtros rápidos">
            {TABS.map(([value, label, count]) => <button key={value} type="button" className={tab === value ? 'active' : ''} onClick={() => setTab(value)}>{label}<span>{count}</span></button>)}
          </nav>
          <div className="op-filter-bar">
            <label className="op-search"><Search size={16} aria-hidden="true" /><span className="op-sr-only">Buscar oportunidades</span><input type="search" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Buscar paciente, procedimento ou jornada" /></label>
            <label className="op-select"><span className="op-sr-only">Filtrar por status</span><select value={status} onChange={(event) => setStatus(event.target.value)}><option value="ALL">Todos os status</option>{Object.entries(STATUS_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
            <label className="op-select"><span className="op-sr-only">Filtrar por tipo</span><select value={type} onChange={(event) => setType(event.target.value)}><option value="ALL">Todos os tipos</option>{Object.entries(TYPE_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
            <button type="button" className="op-filter-button" onClick={() => showNotice('Filtros avançados estarão disponíveis após a conexão da base de dados.')}><Filter size={15} /> Filtros <span className="op-filter-count">2</span></button>
          </div>

          {selectedIds.size > 0 && <div className="op-bulk-bar"><strong>{selectedIds.size} selecionada{selectedIds.size > 1 ? 's' : ''}</strong><button type="button" onClick={() => showNotice('Jornada em lote preparada para as oportunidades selecionadas.') }><Play size={14} /> Iniciar jornada</button><button type="button" onClick={() => showNotice('Campanha em lote preparada para as oportunidades selecionadas.') }><Zap size={14} /> Adicionar à campanha</button><button type="button" onClick={() => showNotice('Tarefas em lote preparadas para as oportunidades selecionadas.') }><ListChecks size={14} /> Criar tarefas</button><button type="button" className="op-bulk-close" aria-label="Limpar seleção" onClick={() => setSelectedIds(new Set())}><X size={15} /></button></div>}

          <div className="op-table-wrap">
            <table className="op-table"><thead><tr><th className="op-check-cell"><input type="checkbox" checked={allVisibleSelected} onChange={toggleAllVisible} aria-label="Selecionar todas as oportunidades visíveis" /></th><th>Paciente / oportunidade</th><th>Retorno esperado</th><th>Valor estimado</th><th>Status</th><th>Responsável / próxima ação</th><th><span className="op-sr-only">Ações</span></th></tr></thead>
              <tbody>{filtered.map((item) => <tr key={item.id} className={selectedId === item.id ? 'selected' : ''}>
                <td className="op-check-cell"><input type="checkbox" checked={selectedIds.has(item.id)} onChange={() => toggleSelection(item.id)} aria-label={`Selecionar ${item.patient}`} /></td>
                <td><div className="op-patient"><span className="op-avatar">{item.initials}</span><div><button type="button" className="op-patient-name" onClick={() => setSelectedId(item.id)}>{item.patient}</button><span>{item.title}</span><small>{TYPE_LABELS[item.type]} · {item.reason}</small></div></div></td>
                <td><strong className={item.window === 'overdue' ? 'op-overdue' : ''}>{item.expected}</strong><span className="op-cell-sub">{item.journey}</span></td>
                <td><strong className="op-money">{formatCurrency(item.value)}</strong><span className="op-cell-sub">potencial</span></td>
                <td><StatusBadge status={item.status} /></td>
                <td><strong>{item.owner}</strong><span className="op-cell-sub">{item.lastContact}</span></td>
                <td><button type="button" className="op-more" aria-label={`Mais ações para ${item.patient}`} onClick={() => setSelectedId(item.id)}><MoreHorizontal size={17} /></button></td>
              </tr>)}</tbody>
            </table>
            {filtered.length === 0 && <div className="op-empty"><Target size={26} /><strong>Nenhuma oportunidade encontrada</strong><span>Ajuste os filtros ou importe pacientes e atendimentos para identificar novas oportunidades.</span><button type="button" className="op-secondary-button" onClick={() => { setSearch(''); setStatus('ALL'); setType('ALL'); setTab('all') }}>Limpar filtros</button></div>}
          </div>
          <footer className="op-list-footer"><span>Mostrando {filtered.length} oportunidades demonstrativas</span><div><button type="button" disabled>Anterior</button><b>1</b><button type="button" disabled>Próxima</button></div></footer>
        </section>

        {selected && <aside className="op-card op-detail" aria-label={`Detalhes da oportunidade de ${selected.patient}`}><header><div><span className="op-detail-kicker">Oportunidade</span><h2>{selected.patient}</h2><span>{selected.phone}</span></div><button type="button" className="op-close" aria-label="Fechar detalhes" onClick={() => setSelectedId(null)}><X size={17} /></button></header><div className="op-detail-status"><StatusBadge status={selected.status} /><span className={`op-priority ${selected.priority.toLowerCase()}`}>{selected.priority} prioridade</span></div><dl className="op-detail-data"><div><dt>Oportunidade</dt><dd>{selected.title}</dd></div><div><dt>Motivo</dt><dd>{selected.reason}</dd></div><div><dt>Retorno esperado</dt><dd><CalendarDays size={14} />{selected.expected}</dd></div><div><dt>Valor potencial</dt><dd className="op-money">{formatCurrency(selected.value)}</dd></div><div><dt>Jornada</dt><dd>{selected.journey}</dd></div><div><dt>Responsável</dt><dd>{selected.owner}</dd></div></dl><div className="op-detail-actions"><button type="button" className="op-primary-button" onClick={startJourney}><Play size={15} /> Iniciar jornada</button><button type="button" className="op-secondary-button" onClick={createTask}><ListChecks size={15} /> Criar tarefa</button><button type="button" className="op-secondary-button" onClick={() => showNotice(`Abrindo conversa com ${selected.patient}.`)}><MessageCircle size={15} /> Enviar mensagem</button><button type="button" className="op-text-button" onClick={markLost}>Marcar como perdida</button></div><p className="op-detail-note"><SlidersHorizontal size={14} /> A oportunidade só deve ser considerada receita recuperada após o comparecimento registrado.</p></aside>}
      </div>
      <p className="op-data-note">Prévia com valores de exemplo. Uma oportunidade representa uma possibilidade concreta de trazer um paciente de volta; a comunicação é uma ação para convertê-la.</p>
    </div>
    {notice && <div className="op-toast" role="status">{notice}</div>}
  </main>
}

function Kpi({ label, value, note, tone = '', icon }) {
  return <article className={`op-kpi ${tone ? `op-kpi-${tone}` : ''}`}><div className="op-kpi-label"><span>{label}</span>{icon}</div><strong>{value}</strong><small>{note}</small></article>
}

function StatusBadge({ status }) {
  return <span className={`op-status op-status-${status.toLowerCase()}`}><i aria-hidden="true" />{STATUS_LABELS[status]}</span>
}

function formatCurrency(value) {
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL', maximumFractionDigits: 0 }).format(value)
}
