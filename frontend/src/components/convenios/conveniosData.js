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
