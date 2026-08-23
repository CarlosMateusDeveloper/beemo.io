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

// Sem conversas ainda — GET /whatsapp/conversas deve popular esta lista.
export const CONVERSAS = []

export function ordenarConversas(lista) {
  const aguardando = lista.filter((c) => c.estado === 'aguardando').sort((a, b) => b.esperaMin - a.esperaMin)
  const outras = lista.filter((c) => c.estado !== 'aguardando').sort((a, b) => b.horario.localeCompare(a.horario))
  return [...aguardando, ...outras]
}

// Sem histórico ainda — GET /whatsapp/conversas/{id}/mensagens e
// GET /whatsapp/conversas/{id}/contexto devem popular estes mapas por id de conversa.
export const MENSAGENS_POR_CONVERSA = {}

export const CONTEXTO_POR_CONVERSA = {}

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

// Sem histórico de volume ainda — os contadores ficam em zero (o eixo de
// horas/meses fica porque os gráficos abaixo assumem pelo menos um ponto).
export const VOLUME_POR_HORA = [
  { hora: '7h', bot: 0, humano: 0 }, { hora: '8h', bot: 0, humano: 0 }, { hora: '9h', bot: 0, humano: 0 },
  { hora: '10h', bot: 0, humano: 0 }, { hora: '11h', bot: 0, humano: 0 }, { hora: '12h', bot: 0, humano: 0 },
  { hora: '13h', bot: 0, humano: 0 }, { hora: '14h', bot: 0, humano: 0 }, { hora: '15h', bot: 0, humano: 0 },
  { hora: '16h', bot: 0, humano: 0 }, { hora: '17h', bot: 0, humano: 0 }, { hora: '18h', bot: 0, humano: 0 },
  { hora: '19h', bot: 0, humano: 0 },
]

export const EVOLUCAO_MENSAL = [
  { mes: 'mar', pct: 0 }, { mes: 'abr', pct: 0 }, { mes: 'mai', pct: 0 },
  { mes: 'jun', pct: 0 }, { mes: 'jul', pct: 0 }, { mes: 'ago', pct: 0 },
]

const ESCALONAMENTO_MOTIVOS_BASE = [
  { motivo: 'Não entendeu', qtd: 0 },
  { motivo: 'Pediu humano', qtd: 0 },
  { motivo: 'Palavra-chave', qtd: 0 },
]

const ACOES_CONCLUIDAS_BASE = [
  { tipo: 'Agendamentos', qtd: 0 },
  { tipo: 'Confirmações', qtd: 0 },
  { tipo: 'Remarcações', qtd: 0 },
  { tipo: 'Cancelamentos', qtd: 0 },
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
    deltaPct: 0, // vs. período anterior — só faz sentido no default "últimos 30 dias"
    escalonamento: { total: humano, motivos: escalonamento },
    tempoPrimeiraRespostaSeg: 0,
    tempoPrimeiraRespostaAtendenteSeg: 0,
    acoes: { total: acoes.reduce((a, x) => a + x.qtd, 0), porTipo: acoes },
    byHour,
    peakLabel: `pico ${PEAK_HOURS[0]}–${PEAK_HOURS[PEAK_HOURS.length - 1]}`,
    peakPct,
    byMonth: EVOLUCAO_MENSAL,
    aguardandoAgora: CONVERSAS.filter((c) => c.estado === 'aguardando').length,
    esperaMediaHojeMin: 0,
  }
}
