// Dataset mock da tela /convenios. Sem endpoint de backend ainda — a fila de
// glosas e os KPIs devem futuramente consumir GET /convenios/glosas?periodo=&
// convenios=&status=&responsavel=, com os agregados calculados no servidor.

export const PERIODOS = ['Últimos 30 dias', '7 dias', 'Mês', 'Personalizado']
export const CONVENIOS = ['Unimed', 'Bradesco Saúde', 'SulAmérica', 'Amil']
export const RESPONSAVEIS = ['Camila F.', 'Diego M.', 'Priscila N.']

export const KPI_BASE = {
  aReceber: 0,
  aReceberLotes: 0,
  aReceberMediaDias: 0,
  glosadoMes: 0,
  glosadoPct: 0,
  prazoVencendoValor: 0,
  prazoVencendoQtd: 0,
  prazoVencendoDias: 0,
  recuperado: 0,
  recuperadoTaxaPct: 0,
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
// Sem glosas registradas ainda — GET /convenios/glosas deve popular esta fila.
export const GLOSAS = []

export function brl(valor) {
  return valor.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

// Aba "Convênios" — uma linha por convênio, para comparação (não cadastro).
// Sem apuração ainda — GET /convenios/resumo deve popular esta lista.
export const CONVENIOS_RESUMO = []

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
// Sem lotes enviados ainda — GET /convenios/lotes deve popular esta lista.
export const LOTES = []
