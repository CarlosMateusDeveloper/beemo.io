// Dataset mock da tela /convenios. Sem endpoint de backend ainda — a fila de
// glosas e os KPIs devem futuramente consumir GET /convenios/glosas?periodo=&
// convenios=&status=&responsavel=, com os agregados calculados no servidor.

export const PERIODOS = ['Últimos 30 dias', '7 dias', 'Mês', 'Personalizado']
export const CONVENIOS = ['Unimed', 'Bradesco Saúde', 'SulAmérica', 'Amil']
export const RESPONSAVEIS = ['Camila F.', 'Diego M.', 'Priscila N.']

export const KPI_BASE = {
  aReceber: 84300,
  aReceberLotes: 12,
  aReceberMediaDias: 47,
  glosadoMes: 19700,
  glosadoPct: 4.2,
  prazoVencendoValor: 12400,
  prazoVencendoQtd: 3,
  prazoVencendoDias: 5,
  recuperado: 8100,
  recuperadoTaxaPct: 61,
}

const STATUS_META = {
  analisar: { label: 'A analisar', cls: 'st-neutro' },
  recurso: { label: 'Em recurso', cls: 'st-info' },
  recorrida: { label: 'Recorrida', cls: 'st-info-tracejado' },
  revertida: { label: 'Revertida', cls: 'st-ok' },
  perdida: { label: 'Perdida', cls: 'st-perdida' },
}

export function statusMeta(status) {
  return STATUS_META[status]
}

// prazoDias null = prazo já expirado (linha some da fila ativa, mas ainda
// aparece esmaecida — é o registro de uma glosa perdida por decurso de prazo).
export const GLOSAS = [
  { id: 1, paciente: 'Marina Castilho Alves', procedimento: 'Ressonância magnética de joelho', dataAtend: '12/08/2026', convenio: 'Unimed', convenioSigla: 'UN', motivoCodigo: '1707', motivo: 'Sem autorização prévia', valor: 340, prazoDias: 2, venceTxt: '18/08', status: 'recurso', responsavel: 'Camila F.' },
  { id: 2, paciente: 'Rogério Tavares Pinto', procedimento: 'Ecocardiograma com Doppler', dataAtend: '07/08/2026', convenio: 'Bradesco Saúde', convenioSigla: 'BS', motivoCodigo: '0341', motivo: 'Código TUSS incorreto', valor: 1780, prazoDias: 4, venceTxt: '20/08', status: 'analisar', responsavel: null },
  { id: 3, paciente: 'Helena Duarte Nogueira', procedimento: 'Consulta em cardiologia', dataAtend: '05/08/2026', convenio: 'SulAmérica', convenioSigla: 'SA', motivoCodigo: '2210', motivo: 'Fora da carência', valor: 620, prazoDias: 5, venceTxt: '21/08', status: 'analisar', responsavel: 'Camila F.' },
  { id: 4, paciente: 'Otávio Bernardes Lima', procedimento: 'Ultrassonografia de abdome total', dataAtend: '28/07/2026', convenio: 'Amil', convenioSigla: 'AM', motivoCodigo: '1512', motivo: 'Documentação insuficiente', valor: 245, prazoDias: 11, venceTxt: '27/08', status: 'recurso', responsavel: 'Diego M.' },
  { id: 5, paciente: 'Beatriz Sampaio Rocha', procedimento: 'Endoscopia digestiva alta', dataAtend: '22/07/2026', convenio: 'Unimed', convenioSigla: 'UN', motivoCodigo: '1707', motivo: 'Sem autorização prévia', valor: 890, prazoDias: 13, venceTxt: '29/08', status: 'recorrida', responsavel: 'Camila F.' },
  { id: 6, paciente: 'Luíza Andrade Peixoto', procedimento: 'Fisioterapia motora · 10 sessões', dataAtend: '14/07/2026', convenio: 'SulAmérica', convenioSigla: 'SA', motivoCodigo: '1512', motivo: 'Documentação insuficiente', valor: 1450, prazoDias: 26, venceTxt: '11/09', status: 'recurso', responsavel: 'Priscila N.' },
  { id: 7, paciente: 'Wagner Toledo Almeida', procedimento: 'Hemograma completo', dataAtend: '02/07/2026', convenio: 'Bradesco Saúde', convenioSigla: 'BS', motivoCodigo: '0341', motivo: 'Código TUSS incorreto', valor: 120, prazoDias: 40, venceTxt: '25/09', status: 'revertida', responsavel: 'Diego M.' },
  { id: 8, paciente: 'Eduardo Vilela Franco', procedimento: 'Tomografia de tórax', dataAtend: '19/05/2026', convenio: 'Amil', convenioSigla: 'AM', motivoCodigo: '2210', motivo: 'Fora da carência', valor: 760, prazoDias: null, venceTxt: '30/06', status: 'perdida', responsavel: 'Diego M.' },
]

export function brl(valor) {
  return valor.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

// Aba "Convênios" — uma linha por convênio, para comparação (não cadastro).
export const CONVENIOS_RESUMO = [
  { convenio: 'Unimed', convenioSigla: 'UN', atendimentos: 142, faturado: 52400, recebido: 43100, glosaPct: 8.1, prazoRealDias: 62, prazoContratadoDias: 45, taxaReversaoPct: 58, tabelaDesatualizada: true },
  { convenio: 'Bradesco Saúde', convenioSigla: 'BS', atendimentos: 98, faturado: 31200, recebido: 28900, glosaPct: 3.4, prazoRealDias: 45, prazoContratadoDias: 40, taxaReversaoPct: 70, tabelaDesatualizada: false },
  { convenio: 'SulAmérica', convenioSigla: 'SA', atendimentos: 76, faturado: 24800, recebido: 19700, glosaPct: 6.2, prazoRealDias: 51, prazoContratadoDias: 45, taxaReversaoPct: 55, tabelaDesatualizada: false },
  { convenio: 'Amil', convenioSigla: 'AM', atendimentos: 54, faturado: 15900, recebido: 11300, glosaPct: 9.5, prazoRealDias: 58, prazoContratadoDias: 40, taxaReversaoPct: 40, tabelaDesatualizada: true },
]

const STATUS_LOTE_META = {
  enviado: { label: 'Enviado', cls: 'st-neutro' },
  processado: { label: 'Processado', cls: 'st-info' },
  pago: { label: 'Pago', cls: 'st-ok' },
  parcial: { label: 'Pago parcialmente', cls: 'st-warn' },
}

export function statusLoteMeta(status) {
  return STATUS_LOTE_META[status]
}

// Aba "Lotes" — remessas enviadas e a conciliação entre valor enviado x pago.
export const LOTES = [
  { id: 'LT-2026-0901', convenio: 'Unimed', convenioSigla: 'UN', dataEnvio: '15/08/2026', guias: 22, valorEnviado: 18400, valorPago: null, status: 'enviado' },
  { id: 'LT-2026-0888', convenio: 'Bradesco Saúde', convenioSigla: 'BS', dataEnvio: '10/08/2026', guias: 15, valorEnviado: 9800, valorPago: null, status: 'processado' },
  { id: 'LT-2026-0879', convenio: 'SulAmérica', convenioSigla: 'SA', dataEnvio: '05/08/2026', guias: 18, valorEnviado: 12600, valorPago: 12600, status: 'pago' },
  { id: 'LT-2026-0864', convenio: 'Amil', convenioSigla: 'AM', dataEnvio: '29/07/2026', guias: 9, valorEnviado: 6200, valorPago: 5100, status: 'parcial' },
  { id: 'LT-2026-0850', convenio: 'Unimed', convenioSigla: 'UN', dataEnvio: '22/07/2026', guias: 26, valorEnviado: 21300, valorPago: 21300, status: 'pago' },
  { id: 'LT-2026-0837', convenio: 'Bradesco Saúde', convenioSigla: 'BS', dataEnvio: '15/07/2026', guias: 12, valorEnviado: 7400, valorPago: 7400, status: 'pago' },
  { id: 'LT-2026-0822', convenio: 'SulAmérica', convenioSigla: 'SA', dataEnvio: '08/07/2026', guias: 20, valorEnviado: 14200, valorPago: 12800, status: 'parcial' },
  { id: 'LT-2026-0809', convenio: 'Amil', convenioSigla: 'AM', dataEnvio: '01/07/2026', guias: 11, valorEnviado: 8100, valorPago: 8100, status: 'pago' },
  { id: 'LT-2026-0795', convenio: 'Unimed', convenioSigla: 'UN', dataEnvio: '24/06/2026', guias: 19, valorEnviado: 15600, valorPago: 15600, status: 'pago' },
  { id: 'LT-2026-0781', convenio: 'Bradesco Saúde', convenioSigla: 'BS', dataEnvio: '17/06/2026', guias: 14, valorEnviado: 9200, valorPago: 9200, status: 'pago' },
  { id: 'LT-2026-0768', convenio: 'SulAmérica', convenioSigla: 'SA', dataEnvio: '10/06/2026', guias: 17, valorEnviado: 11800, valorPago: 10400, status: 'parcial' },
  { id: 'LT-2026-0754', convenio: 'Amil', convenioSigla: 'AM', dataEnvio: '03/06/2026', guias: 8, valorEnviado: 5400, valorPago: 5400, status: 'pago' },
]
