import { useMemo, useState } from 'react'
import {
  CalendarDays, Check, ChevronDown, Clock3, Download, Filter,
  HeartPulse, ListChecks, MessageCircle, MoreHorizontal, Search,
  Sparkles, Tag, UserCheck, Users, X,
} from 'lucide-react'
import './Pacientes.css'

const RELATIONSHIP_LABELS = {
  active: 'Ativo',
  attention: 'Requer atenção',
  dormant: 'Inativo',
  new: 'Novo',
}

const DEMO_PATIENTS = [
  { id: 'pat-001', name: 'Maria Silva', initials: 'MS', phone: '(85) 99821-4321', email: 'maria.silva@email.com', lastVisit: 'Há 12 dias', lastDate: '22 set 2026', nextAction: 'Retorno hoje', nextTone: 'urgent', relationship: 'active', owner: 'Carla Mendes', journeys: ['Pós-limpeza'], tags: ['Recorrente', 'Particular'], appointments: 8, ticket: 290, total: 2320, lastProcedure: 'Limpeza preventiva', lastProfessional: 'Dra. Helena Costa', consent: true },
  { id: 'pat-002', name: 'João Almeida', initials: 'JA', phone: '(85) 99710-2048', email: 'joao.almeida@email.com', lastVisit: 'Há 35 dias', lastDate: '30 ago 2026', nextAction: 'Aguardando resposta', nextTone: 'waiting', relationship: 'attention', owner: 'Carla Mendes', journeys: ['Pós-Botox'], tags: ['Estética'], appointments: 3, ticket: 900, total: 2700, lastProcedure: 'Aplicação de toxina', lastProfessional: 'Dr. Marcos Lima', consent: true },
  { id: 'pat-003', name: 'Ana Costa', initials: 'AC', phone: '(85) 99142-7770', email: 'ana.costa@email.com', lastVisit: 'Há 7 meses', lastDate: '01 mar 2026', nextAction: 'Reativação vencida', nextTone: 'urgent', relationship: 'dormant', owner: 'Rafael Lima', journeys: ['Reativação 180 dias'], tags: ['Plano Essencial'], appointments: 5, ticket: 320, total: 1600, lastProcedure: 'Consulta de rotina', lastProfessional: 'Dra. Helena Costa', consent: true },
  { id: 'pat-004', name: 'Carlos Souza', initials: 'CS', phone: '(85) 99653-1102', email: 'carlos.souza@email.com', lastVisit: 'Há 18 dias', lastDate: '16 set 2026', nextAction: 'Agendado para 18 out', nextTone: 'success', relationship: 'active', owner: 'Carla Mendes', journeys: ['Retorno clínico'], tags: ['Alto valor', 'Particular'], appointments: 12, ticket: 650, total: 7800, lastProcedure: 'Avaliação clínica', lastProfessional: 'Dr. Marcos Lima', consent: true },
  { id: 'pat-005', name: 'Beatriz Rocha', initials: 'BR', phone: '(85) 99804-5681', email: 'beatriz.rocha@email.com', lastVisit: 'Há 3 dias', lastDate: '01 out 2026', nextAction: 'Manutenção hoje', nextTone: 'urgent', relationship: 'new', owner: 'Sem responsável', journeys: [], tags: ['Nova paciente'], appointments: 1, ticket: 480, total: 480, lastProcedure: 'Primeira avaliação', lastProfessional: 'Dra. Helena Costa', consent: true },
  { id: 'pat-006', name: 'Rafael Oliveira', initials: 'RO', phone: '(85) 99222-0901', email: 'rafael.oliveira@email.com', lastVisit: 'Há 9 meses', lastDate: '10 jan 2026', nextAction: 'Sem resposta há 8 dias', nextTone: 'waiting', relationship: 'dormant', owner: 'Rafael Lima', journeys: ['Reativação 180 dias'], tags: ['Não respondeu'], appointments: 4, ticket: 420, total: 1680, lastProcedure: 'Manutenção', lastProfessional: 'Dra. Helena Costa', consent: true },
  { id: 'pat-007', name: 'Juliana Martins', initials: 'JM', phone: '(85) 99931-4406', email: 'juliana.martins@email.com', lastVisit: 'Ontem', lastDate: '03 out 2026', nextAction: 'Acompanhamento em 5 dias', nextTone: 'default', relationship: 'active', owner: 'Carla Mendes', journeys: ['Pós-tratamento'], tags: ['Recorrente'], appointments: 7, ticket: 540, total: 3780, lastProcedure: 'Revisão de tratamento', lastProfessional: 'Dr. Marcos Lima', consent: true },
  { id: 'pat-008', name: 'Lucas Fernandes', initials: 'LF', phone: '(85) 99541-8830', email: 'lucas.fernandes@email.com', lastVisit: 'Há 92 dias', lastDate: '04 jul 2026', nextAction: 'Retorno em 7 dias', nextTone: 'default', relationship: 'attention', owner: 'Sem responsável', journeys: [], tags: ['Alto valor'], appointments: 6, ticket: 780, total: 4680, lastProcedure: 'Procedimento clínico', lastProfessional: 'Dra. Helena Costa', consent: false },
]

const SEGMENTS = [
  ['all', 'Todos', 1284],
  ['active', 'Ativos', 842],
  ['attention', 'Requerem atenção', 89],
  ['dormant', 'Inativos', 214],
  ['new', 'Novos', 139],
]

export default function PosVendaPacientes() {
  const [patients] = useState(DEMO_PATIENTS)
  const [search, setSearch] = useState('')
  const [segment, setSegment] = useState('all')
  const [owner, setOwner] = useState('all')
  const [journey, setJourney] = useState('all')
  const [selectedIds, setSelectedIds] = useState(new Set())
  const [selectedId, setSelectedId] = useState(null)
  const [notice, setNotice] = useState('')

  const filtered = useMemo(() => patients.filter((patient) => {
    const term = search.trim().toLocaleLowerCase('pt-BR')
    const matchesSearch = !term || [patient.name, patient.phone, patient.email, ...patient.tags]
      .some((field) => field.toLocaleLowerCase('pt-BR').includes(term))
    const matchesSegment = segment === 'all' || patient.relationship === segment
    const matchesOwner = owner === 'all' || patient.owner === owner
    const matchesJourney = journey === 'all' || (journey === 'none' ? patient.journeys.length === 0 : patient.journeys.includes(journey))
    return matchesSearch && matchesSegment && matchesOwner && matchesJourney
  }), [journey, owner, patients, search, segment])

  const selected = patients.find((patient) => patient.id === selectedId)
  const allVisibleSelected = filtered.length > 0 && filtered.every((patient) => selectedIds.has(patient.id))

  function toggleSelection(id) {
    setSelectedIds((current) => {
      const next = new Set(current)
      next.has(id) ? next.delete(id) : next.add(id)
      return next
    })
  }

  function toggleAllVisible() {
    setSelectedIds((current) => {
      const next = new Set(current)
      filtered.forEach((patient) => allVisibleSelected ? next.delete(patient.id) : next.add(patient.id))
      return next
    })
  }

  function showNotice(message) {
    setNotice(message)
    window.setTimeout(() => setNotice(''), 2800)
  }

  function clearFilters() {
    setSearch('')
    setSegment('all')
    setOwner('all')
    setJourney('all')
  }

  return <main className="pv-patients-page" aria-labelledby="pv-patients-title">
    <div className="pp-wrap">
      <header className="pp-heading">
        <div><p>Pós-atendimento e retenção</p><h1 id="pv-patients-title">Pacientes</h1><span>Entenda o relacionamento com cada paciente e organize o próximo passo.</span></div>
        <div className="pp-heading-actions"><span className="pp-demo-label">Dados demonstrativos</span><button type="button" className="pp-secondary-button" onClick={() => showNotice('A exportação será habilitada com a conexão da base de pacientes.')}><Download size={15} /> Exportar base</button></div>
      </header>

      <section className="pp-kpis" aria-label="Resumo da base de pacientes">
        <Kpi label="Base de pacientes" value="1.284" note="Pacientes identificados na clínica" icon={<Users size={17} />} />
        <Kpi label="Relacionamento ativo" value="842" note="65,6% receberam atenção em até 90 dias" tone="teal" icon={<UserCheck size={17} />} />
        <Kpi label="Requerem atenção" value="89" note="Sem retorno ou contato no prazo esperado" tone="warning" icon={<HeartPulse size={17} />} />
        <Kpi label="Sem jornada ativa" value="386" note="Podem entrar em uma régua de relacionamento" icon={<Sparkles size={17} />} />
      </section>

      <div className={`pp-content${selected ? ' has-detail' : ''}`}>
        <section className="pp-card pp-list" aria-label="Base de pacientes de pós-venda">
          <nav className="pp-tabs" aria-label="Segmentos rápidos">
            {SEGMENTS.map(([value, label, count]) => <button type="button" key={value} className={segment === value ? 'active' : ''} onClick={() => setSegment(value)}>{label}<span>{count}</span></button>)}
          </nav>

          <div className="pp-filter-bar">
            <label className="pp-search"><Search size={16} aria-hidden="true" /><span className="pp-sr-only">Buscar paciente</span><input type="search" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Buscar por nome, telefone, e-mail ou tag" /></label>
            <label className="pp-select"><span className="pp-sr-only">Filtrar por responsável</span><select value={owner} onChange={(event) => setOwner(event.target.value)}><option value="all">Todos os responsáveis</option><option>Carla Mendes</option><option>Rafael Lima</option><option>Sem responsável</option></select><ChevronDown size={13} /></label>
            <label className="pp-select"><span className="pp-sr-only">Filtrar por jornada</span><select value={journey} onChange={(event) => setJourney(event.target.value)}><option value="all">Todas as jornadas</option><option value="none">Sem jornada</option><option>Pós-limpeza</option><option>Pós-Botox</option><option>Retorno clínico</option><option>Reativação 180 dias</option><option>Pós-tratamento</option></select><ChevronDown size={13} /></label>
            <button type="button" className="pp-filter-button" onClick={() => showNotice('Os filtros avançados farão parte da conexão com a base clínica.')}><Filter size={15} /> Mais filtros</button>
          </div>

          {selectedIds.size > 0 && <div className="pp-bulk-bar"><strong>{selectedIds.size} paciente{selectedIds.size > 1 ? 's' : ''} selecionado{selectedIds.size > 1 ? 's' : ''}</strong><button type="button" onClick={() => showNotice('Pacientes preparados para inclusão em uma jornada.')}><Sparkles size={14} /> Adicionar à jornada</button><button type="button" onClick={() => showNotice('Tarefa criada para os pacientes selecionados.')}><ListChecks size={14} /> Criar tarefa</button><button type="button" onClick={() => showNotice('Tag aplicada aos pacientes selecionados.')}><Tag size={14} /> Aplicar tag</button><button type="button" className="pp-bulk-close" aria-label="Limpar seleção" onClick={() => setSelectedIds(new Set())}><X size={15} /></button></div>}

          <div className="pp-table-wrap">
            <table className="pp-table"><thead><tr><th className="pp-check-cell"><input type="checkbox" checked={allVisibleSelected} onChange={toggleAllVisible} aria-label="Selecionar todos os pacientes visíveis" /></th><th>Paciente</th><th>Último atendimento</th><th>Relacionamento</th><th>Próximo passo</th><th>Responsável</th><th><span className="pp-sr-only">Ações</span></th></tr></thead>
              <tbody>{filtered.map((patient) => <tr key={patient.id} className={selectedId === patient.id ? 'selected' : ''}>
                <td className="pp-check-cell"><input type="checkbox" checked={selectedIds.has(patient.id)} onChange={() => toggleSelection(patient.id)} aria-label={`Selecionar ${patient.name}`} /></td>
                <td><div className="pp-patient"><span className="pp-avatar">{patient.initials}</span><div><button type="button" className="pp-patient-name" onClick={() => setSelectedId(patient.id)}>{patient.name}</button><span>{patient.phone}</span><small>{patient.tags.join(' · ')}</small></div></div></td>
                <td><strong>{patient.lastVisit}</strong><span className="pp-cell-sub">{patient.lastProcedure}</span></td>
                <td><RelationshipBadge value={patient.relationship} /><span className="pp-cell-sub">{patient.journeys[0] || 'Sem jornada ativa'}</span></td>
                <td><strong className={`pp-next pp-next-${patient.nextTone}`}>{patient.nextAction}</strong><span className="pp-cell-sub">{patient.consent ? 'WhatsApp autorizado' : 'Sem opt-in de WhatsApp'}</span></td>
                <td><strong>{patient.owner}</strong></td>
                <td><button type="button" className="pp-more" aria-label={`Ver detalhes de ${patient.name}`} onClick={() => setSelectedId(patient.id)}><MoreHorizontal size={17} /></button></td>
              </tr>)}</tbody>
            </table>
            {filtered.length === 0 && <div className="pp-empty"><Users size={28} /><strong>Nenhum paciente encontrado</strong><span>Revise a busca ou remova os filtros para consultar toda a base.</span><button type="button" className="pp-secondary-button" onClick={clearFilters}>Limpar filtros</button></div>}
          </div>
          <footer className="pp-list-footer"><span>Mostrando {filtered.length} de 1.284 pacientes</span><div><button type="button" disabled>Anterior</button><b>1</b><button type="button">2</button><button type="button">3</button><span>…</span><button type="button">Próxima</button></div></footer>
        </section>

        {selected && <PatientDetail patient={selected} onClose={() => setSelectedId(null)} onNotice={showNotice} />}
      </div>
      <p className="pp-data-note">Prévia do MVP com dados de exemplo. A visão reúne dados de cadastro, atendimentos e relacionamento; mensagens e jornadas só serão executadas após a conexão da base.</p>
    </div>
    {notice && <div className="pp-toast" role="status"><Check size={14} />{notice}</div>}
  </main>
}

function PatientDetail({ patient, onClose, onNotice }) {
  return <aside className="pp-card pp-detail" aria-label={`Resumo de relacionamento de ${patient.name}`}>
    <header><div className="pp-detail-person"><span className="pp-avatar large">{patient.initials}</span><div><span className="pp-detail-kicker">Paciente</span><h2>{patient.name}</h2><span>{patient.phone}</span></div></div><button type="button" className="pp-close" aria-label="Fechar detalhes" onClick={onClose}><X size={17} /></button></header>
    <div className="pp-detail-status"><RelationshipBadge value={patient.relationship} />{patient.tags.map((tag) => <span className="pp-tag" key={tag}>{tag}</span>)}</div>
    <section className="pp-detail-section"><h3>Visão do relacionamento</h3><div className="pp-stats"><div><strong>{patient.appointments}</strong><span>atendimentos</span></div><div><strong>{formatCurrency(patient.ticket)}</strong><span>ticket médio</span></div><div><strong>{formatCurrency(patient.total)}</strong><span>valor histórico</span></div></div></section>
    <section className="pp-detail-section"><h3>Próximo passo</h3><div className="pp-next-card"><Clock3 size={16} /><div><strong>{patient.nextAction}</strong><span>{patient.journeys[0] || 'Nenhuma jornada ativa'}</span></div></div></section>
    <section className="pp-detail-section"><h3>Último atendimento</h3><dl className="pp-detail-data"><div><dt>Data</dt><dd>{patient.lastDate}</dd></div><div><dt>Procedimento</dt><dd>{patient.lastProcedure}</dd></div><div><dt>Profissional</dt><dd>{patient.lastProfessional}</dd></div><div><dt>Responsável</dt><dd>{patient.owner}</dd></div></dl></section>
    <div className="pp-detail-actions"><button type="button" className="pp-primary-button" disabled={!patient.consent} title={patient.consent ? '' : 'Paciente sem autorização para WhatsApp'} onClick={() => onNotice(`Abrindo conversa com ${patient.name}.`)}><MessageCircle size={15} /> Enviar mensagem</button><button type="button" className="pp-secondary-button" onClick={() => onNotice(`Jornada preparada para ${patient.name}.`)}><Sparkles size={15} /> Adicionar à jornada</button><button type="button" className="pp-secondary-button" onClick={() => onNotice(`Tarefa criada para ${patient.name}.`)}><ListChecks size={15} /> Criar tarefa</button></div>
    <button type="button" className="pp-profile-link" onClick={() => onNotice('O perfil clínico completo permanece no módulo principal de Pacientes.')}><CalendarDays size={14} /> Ver perfil completo no ClinicOS</button>
  </aside>
}

function Kpi({ label, value, note, tone = '', icon }) {
  return <article className={`pp-kpi ${tone ? `pp-kpi-${tone}` : ''}`}><div className="pp-kpi-label"><span>{label}</span>{icon}</div><strong>{value}</strong><small>{note}</small></article>
}

function RelationshipBadge({ value }) {
  return <span className={`pp-relationship pp-relationship-${value}`}><i aria-hidden="true" />{RELATIONSHIP_LABELS[value]}</span>
}

function formatCurrency(value) {
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL', maximumFractionDigits: 0 }).format(value)
}
