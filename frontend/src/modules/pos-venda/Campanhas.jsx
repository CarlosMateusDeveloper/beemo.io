import { useMemo, useState } from 'react'
import {
  CalendarDays, Check, CheckCircle2, Filter, MessageCircle, Pause,
  Play, Plus, Search, Send, Target, X, Zap,
} from 'lucide-react'
import './Campanhas.css'

const STATUS_LABELS = { DRAFT: 'Rascunho', SCHEDULED: 'Agendada', RUNNING: 'Em andamento', PAUSED: 'Pausada', COMPLETED: 'Concluída' }
const CHANNEL_LABELS = { WHATSAPP: 'WhatsApp', EMAIL: 'E-mail', MULTI: 'Multicanal' }

const DEMO_CAMPAIGNS = [
  { id: 'campaign-001', name: 'Retorno preventivo de outubro', objective: 'Agendar retorno', channel: 'WHATSAPP', status: 'RUNNING', audience: 'Pacientes com retorno previsto no mês', recipients: 184, sent: 132, delivered: 126, responses: 38, conversions: 17, owner: 'Carla Mendes', schedule: 'Iniciada hoje, 08:00', message: 'Olá, {{primeiro_nome}}! Está na hora de cuidar novamente do seu sorriso. Posso ajudar a encontrar o melhor horário para seu retorno?', updated: 'Há 12 min' },
  { id: 'campaign-002', name: 'Reativação de pacientes inativos', objective: 'Reativar pacientes', channel: 'WHATSAPP', status: 'SCHEDULED', audience: 'Sem atendimento há mais de 180 dias', recipients: 96, sent: 0, delivered: 0, responses: 0, conversions: 0, owner: 'Rafael Lima', schedule: '07 out, 09:00', message: 'Oi, {{primeiro_nome}}! Sentimos sua falta. Que tal reservar um momento para atualizar seu acompanhamento?', updated: 'Ontem, 16:42' },
  { id: 'campaign-003', name: 'Acompanhamento pós-procedimento', objective: 'Acompanhar recuperação', channel: 'MULTI', status: 'PAUSED', audience: 'Procedimentos realizados nos últimos 7 dias', recipients: 42, sent: 28, delivered: 26, responses: 14, conversions: 4, owner: 'Você', schedule: 'Pausada para revisão', message: 'Olá, {{primeiro_nome}}. Como você está se sentindo após o procedimento? Responda esta mensagem se precisar falar com nossa equipe.', updated: '02 out, 15:10' },
  { id: 'campaign-004', name: 'Confirmação de agenda da semana', objective: 'Reduzir faltas', channel: 'WHATSAPP', status: 'COMPLETED', audience: 'Consultas agendadas para esta semana', recipients: 78, sent: 78, delivered: 76, responses: 61, conversions: 58, owner: 'Carla Mendes', schedule: 'Concluída em 03 out', message: 'Olá, {{primeiro_nome}}! Podemos confirmar seu atendimento em {{data_consulta}} às {{hora_consulta}}?', updated: '03 out, 18:00' },
  { id: 'campaign-005', name: 'Novidades da clínica', objective: 'Relacionamento', channel: 'EMAIL', status: 'DRAFT', audience: 'Pacientes ativos com e-mail', recipients: 0, sent: 0, delivered: 0, responses: 0, conversions: 0, owner: 'Você', schedule: 'Ainda não agendada', message: 'Olá, {{primeiro_nome}}! Preparamos novidades para tornar sua experiência ainda melhor.', updated: '30 set, 10:25' },
  { id: 'campaign-006', name: 'Avaliação de satisfação', objective: 'Coletar feedback', channel: 'WHATSAPP', status: 'COMPLETED', audience: 'Atendimentos concluídos em setembro', recipients: 214, sent: 214, delivered: 207, responses: 89, conversions: 0, owner: 'Rafael Lima', schedule: 'Concluída em 30 set', message: 'Sua opinião é muito importante, {{primeiro_nome}}. Como foi sua experiência com a nossa equipe?', updated: '30 set, 19:30' },
]

const TABS = [['all', 'Todas'], ['RUNNING', 'Em andamento'], ['SCHEDULED', 'Agendadas'], ['DRAFT', 'Rascunhos'], ['COMPLETED', 'Concluídas']]

export default function Campanhas() {
  const [campaigns, setCampaigns] = useState(DEMO_CAMPAIGNS)
  const [tab, setTab] = useState('all')
  const [search, setSearch] = useState('')
  const [channel, setChannel] = useState('ALL')
  const [selectedId, setSelectedId] = useState(null)
  const [creating, setCreating] = useState(false)
  const [notice, setNotice] = useState('')

  const filtered = useMemo(() => campaigns.filter((campaign) => {
    const term = search.trim().toLowerCase()
    const matchesSearch = !term || [campaign.name, campaign.objective, campaign.audience, campaign.owner].some((field) => field.toLowerCase().includes(term))
    return (tab === 'all' || campaign.status === tab) && (channel === 'ALL' || campaign.channel === channel) && matchesSearch
  }), [campaigns, channel, search, tab])

  const selected = campaigns.find((campaign) => campaign.id === selectedId)
  const sent = campaigns.reduce((total, campaign) => total + campaign.sent, 0)
  const delivered = campaigns.reduce((total, campaign) => total + campaign.delivered, 0)
  const responses = campaigns.reduce((total, campaign) => total + campaign.responses, 0)

  function showNotice(message) {
    setNotice(message)
    window.setTimeout(() => setNotice(''), 2800)
  }

  function changeStatus(id, status) {
    setCampaigns((current) => current.map((campaign) => campaign.id === id ? { ...campaign, status, updated: 'Agora', schedule: status === 'RUNNING' ? 'Iniciada agora' : 'Pausada manualmente' } : campaign))
    showNotice(status === 'RUNNING' ? 'Campanha iniciada em modo demonstrativo.' : 'Campanha pausada.')
  }

  function createCampaign(event) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    const draft = {
      id: `campaign-${Date.now()}`, name: data.get('name').trim(), objective: data.get('objective').trim(),
      channel: data.get('channel'), status: 'DRAFT', audience: data.get('audience').trim(),
      recipients: 0, sent: 0, delivered: 0, responses: 0, conversions: 0,
      owner: data.get('owner'), schedule: 'Ainda não agendada', message: data.get('message').trim(), updated: 'Agora',
    }
    setCampaigns((current) => [draft, ...current])
    setSelectedId(draft.id)
    setTab('all')
    setCreating(false)
    showNotice('Campanha salva como rascunho.')
  }

  return <main className="campaigns-page" aria-labelledby="campaigns-title">
    <div className="campaigns-wrap">
      <header className="campaigns-heading"><div><p>Pós-atendimento e retenção</p><h1 id="campaigns-title">Campanhas</h1><span>Planeje comunicações segmentadas e acompanhe os resultados.</span></div><div className="campaigns-heading-actions"><span className="campaigns-demo-label">Dados demonstrativos</span><button type="button" className="campaigns-primary-button" onClick={() => setCreating(true)}><Plus size={15} /> Nova campanha</button></div></header>

      <section className="campaigns-kpis" aria-label="Resumo das campanhas">
        <Kpi label="Campanhas ativas" value={campaigns.filter((item) => item.status === 'RUNNING').length} note={`${campaigns.filter((item) => item.status === 'SCHEDULED').length} agendada para os próximos dias`} icon={<Zap size={17} />} />
        <Kpi label="Mensagens enviadas" value={sent} note={`${delivered} entregues nesta prévia`} tone="teal" icon={<Send size={17} />} />
        <Kpi label="Taxa de entrega" value={`${sent ? Math.round(delivered / sent * 100) : 0}%`} note="Mensagens aceitas pelo canal" tone="success" icon={<CheckCircle2 size={17} />} />
        <Kpi label="Taxa de resposta" value={`${delivered ? Math.round(responses / delivered * 100) : 0}%`} note={`${responses} respostas recebidas`} tone="teal" icon={<MessageCircle size={17} />} />
      </section>

      <div className={`campaigns-content${selected ? ' has-detail' : ''}`}>
        <section className="campaigns-card campaigns-list" aria-label="Lista de campanhas">
          <nav className="campaigns-tabs" aria-label="Status das campanhas">{TABS.map(([value, label]) => <button type="button" key={value} className={tab === value ? 'active' : ''} onClick={() => setTab(value)}>{label}<span>{value === 'all' ? campaigns.length : campaigns.filter((item) => item.status === value).length}</span></button>)}</nav>
          <div className="campaigns-filter-bar"><label className="campaigns-search"><Search size={15} aria-hidden="true" /><span className="campaigns-sr-only">Buscar campanhas</span><input type="search" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Buscar campanha, público ou responsável" /></label><label className="campaigns-select"><span className="campaigns-sr-only">Filtrar por canal</span><select value={channel} onChange={(event) => setChannel(event.target.value)}><option value="ALL">Todos os canais</option>{Object.entries(CHANNEL_LABELS).map(([value, label]) => <option value={value} key={value}>{label}</option>)}</select></label><button type="button" className="campaigns-filter-button" onClick={() => showNotice('Os filtros disponíveis já estão aplicados à lista.')}><Filter size={14} /> Filtros</button></div>

          <div className="campaigns-table-wrap"><table className="campaigns-table"><thead><tr><th>Campanha</th><th>Status</th><th>Público</th><th>Desempenho</th><th>Conversões</th><th>Responsável</th></tr></thead><tbody>{filtered.map((campaign) => <tr key={campaign.id} className={selectedId === campaign.id ? 'selected' : ''}><td><div className="campaign-main"><span className={`campaign-channel campaign-channel-${campaign.channel.toLowerCase()}`}>{campaign.channel === 'EMAIL' ? '@' : <MessageCircle size={13} />}</span><div><button type="button" onClick={() => setSelectedId(campaign.id)}>{campaign.name}</button><span>{campaign.objective}</span><small>{campaign.schedule}</small></div></div></td><td><StatusBadge status={campaign.status} /></td><td><strong>{campaign.recipients || '—'}</strong><span className="campaign-cell-sub">{campaign.audience}</span></td><td><div className="campaign-delivery"><span><b>{campaign.delivered}</b> entregues</span><strong>{campaign.sent ? Math.round(campaign.delivered / campaign.sent * 100) : 0}%</strong><div><i style={{ width: `${campaign.sent ? campaign.delivered / campaign.sent * 100 : 0}%` }} /></div></div></td><td><strong className="campaign-conversion">{campaign.conversions}</strong><span className="campaign-cell-sub">{campaign.responses} respostas</span></td><td><strong>{campaign.owner}</strong><span className="campaign-cell-sub">{campaign.updated}</span></td></tr>)}</tbody></table></div>
          {filtered.length === 0 && <div className="campaigns-empty"><Send size={27} /><strong>Nenhuma campanha encontrada</strong><span>Ajuste os filtros ou crie uma campanha para um público específico.</span><button type="button" className="campaigns-secondary-button" onClick={() => { setSearch(''); setChannel('ALL'); setTab('all') }}>Limpar filtros</button></div>}
          <footer className="campaigns-list-footer"><span>Mostrando {filtered.length} campanhas demonstrativas</span><span>Nenhum envio real será realizado</span></footer>
        </section>

        {selected && <CampaignDetail campaign={selected} onClose={() => setSelectedId(null)} onStatus={(status) => changeStatus(selected.id, status)} onNotice={showNotice} />}
      </div>
      <p className="campaigns-data-note">Prévia do MVP com dados de exemplo. O disparo depende de consentimento, modelos aprovados e integração com um provedor de mensagens.</p>
    </div>
    {creating && <CampaignModal onClose={() => setCreating(false)} onSubmit={createCampaign} />}
    {notice && <div className="campaigns-toast" role="status"><Check size={14} />{notice}</div>}
  </main>
}

function CampaignDetail({ campaign, onClose, onStatus, onNotice }) {
  const deliveryRate = campaign.sent ? Math.round(campaign.delivered / campaign.sent * 100) : 0
  return <aside className="campaigns-card campaign-detail" aria-label={`Detalhes da campanha ${campaign.name}`}><header><div><span className="campaign-detail-kicker">{CHANNEL_LABELS[campaign.channel]}</span><h2>{campaign.name}</h2><StatusBadge status={campaign.status} /></div><button type="button" className="campaigns-close" aria-label="Fechar detalhes" onClick={onClose}><X size={17} /></button></header><dl className="campaign-detail-data"><div><dt>Público</dt><dd>{campaign.audience}</dd></div><div><dt>Responsável</dt><dd>{campaign.owner}</dd></div><div><dt>Programação</dt><dd><CalendarDays size={13} />{campaign.schedule}</dd></div></dl><section className="campaign-metrics"><div><strong>{campaign.sent}</strong><span>enviadas</span></div><div><strong>{deliveryRate}%</strong><span>entregues</span></div><div><strong>{campaign.responses}</strong><span>respostas</span></div><div><strong>{campaign.conversions}</strong><span>conversões</span></div></section><section className="campaign-message"><h3>Prévia da mensagem</h3><div><MessageCircle size={15} /><p>{campaign.message}</p></div><small>Variáveis serão substituídas com os dados do paciente antes do envio.</small></section><div className="campaign-detail-actions">{campaign.status === 'RUNNING' ? <button type="button" className="campaigns-secondary-button danger" onClick={() => onStatus('PAUSED')}><Pause size={14} /> Pausar campanha</button> : !['COMPLETED'].includes(campaign.status) && <button type="button" className="campaigns-primary-button" onClick={() => onStatus('RUNNING')}><Play size={14} /> {campaign.status === 'DRAFT' ? 'Revisar e iniciar' : 'Iniciar campanha'}</button>}<button type="button" className="campaigns-secondary-button" onClick={() => onNotice('Prévia validada em modo demonstrativo.')}><Target size={14} /> Validar público</button></div></aside>
}

function CampaignModal({ onClose, onSubmit }) {
  return <div className="campaigns-modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose() }}><section className="campaigns-modal" role="dialog" aria-modal="true" aria-labelledby="new-campaign-title"><header><div><span>Nova comunicação</span><h2 id="new-campaign-title">Criar campanha</h2></div><button type="button" className="campaigns-close" aria-label="Fechar" onClick={onClose}><X size={18} /></button></header><form onSubmit={onSubmit}><label><span>Nome da campanha</span><input name="name" required maxLength="90" placeholder="Ex.: Retorno preventivo de novembro" /></label><div className="campaigns-form-grid"><label><span>Objetivo</span><input name="objective" required maxLength="70" placeholder="Ex.: Agendar retorno" /></label><label><span>Canal</span><select name="channel" defaultValue="WHATSAPP">{Object.entries(CHANNEL_LABELS).map(([value, label]) => <option value={value} key={value}>{label}</option>)}</select></label></div><label><span>Público</span><input name="audience" required maxLength="110" placeholder="Ex.: Pacientes sem retorno há 180 dias" /></label><label><span>Mensagem</span><textarea name="message" required rows="5" maxLength="600" placeholder="Olá, {{primeiro_nome}}! Escreva aqui a mensagem da campanha." /></label><label><span>Responsável</span><select name="owner" defaultValue="Você"><option>Você</option><option>Carla Mendes</option><option>Rafael Lima</option></select></label><footer><button type="button" className="campaigns-secondary-button" onClick={onClose}>Cancelar</button><button type="submit" className="campaigns-primary-button"><Plus size={15} /> Salvar rascunho</button></footer></form></section></div>
}

function Kpi({ label, value, note, tone = '', icon }) { return <article className={`campaigns-kpi ${tone ? `campaigns-kpi-${tone}` : ''}`}><div><span>{label}</span>{icon}</div><strong>{value}</strong><small>{note}</small></article> }
function StatusBadge({ status }) { return <span className={`campaign-status campaign-status-${status.toLowerCase()}`}><i />{STATUS_LABELS[status]}</span> }
