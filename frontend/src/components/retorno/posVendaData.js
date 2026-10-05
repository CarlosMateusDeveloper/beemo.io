export const STATUS_POS_VENDA = {
  pendente: 'Contato pendente', em_contato: 'Em contato', sem_resposta: 'Sem resposta',
  reagendado: 'Aguardando comparecimento', nova_falta: 'Nova falta', recuperado: 'Comparecimento confirmado',
  nao_deseja: 'Não deseja reagendar', nao_contatar: 'Não contatar', registro_corrigido: 'Falta corrigida na agenda',
}
export const ENCERRADOS = ['recuperado', 'nao_deseja', 'nao_contatar', 'registro_corrigido']
export function parseData(valor) {
  // PostgreSQL devolve offsets como -03/+00; ISO exige -03:00/+00:00.
  return new Date(valor.replace(' ', 'T').replace(/([+-]\d{2})$/, '$1:00'))
}
export function dataHora(valor) {
  if (!valor) return 'A definir'
  return parseData(valor).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' })
}
export function inputData(valor) {
  if (!valor) return ''
  const d = parseData(valor)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}T${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}
