// Dataset mock da tela /whatsapp. Sem endpoint de backend ainda — as formas
// abaixo já são desenhadas para bater com o que os endpoints reais devem
// devolver, então trocar por fetch() real deve significar só isso: trocar
// a fonte do array/objeto, sem remodelar os componentes.
//
//   GET  /whatsapp/conversas                       -> CONVERSAS
//   GET  /whatsapp/conversas/{id}/mensagens         -> MENSAGENS_POR_CONVERSA[id]
//   GET  /whatsapp/conversas/{id}/contexto           -> CONTEXTO_POR_CONVERSA[id] (pode não existir)
//   POST /whatsapp/conversas/{id}/assumir            -> muda estado para 'com_agente'
//   POST /whatsapp/conversas/{id}/devolver           -> muda estado para 'bot'
//   GET  /whatsapp/assistente                        -> CAPACIDADES + MENSAGENS_POR_CAPACIDADE + REGRAS_PADRAO
//   PATCH /whatsapp/assistente/capacidades/{id}      -> liga/desliga
//   PATCH /whatsapp/assistente/mensagens/{capId}     -> autosave dos textos
//   PATCH /whatsapp/assistente/regras                -> autosave das regras
//   GET  /whatsapp/desempenho?periodo=               -> calcularDesempenho(periodo)

export const NUMERO_CLINICA = '+55 11 4002-8922'
export const NOME_CLINICA = 'Clínica Vitalis'
export const CONECTADO = true // status real da sessão do WhatsApp Business

// ---------- Conversas ----------

export const CONVERSAS = [
  { id: 'renata', paciente: 'Renata Albuquerque', telefone: '+55 11 97744-2318', horario: '08:41',
    ultimaMensagem: 'não sei, tô com dor e queria falar com alguém', estado: 'aguardando', esperaMin: 13, nota: null },
  { id: 'thiago', paciente: 'Thiago Nakamura', telefone: '+55 11 98221-0937', horario: '08:47',
    ultimaMensagem: 'pedi pra falar com uma pessoa, por favor', estado: 'aguardando', esperaMin: 7, nota: null },
  { id: 'desconhecido-1', paciente: null, telefone: '+55 11 98812-4471', horario: '08:50',
    ultimaMensagem: 'quero marcar com cardiologia amanhã', estado: 'aguardando', esperaMin: 4, nota: 'não cadastrado' },
  { id: 'vera', paciente: 'Vera Lúcia Prado', telefone: '+55 11 99456-7723', horario: '08:52',
    ultimaMensagem: 'não é isso que eu quis dizer', estado: 'aguardando', esperaMin: 2, nota: 'não entendeu 2×' },
  { id: 'marcos', paciente: 'Marcos Vinícius Sales', telefone: '+55 11 98123-4590', horario: '08:44',
    ultimaMensagem: 'combinado, obrigado!', estado: 'com_agente', agente: 'Camila', esperaMin: 0, nota: null },
  { id: 'juliana', paciente: 'Juliana Prates', telefone: '+55 11 97788-2210', horario: '08:39',
    ultimaMensagem: '1', estado: 'bot', esperaMin: 0, nota: 'remarcou 28/08' },
  { id: 'eduardo', paciente: 'Eduardo Bittencourt', telefone: '+55 11 99011-4432', horario: '08:21',
    ultimaMensagem: 'obrigado, até quinta', estado: 'bot', esperaMin: 0, nota: 'confirmou presença' },
]

export function ordenarConversas(lista) {
  const aguardando = lista.filter((c) => c.estado === 'aguardando').sort((a, b) => b.esperaMin - a.esperaMin)
  const outras = lista.filter((c) => c.estado !== 'aguardando').sort((a, b) => b.horario.localeCompare(a.horario))
  return [...aguardando, ...outras]
}

export const MENSAGENS_POR_CONVERSA = {
  renata: [
    { id: 1, tipo: 'mensagem', remetente: 'agente', agente: 'Camila', texto: 'Atendemos sim, Renata. Unimed e Bradesco Saúde.', horario: 'ontem · 16:05' },
    { id: 2, tipo: 'mensagem', remetente: 'paciente', texto: 'Oi, bom dia', horario: '08:36' },
    { id: 3, tipo: 'mensagem', remetente: 'bot', texto: 'Bom dia, Renata! Sou o assistente da Clínica Vitalis.\n1 · marcar · 2 · remarcar ou cancelar · 3 · confirmar presença · 4 · meu horário', horario: '08:36' },
    { id: 4, tipo: 'mensagem', remetente: 'paciente', texto: '1', horario: '08:37' },
    { id: 5, tipo: 'mensagem', remetente: 'bot', texto: 'Para qual especialidade? Você pode escrever, por exemplo: cardiologia, dermatologia, ortopedia.', horario: '08:37' },
    { id: 6, tipo: 'mensagem', remetente: 'paciente', texto: 'ortopedia', horario: '08:38' },
    { id: 7, tipo: 'mensagem', remetente: 'bot', texto: 'Tenho com Dr. Henrique Salles:\n1 · qui, 27/08 às 14:20 · 2 · sex, 28/08 às 09:40', horario: '08:38' },
    { id: 8, tipo: 'mensagem', remetente: 'paciente', texto: 'não sei, tô com dor e queria falar com alguém', horario: '08:41', destaque: 'dor' },
    { id: 9, tipo: 'escalonamento', motivo: 'palavra-chave "dor"', horario: '08:41' },
  ],
  thiago: [
    { id: 1, tipo: 'mensagem', remetente: 'bot', texto: 'Oi Thiago! Sou o assistente da Clínica Vitalis.\n1 · marcar · 2 · remarcar ou cancelar · 3 · confirmar presença · 4 · meu horário', horario: '08:45' },
    { id: 2, tipo: 'mensagem', remetente: 'paciente', texto: 'pedi pra falar com uma pessoa, por favor', horario: '08:47' },
    { id: 3, tipo: 'escalonamento', motivo: 'paciente pediu atendente', horario: '08:47' },
  ],
  'desconhecido-1': [
    { id: 1, tipo: 'mensagem', remetente: 'bot', texto: 'Oi! Não encontrei esse número no nosso cadastro. Antes de marcar, preciso de alguns dados seus — pode me dizer seu nome completo?', horario: '08:49' },
    { id: 2, tipo: 'mensagem', remetente: 'paciente', texto: 'quero marcar com cardiologia amanhã', horario: '08:50' },
    { id: 3, tipo: 'escalonamento', motivo: 'cadastro incompleto', horario: '08:50' },
  ],
  vera: [
    { id: 1, tipo: 'mensagem', remetente: 'bot', texto: 'Para qual especialidade? Você pode escrever, por exemplo: cardiologia, dermatologia, ortopedia.', horario: '08:51' },
    { id: 2, tipo: 'mensagem', remetente: 'paciente', texto: 'não é isso que eu quis dizer', horario: '08:52' },
    { id: 3, tipo: 'escalonamento', motivo: 'não entendeu 2 vezes seguidas', horario: '08:52' },
  ],
  marcos: [
    { id: 1, tipo: 'mensagem', remetente: 'agente', agente: 'Camila', texto: 'Fechado, Marcos! 29/08 às 11:00 com Dr. Ivan Portela.', horario: '08:43' },
    { id: 2, tipo: 'mensagem', remetente: 'paciente', texto: 'combinado, obrigado!', horario: '08:44' },
  ],
  juliana: [
    { id: 1, tipo: 'mensagem', remetente: 'bot', texto: 'Sua consulta de 26/08 às 09:00 precisa ser remarcada?\n1 · sim · 2 · não', horario: '08:38' },
    { id: 2, tipo: 'mensagem', remetente: 'paciente', texto: '1', horario: '08:39' },
    { id: 3, tipo: 'mensagem', remetente: 'bot', texto: 'Pronto! Remarquei para 28/08 às 09:00.', horario: '08:39' },
  ],
  eduardo: [
    { id: 1, tipo: 'mensagem', remetente: 'bot', texto: 'Oi Eduardo! Confirma presença na consulta de quinta, 27/08 às 16:00?\n1 · confirmar · 2 · remarcar', horario: '08:20' },
    { id: 2, tipo: 'mensagem', remetente: 'paciente', texto: 'obrigado, até quinta', horario: '08:21' },
  ],
}

export const CONTEXTO_POR_CONVERSA = {
  renata: {
    paciente: { nome: 'Renata Albuquerque', idade: 42, convenio: 'Unimed', clienteDesde: 'mar/2021' },
    proximaConsulta: { data: 'ter, 08/09', hora: '10:15', especialidade: 'Clínica geral', profissional: 'Dra. Paula Vasques', confirmacaoEnviada: false },
    ultimasVisitas: [
      { especialidade: 'Ortopedia', profissional: 'Dr. Henrique Salles', data: '12/06' },
      { especialidade: 'Clínica geral', profissional: 'Dra. Paula Vasques', data: '03/03' },
    ],
    pendencias: [
      { tipo: 'pagamento', label: 'Pagamento em aberto', valor: 'R$ 180,00', critico: true },
      { tipo: 'exame', label: 'Exame sem resultado', nota: 'env. 12/08', critico: false },
    ],
    assistente: {
      capacidade: 'Marcar consulta', passoAtual: 3, totalPassos: 4, parouEm: 'Escolha de horário',
      perguntas: [
        { label: 'Especialidade', valor: 'Ortopedia' },
        { label: 'Profissional', valor: 'Dr. Henrique Salles' },
        { label: 'Horários oferecidos', valor: '27/08 · 28/08' },
        { label: 'Escolha do paciente', valor: 'sem resposta', pendente: true },
      ],
    },
  },
  thiago: {
    paciente: { nome: 'Thiago Nakamura', idade: 35, convenio: 'Particular', clienteDesde: 'jan/2024' },
    proximaConsulta: null,
    ultimasVisitas: [{ especialidade: 'Dermatologia', profissional: 'Dra. Fernanda Ruiz', data: '20/07' }],
    pendencias: [],
    assistente: { capacidade: 'Menu inicial', passoAtual: 1, totalPassos: 1, parouEm: 'Saudação', perguntas: [] },
  },
  vera: {
    paciente: { nome: 'Vera Lúcia Prado', idade: 67, convenio: 'Bradesco Saúde', clienteDesde: 'nov/2019' },
    proximaConsulta: { data: 'sex, 04/09', hora: '09:30', especialidade: 'Cardiologia', profissional: 'Dra. Ana Beltrão', confirmacaoEnviada: true },
    ultimasVisitas: [{ especialidade: 'Cardiologia', profissional: 'Dra. Ana Beltrão', data: '05/07' }],
    pendencias: [],
    assistente: { capacidade: 'Marcar consulta', passoAtual: 1, totalPassos: 4, parouEm: 'Escolha de especialidade', perguntas: [{ label: 'Mensagem não reconhecida', valor: '2×', pendente: true }] },
  },
  marcos: {
    paciente: { nome: 'Marcos Vinícius Sales', idade: 29, convenio: 'Particular', clienteDesde: 'ago/2023' },
    proximaConsulta: { data: 'sex, 29/08', hora: '11:00', especialidade: 'Ortopedia', profissional: 'Dr. Ivan Portela', confirmacaoEnviada: false },
    ultimasVisitas: [],
    pendencias: [],
    assistente: null,
  },
}

// ---------- Assistente ----------

export const CAPACIDADES = [
  { id: 'confirmar_presenca', nome: 'Confirmar presença', ativo: true,
    impactoDesligada: 'Pacientes não recebem mais o lembrete automático na véspera — a régua de confirmação para de disparar.' },
  { id: 'consultar_horario', nome: 'Consultar meu horário', ativo: true,
    impactoDesligada: 'Pacientes deixam de conseguir consultar o próprio horário pelo WhatsApp; toda dúvida vira contato manual.' },
  { id: 'marcar_consulta', nome: 'Marcar consulta', ativo: true,
    impactoDesligada: 'O assistente para de fechar agendamentos pelo WhatsApp.' },
  { id: 'remarcar_cancelar', nome: 'Remarcar e cancelar', ativo: true,
    impactoDesligada: 'Pacientes não conseguem mais remarcar ou cancelar sozinhos — toda mudança vira ligação para a recepção.' },
  { id: 'vaga_aberta', nome: 'Oferecer vaga aberta', ativo: true,
    impactoDesligada: 'Vagas abertas por cancelamento deixam de ser oferecidas automaticamente à fila de espera.' },
  { id: 'cadastro_novo', nome: 'Cadastro de paciente novo', ativo: true,
    impactoDesligada: 'Pacientes novos voltam a precisar ligar ou ir à recepção para se cadastrar.' },
  { id: 'aviso_exame', nome: 'Avisar resultado de exame', ativo: false,
    impactoDesligada: 'Pacientes não são avisados quando um resultado de exame fica pronto.' },
]

export const MENSAGENS_POR_CAPACIDADE = {
  confirmar_presenca: {
    saudacao: 'Oi {nome}! Confirma presença na consulta de {data} às {hora} com {medico}?\n1 · confirmar · 2 · remarcar',
    opcoes: 'Responda 1 para confirmar ou 2 se precisar remarcar.',
    confirmacao: 'Presença confirmada para {data} às {hora}. Até lá!',
    naoEntendi: 'Não entendi. Responda 1 para confirmar ou 2 para remarcar.',
  },
  consultar_horario: {
    saudacao: 'Oi {nome}! Sua próxima consulta é {data} às {hora}, com {medico} ({especialidade}).',
    opcoes: 'Quer que eu remarque ou cancele esse horário?\n1 · remarcar · 2 · cancelar · 3 · não, obrigado',
    confirmacao: 'Certo, {nome}! Fica como está: {data} às {hora}.',
    naoEntendi: 'Não entendi. Responda o número da opção ou escreva "atendente".',
  },
  marcar_consulta: {
    saudacao: 'Oi {nome}, tudo bem? Sou o assistente da {clinica}.',
    opcoes: 'Para qual especialidade? Ex.: cardiologia, ortopedia, dermatologia.',
    confirmacao: 'Pronto, {nome}! {data} às {hora} com {medico}.',
    naoEntendi: 'Não entendi. Responda o número da opção ou escreva "atendente".',
  },
  remarcar_cancelar: {
    saudacao: 'Oi {nome}! Sua consulta de {data} às {hora} precisa ser remarcada?\n1 · sim · 2 · não',
    opcoes: 'Tenho estes horários com {medico}:\n1 · próxima quinta às 14:20 · 2 · próxima sexta às 09:40',
    confirmacao: 'Pronto! Remarquei para {data} às {hora}.',
    naoEntendi: 'Não entendi. Responda 1 ou 2, ou escreva "atendente".',
  },
  vaga_aberta: {
    saudacao: 'Oi {nome}! Abriu uma vaga com {medico} ({especialidade}) em {data} às {hora}. Quer ficar com ela?',
    opcoes: 'Responda 1 para confirmar essa vaga ou 2 para continuar na fila de espera.',
    confirmacao: 'Vaga confirmada, {nome}! {data} às {hora} com {medico}.',
    naoEntendi: 'Não entendi. Responda 1 para confirmar a vaga ou 2 para continuar esperando.',
  },
  cadastro_novo: {
    saudacao: 'Oi! Não encontrei esse número no nosso cadastro. Antes de marcar, pode me dizer seu nome completo?',
    opcoes: 'Agora preciso da sua data de nascimento e, se tiver, o convênio.',
    confirmacao: 'Cadastro feito, {nome}! Agora vamos escolher o horário.',
    naoEntendi: 'Não entendi. Pode escrever seu nome completo, por favor?',
  },
  aviso_exame: {
    saudacao: 'Oi {nome}! O resultado do seu exame já está disponível.',
    opcoes: 'Quer que eu envie por aqui ou prefere retirar na recepção da {clinica}?\n1 · enviar por aqui · 2 · vou retirar',
    confirmacao: 'Certo, {nome}! Já providenciamos.',
    naoEntendi: 'Não entendi. Responda 1 para receber por aqui ou 2 se for retirar na recepção.',
  },
}

export const VARIAVEIS = ['nome', 'clinica', 'data', 'hora', 'medico', 'especialidade']

export const SAMPLE_VARS = { nome: 'Renata', clinica: NOME_CLINICA, data: '27/08', hora: '14:20', medico: 'Dr. Ivan Portela', especialidade: 'cardiologia' }

export function preencherVariaveis(texto, vars = SAMPLE_VARS) {
  return texto.replace(/\{(\w+)\}/g, (m, chave) => (chave in vars ? vars[chave] : m))
}

export const REGRAS_PADRAO = {
  escalonamento: {
    pedirAtendenteSempre: true,
    naoEntendeuLimite: 2,
    palavrasChave: ['urgente', 'dor', 'emergência'],
  },
  horarios: {
    atendimentoHumano: 'seg–sex 08:00–18:00',
    sabado: '08:00–12:00',
  },
  disparos: {
    confirmacaoPresencaHoras: 24,
    avisoResultadoExameAtivo: true,
  },
  limiteMensagensPorPacientePorDia: 3,
}

// ---------- Desempenho ----------

export const PERIODOS_DESEMPENHO = ['Hoje', '7 dias', 'Últimos 30 dias']
const FATOR_PERIODO_DESEMPENHO = { Hoje: 0.033, '7 dias': 0.23, 'Últimos 30 dias': 1 }

export const VOLUME_POR_HORA = [
  { hora: '7h', bot: 18, humano: 5 },
  { hora: '8h', bot: 96, humano: 27 },
  { hora: '9h', bot: 104, humano: 31 },
  { hora: '10h', bot: 71, humano: 21 },
  { hora: '11h', bot: 48, humano: 14 },
  { hora: '12h', bot: 31, humano: 7 },
  { hora: '13h', bot: 34, humano: 9 },
  { hora: '14h', bot: 42, humano: 13 },
  { hora: '15h', bot: 39, humano: 11 },
  { hora: '16h', bot: 45, humano: 13 },
  { hora: '17h', bot: 40, humano: 14 },
  { hora: '18h', bot: 26, humano: 5 },
  { hora: '19h', bot: 18, humano: 3 },
]

export const EVOLUCAO_MENSAL = [
  { mes: 'mar', pct: 59 },
  { mes: 'abr', pct: 63 },
  { mes: 'mai', pct: 66 },
  { mes: 'jun', pct: 70 },
  { mes: 'jul', pct: 73 },
  { mes: 'ago', pct: 78 },
]

const ESCALONAMENTO_MOTIVOS_BASE = [
  { motivo: 'Não entendeu', qtd: 71 },
  { motivo: 'Pediu humano', qtd: 62 },
  { motivo: 'Palavra-chave', qtd: 40 },
]

const ACOES_CONCLUIDAS_BASE = [
  { tipo: 'Agendamentos', qtd: 240 },
  { tipo: 'Confirmações', qtd: 310 },
  { tipo: 'Remarcações', qtd: 88 },
  { tipo: 'Cancelamentos', qtd: 51 },
]

const PEAK_HOURS = ['8h', '9h', '10h']

export function fInt(v) {
  return Math.round(v).toLocaleString('pt-BR')
}
export function pct(v, casas) {
  return v.toLocaleString('pt-BR', { minimumFractionDigits: 0, maximumFractionDigits: casas == null ? 1 : casas }) + '%'
}
export function fDelta(v) {
  return (v > 0 ? '+' : '') + v.toLocaleString('pt-BR', { maximumFractionDigits: 1 }) + '%'
}

// Calcula o payload da aba Desempenho para um período. Em produção isto é a
// resposta (já pronta) de GET /whatsapp/desempenho?periodo=.
export function calcularDesempenho(periodo) {
  const f = FATOR_PERIODO_DESEMPENHO[periodo] ?? 1
  const byHour = VOLUME_POR_HORA.map((h) => ({ ...h, bot: Math.round(h.bot * f), humano: Math.round(h.humano * f) }))
  const bot = byHour.reduce((a, h) => a + h.bot, 0)
  const humano = byHour.reduce((a, h) => a + h.humano, 0)
  const total = bot + humano
  const sharePct = total ? (bot / total) * 100 : 0
  const peakTotal = byHour.filter((h) => PEAK_HOURS.includes(h.hora)).reduce((a, h) => a + h.bot + h.humano, 0)
  const peakPct = total ? (peakTotal / total) * 100 : 0

  const escalonamento = ESCALONAMENTO_MOTIVOS_BASE.map((m) => ({ ...m, qtd: Math.round(m.qtd * f) }))
  const acoes = ACOES_CONCLUIDAS_BASE.map((a) => ({ ...a, qtd: Math.round(a.qtd * f) }))

  return {
    resolvidasSemAtendente: bot,
    total,
    sharePct,
    deltaPct: 14, // vs. período anterior — só faz sentido no default "últimos 30 dias"
    escalonamento: { total: humano, motivos: escalonamento },
    tempoPrimeiraRespostaSeg: 8,
    tempoPrimeiraRespostaAtendenteSeg: 252,
    acoes: { total: acoes.reduce((a, x) => a + x.qtd, 0), porTipo: acoes },
    byHour,
    peakLabel: `pico ${PEAK_HOURS[0]}–${PEAK_HOURS[PEAK_HOURS.length - 1]}`,
    peakPct,
    byMonth: EVOLUCAO_MENSAL,
    aguardandoAgora: CONVERSAS.filter((c) => c.estado === 'aguardando').length,
    esperaMediaHojeMin: 9,
  }
}
