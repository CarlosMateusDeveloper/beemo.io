// Dataset mock da tela /caixa. Sem endpoint de backend ainda — turno, fila de
// recebimento e movimento devem futuramente consumir GET /caixa/turno-atual,
// com o registro de pagamento e o fechamento gravados via POST.

// Operador placeholder até a autenticação real existir — mesmo nome usado
// no restante do app (ver CURRENT_USER em components/layout/UserMenu.jsx).
export const TURNO = {
  dataLabel: 'Quinta, 20 de agosto de 2026',
  horaAbertura: null,
  operador: 'Ana Souza',
  operadorCargo: 'Administradora',
  totalHoje: 0,
  recebimentosHoje: 0,
  dinheiro: 0,
  cartao: 0,
  pix: 0,
}

// Sem consultas do dia ainda — GET /caixa/turno-atual deve popular a fila de
// "a receber hoje" a partir da agenda.
export const RECEBER_INICIAL = []

// Sem lançamentos ainda — GET /caixa/turno-atual deve popular o movimento do dia.
export const MOVIMENTO = []

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
