// Helpers puros da tela /caixa. Os dados em si vêm de GET/POST /api/caixa/*
// (ver ./api.js) — nada aqui é mock.

export const METODOS = [
  { key: 'dinheiro', label: 'Dinheiro' },
  { key: 'debito', label: 'Débito' },
  { key: 'credito', label: 'Crédito' },
  { key: 'pix', label: 'Pix' },
]

export function brl(valor) {
  return valor.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

export function formatNum(valor) {
  return Number(valor).toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

export function parseValorInput(raw) {
  const v = parseFloat(String(raw ?? '').replace(/\s/g, '').replace(/\./g, '').replace(',', '.'))
  return isNaN(v) ? 0 : v
}

// Mesmas strings de período aceitas por CaixaDreService/CaixaFluxoConsolidadoService
// (e por ConveniosKpiService, do lado de /convenios) — consistência entre módulos.
export const PERIODOS = [
  { valor: 'Últimos 30 dias', rotulo: 'Últimos 30 dias' },
  { valor: 'Hoje', rotulo: 'Hoje' },
  { valor: '7 dias', rotulo: 'Últimos 7 dias' },
  { valor: '90 dias', rotulo: 'Últimos 90 dias' },
]

// Mesmo CHECK de despesa.categoria (migração 008).
export const CATEGORIAS_DESPESA = [
  { valor: 'aluguel', rotulo: 'Aluguel' },
  { valor: 'folha', rotulo: 'Folha de pagamento' },
  { valor: 'fornecedores', rotulo: 'Fornecedores' },
  { valor: 'insumos', rotulo: 'Insumos' },
  { valor: 'impostos', rotulo: 'Impostos' },
  { valor: 'marketing', rotulo: 'Marketing' },
  { valor: 'manutencao', rotulo: 'Manutenção' },
  { valor: 'servicos', rotulo: 'Serviços' },
  { valor: 'outros', rotulo: 'Outros' },
]

export function categoriaDespesaLabel(valor) {
  return CATEGORIAS_DESPESA.find((c) => c.valor === valor)?.rotulo ?? valor
}

// despesa.status (pendente/pago/cancelado) + "atrasado" calculado pelo
// backend (DespesaDto.atrasado) — não é um status armazenado.
export function statusDespesaMeta(status, atrasado) {
  if (status === 'pendente' && atrasado) return { rotulo: 'Atrasada', cls: 'st-perdida' }
  if (status === 'pendente') return { rotulo: 'Pendente', cls: 'st-warn' }
  if (status === 'pago') return { rotulo: 'Paga', cls: 'st-ok' }
  if (status === 'cancelado') return { rotulo: 'Cancelada', cls: 'st-neutro' }
  return { rotulo: status, cls: 'st-neutro' }
}
