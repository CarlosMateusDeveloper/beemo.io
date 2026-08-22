// Dataset mock da tela /caixa. Sem endpoint de backend ainda — turno, fila de
// recebimento e movimento devem futuramente consumir GET /caixa/turno-atual,
// com o registro de pagamento e o fechamento gravados via POST.

export const TURNO = {
  dataLabel: 'Quinta, 20 de agosto de 2026',
  horaAbertura: '08:12',
  operador: 'Camila Reis',
  operadorCargo: 'Recepção',
  totalHoje: 4380,
  recebimentosHoje: 23,
  dinheiro: 620,
  cartao: 2940,
  pix: 820,
}

export const RECEBER_INICIAL = [
  { id: 1, hora: '14:30', paciente: 'Marina Albuquerque', procedimento: 'Consulta dermatológica', convenio: null, valor: 320, pago: false },
  { id: 2, hora: '14:50', paciente: 'Otávio Bandeira', procedimento: 'Retorno ortopédico', convenio: 'Unimed', valor: 45, pago: false },
  { id: 3, hora: '15:10', paciente: 'Juliana Sampaio', procedimento: 'Aplicação de toxina botulínica', convenio: null, valor: 890, pago: false },
  { id: 4, hora: '15:40', paciente: 'Ricardo Meneghini', procedimento: 'Consulta cardiológica', convenio: 'Bradesco Saúde', valor: 60, pago: false },
  { id: 5, hora: '16:00', paciente: 'Heloísa Prado', procedimento: 'Drenagem linfática', convenio: null, valor: 180, pago: false },
  { id: 6, hora: '13:20', paciente: 'Fábio Constantino', procedimento: 'Consulta clínica', convenio: null, valor: 250, pago: true },
]

export const MOVIMENTO = [
  { hora: '13:24', desc: 'Fábio Constantino', sub: 'Consulta clínica', metodo: 'pix', metodoLabel: 'Pix', tipo: 'in', valor: 250, id: 'REC-0428', quem: 'Camila' },
  { hora: '13:02', desc: 'Compra de material', sub: 'Luvas e gaze · Drogaria Central', metodo: 'dinheiro', metodoLabel: 'Dinheiro', tipo: 'out', valor: 84.9, id: 'DES-0061', quem: 'Camila' },
  { hora: '12:40', desc: 'Sueli Andrade', sub: 'Peeling químico', metodo: 'credito', metodoLabel: 'Crédito 3x', tipo: 'in', valor: 690, id: 'REC-0427', quem: 'Camila' },
  { hora: '11:55', desc: 'Adiantamento — Jorge Nunes', sub: 'Vale · equipe de limpeza', metodo: 'dinheiro', metodoLabel: 'Dinheiro', tipo: 'out', valor: 150, id: 'DES-0060', quem: 'Camila' },
  { hora: '11:10', desc: 'Lucas Ferreira Pinto', sub: 'Consulta clínica', metodo: 'debito', metodoLabel: 'Débito', tipo: 'in', valor: 320, id: 'REC-0426', quem: 'Camila' },
  { hora: '10:05', desc: 'Retirada para fundo de troco', sub: 'Suprimento devolvido ao cofre', metodo: 'dinheiro', metodoLabel: 'Dinheiro', tipo: 'out', valor: 200, id: 'DES-0059', quem: 'Camila' },
  { hora: '09:20', desc: 'Beatriz Camargo', sub: 'Retorno dermatológico', metodo: 'dinheiro', metodoLabel: 'Dinheiro', tipo: 'in', valor: 160, id: 'REC-0425', quem: 'Camila' },
]

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
