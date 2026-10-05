import { useState } from 'react'

const paths = {
  arrow: 'M4 12h16m-6-6 6 6-6 6',
  check: 'm5 12 4 4L19 6',
  message: 'M21 11.5a8.4 8.4 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.4 8.4 0 0 1-3.8-.9L3 21l1.9-5.7a8.4 8.4 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.4 8.4 0 0 1 3.8-.9h.5a8.5 8.5 0 0 1 8 8v.5Z',
  clock: 'M12 8v4l3 2M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0Z',
  chart: 'M4 4v16h16M8 14l4-4 4 2 4-7',
  calendar: 'M8 2v4m8-4v4M3 10h18M5 4h14a2 2 0 0 1 2 2v14H3V6a2 2 0 0 1 2-2Zm2 10h3m4 0h3',
  people: 'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2m20 0v-2a4 4 0 0 0-3-3.87M13 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0Zm4-3.87a4 4 0 0 1 0 7.75',
  spark: 'm12 3 2.8 6.2L21 12l-6.2 2.8L12 21l-2.8-6.2L3 12l6.2-2.8L12 3Z',
  grid: 'M3 3h7v7H3Zm11 0h7v7h-7ZM3 14h7v7H3Zm11 0h7v7h-7Z',
  journey: 'M7 9v6a4 4 0 0 0 4 4h5M4 3h6v6H4Zm12 13h6v8h-6M7 11h10V5',
  link: 'm9 15 6-6m-7 9-1 1a4.24 4.24 0 0 1-6-6l4-4a4.24 4.24 0 0 1 6 0m2-3 1-1a4.24 4.24 0 0 1 6 6l-4 4a4.24 4.24 0 0 1-6 0',
  plus: 'M12 5v14M5 12h14', menu: 'M4 6h16M4 12h16M4 18h16', close: 'm6 6 12 12M6 18 18 6',
}

export function Icon({ name, size = 20 }) {
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d={paths[name]} /></svg>
}

export function Brand({ small = false }) {
  return <span className={`brand${small ? ' brand-small' : ''}`}><svg width="32" height="32" viewBox="0 0 32 32" fill="none" aria-hidden="true"><path d="M9 8h14v15H9V8Z" stroke="currentColor" strokeWidth="2" /><circle cx="9" cy="8" r="5" fill="currentColor" /><circle cx="23" cy="8" r="4" fill="#69cbb8" /><circle cx="23" cy="23" r="5" fill="currentColor" /><circle cx="9" cy="23" r="4" fill="#69cbb8" /></svg>clinisis<span className="brand-period">.</span></span>
}

const patients = [
  { name: 'Ana Paula', initials: 'AP', description: 'Consulta há 3 dias', status: 'Acompanhar', tone: 'mint', action: 'Enviar acompanhamento', note: 'O momento de saber como ela está.', message: 'Olá, Ana! Como você está se sentindo depois da sua consulta? Estamos por aqui se precisar.', journey: 'Acompanhamento pós-consulta' },
  { name: 'Carlos Oliveira', initials: 'CO', description: 'Retorno recomendado', status: 'Retorno próximo', tone: 'blue', action: 'Entrar em contato', note: 'Um lembrete para continuar o cuidado.', message: 'Olá, Carlos! Seu retorno está próximo. Podemos ajudar você a encontrar um horário com a equipe?', journey: 'Lembrete de retorno' },
  { name: 'Mariana Santos', initials: 'MS', description: '180 dias sem atendimento', status: 'Reativar', tone: 'amber', action: 'Retomar relacionamento', note: 'Uma oportunidade de retomar a conversa.', message: 'Olá, Mariana! Faz um tempo desde a sua última visita. Como você está? Nossa equipe está à disposição.', journey: 'Reativação de pacientes' },
  { name: 'Pedro Lima', initials: 'PL', description: 'Respondeu ao acompanhamento', status: 'Precisa de atenção', tone: 'rose', action: 'Encaminhar à equipe', note: 'Quando é preciso, uma pessoa assume.', message: 'Olá, Pedro! Recebemos sua mensagem. Vamos encaminhar seu relato para a equipe responsável acompanhar você.', journey: 'Acompanhamento com a equipe' },
]

export const journeySteps = [
  ['calendar', 'Consulta realizada', 'O ponto de partida', 'O atendimento registrado dá início ao acompanhamento do paciente.'],
  ['clock', 'Momento certo', 'O Clinisis identifica', 'A jornada organiza o próximo contato de acordo com o atendimento e o retorno previsto.'],
  ['message', 'Acompanhamento', 'A conversa continua', 'Uma mensagem abre espaço para o paciente contar como está e tirar dúvidas com a equipe.'],
  ['people', 'Paciente responde', 'Contexto em um só lugar', 'A resposta fica ligada ao paciente e à oportunidade que iniciou o relacionamento.'],
  ['check', 'Equipe acompanha', 'Do contato ao retorno', 'Sua equipe assume quando necessário e acompanha os próximos passos.'],
]

export function HeroPreview() {
  return <div className="hero-visual">
    <div className="orbit orbit-one" /><div className="orbit orbit-two" />
    <div className="preview-window">
      <div className="window-bar"><span className="window-dots"><i /><i /><i /></span><span>Um novo jeito de acompanhar</span><Icon name="spark" size={14} /></div>
      <div className="preview-body"><aside className="preview-rail" aria-hidden="true"><Brand small /><span className="rail-active"><Icon name="grid" size={17} /></span><Icon name="people" size={17} /><Icon name="message" size={17} /><Icon name="journey" size={17} /><span className="rail-avatar">CL</span></aside>
        <div className="preview-main"><div className="preview-title"><div><span>Pós-venda</span><h2>Seu acompanhamento, hoje.</h2></div><span className="mini-pill">Prévia</span></div>
          <div className="preview-total"><strong>48</strong><span>pacientes em<br />acompanhamento</span><div className="spark-bars" aria-hidden="true">{[28, 42, 36, 55, 68, 82, 100].map((n, i) => <i key={i} style={{ height: `${n}%` }} />)}</div></div>
          <div className="preview-stats"><div><i className="dot mint" /><strong>32</strong><span>Responderam</span></div><div><i className="dot amber" /><strong>11</strong><span>Aguardando</span></div><div><i className="dot rose" /><strong>5</strong><span>Precisam de atenção</span></div></div>
          <div className="preview-actions-title"><h3>Próximas ações</h3><span>Hoje</span></div>
          {patients.slice(0, 2).map(p => <div className="preview-person" key={p.name}><span className={`avatar ${p.tone}`}>{p.initials}</span><div><strong>{p.name}</strong><span>{p.description}</span></div><span className="preview-action"><Icon name="arrow" size={12} />{p.action}</span></div>)}
          <div className="preview-foot"><Icon name="check" size={13} /> Cada paciente com um próximo passo.</div>
        </div>
      </div>
    </div>
    <div className="message-float"><span className="message-symbol"><Icon name="message" size={21} /></span><div><strong>O cuidado continua na conversa.</strong><p>“Tudo certo por aqui. Obrigada pelo carinho!”</p><span>Exemplo de resposta ao acompanhamento <Icon name="check" size={12} /></span></div></div>
    <span className="visual-caption">Interface ilustrativa · dados de demonstração</span>
  </div>
}

export function ProductDemo() {
  const [view, setView] = useState('Oportunidades')
  const [patientIndex, setPatientIndex] = useState(0)
  const [stepIndex, setStepIndex] = useState(0)
  const patient = patients[patientIndex]
  return <div className="product-demo">
    <div className="demo-toolbar"><Brand small /><span className="demo-label"><i />Demonstração interativa</span></div>
    <div className="demo-layout">
      <nav className="demo-nav" aria-label="Explorar a demonstração"><span>PÓS-VENDA</span>{[['Oportunidades', 'people'], ['Jornadas', 'journey'], ['Resultados', 'chart']].map(([title, icon]) => <button type="button" key={title} aria-pressed={view === title} onClick={() => setView(title)}><Icon name={icon} size={18} /><span>{title}</span></button>)}<div className="demo-clinic"><span className="avatar mint">CE</span><div>Clínica exemplo<small>Ambiente demonstrativo</small></div></div></nav>
      <div className="demo-content" aria-live="polite">
        <div className="demo-heading"><div><span>Relacionamento que tem continuidade</span><h3>{view}</h3></div><span className="mini-pill">Dados fictícios</span></div>
        {view === 'Oportunidades' && <div className="demo-opportunities"><div className="demo-patient-list"><div className="demo-list-label"><span>QUEM PRECISA DE VOCÊ</span><span>04 pacientes</span></div>{patients.map((p, i) => <button type="button" key={p.name} className={`demo-patient${patientIndex === i ? ' selected' : ''}`} aria-pressed={patientIndex === i} onClick={() => setPatientIndex(i)}><span className={`avatar ${p.tone}`}>{p.initials}</span><span className="patient-copy"><strong>{p.name}</strong><small>{p.description}</small></span><span className={`status-pill ${p.tone}`}>{p.status}</span><Icon name="arrow" size={15} /></button>)}<p className="demo-hint">Selecione um paciente para ver o próximo passo.</p></div><div className="demo-patient-detail"><span className="section-label">PRÓXIMA AÇÃO</span><h4>{patient.action}</h4><p>{patient.note}</p><div className="demo-message"><span><Icon name="message" size={14} /> Exemplo de mensagem</span><p>{patient.message}</p></div><div className="demo-detail-foot"><Icon name="journey" size={16} /><span>{patient.journey}</span></div></div></div>}
        {view === 'Jornadas' && <div className="demo-journey"><div><span className="section-label">JORNADA DE EXEMPLO</span><h4>Depois da consulta,<br />o cuidado tem sequência.</h4><p>Uma etapa por vez. Com a equipe por perto sempre que o paciente precisar.</p><div className="journey-explanation"><Icon name={journeySteps[stepIndex][0]} /><strong>{journeySteps[stepIndex][1]}</strong><p>{journeySteps[stepIndex][3]}</p></div></div><ol>{journeySteps.map(([icon, title, timing], i) => <li key={title}><button type="button" onClick={() => setStepIndex(i)} aria-pressed={stepIndex === i}><span className="journey-number">0{i + 1}</span><Icon name={icon} size={18} /><span><strong>{title}</strong><small>{timing}</small></span><Icon name="arrow" size={15} /></button></li>)}</ol></div>}
        {view === 'Resultados' && <div className="demo-results"><div className="result-summary"><span className="section-label">DO CONTATO AO RETORNO</span><h4>O resultado aparece<br />no relacionamento.</h4><div><strong>43</strong><span>pacientes recuperados</span></div><div><strong>R$ 12.480</strong><span>em atendimentos associados às ações</span></div><small>Exemplo ilustrativo, sem promessa de resultado.</small></div><div className="result-funnel">{[['Identificadas', 214], ['Contatadas', 167], ['Responderam', 89], ['Agendadas', 52], ['Compareceram', 43]].map(([label, value], i) => <div className="result-stage" key={label}><div><span>{label}</span><strong>{value}</strong></div><div className="result-track"><i style={{ width: `${value / 214 * 100}%`, opacity: 0.45 + i * 0.13 }} /></div></div>)}<span className="funnel-note"><Icon name="check" size={14} /> Receita potencial e realizada, sempre separadas.</span></div></div>}
      </div>
    </div>
    <div className="demo-bottom"><span><Icon name="spark" size={13} /> Explore a proposta do Clinisis</span><span>Exemplos interativos · nenhum envio é realizado</span></div>
  </div>
}
