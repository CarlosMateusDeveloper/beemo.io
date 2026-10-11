import { apiRequest } from '../../lib/apiClient'

export function fetchResumo() {
  return apiRequest('/api/retorno/resumo')
}

export function fetchGrupo(grupo) {
  return apiRequest(`/api/retorno/grupos/${grupo}`)
}

export function adiarPacientes(idsPaciente) {
  return apiRequest('/api/retorno/pacientes/adiar', { method: 'POST', body: JSON.stringify({ idsPaciente }) })
}

export function marcarNaoContatar(idsPaciente, motivo) {
  return apiRequest('/api/retorno/pacientes/nao-contatar', { method: 'POST', body: JSON.stringify({ idsPaciente, motivo }) })
}

export function enviarMensagem(idsPaciente, grupo, texto) {
  return apiRequest('/api/retorno/pacientes/enviar-mensagem', {
    method: 'POST', body: JSON.stringify({ idsPaciente, grupo, texto, idUsuario: null }),
  })
}

export function fetchReguas() {
  return apiRequest('/api/retorno/reguas')
}

export function atualizarRegua(id, dados) {
  return apiRequest(`/api/retorno/reguas/${id}`, { method: 'PUT', body: JSON.stringify(dados) })
}

export function fetchModelos() {
  return apiRequest('/api/retorno/modelos')
}

export function atualizarModelo(grupo, texto) {
  return apiRequest(`/api/retorno/modelos/${grupo}`, { method: 'PUT', body: JSON.stringify({ texto }) })
}

export function fetchResultados(inicio, fim) {
  const params = new URLSearchParams()
  if (inicio) params.set('inicio', inicio)
  if (fim) params.set('fim', fim)
  const qs = params.toString()
  return apiRequest(`/api/retorno/resultados${qs ? `?${qs}` : ''}`)
}

export function fetchOportunidades({ status = 'abertos', busca = '', pagina = 0, fila = 'prioridades', responsavel = '' } = {}) {
  const params = new URLSearchParams({ status, busca, pagina: String(pagina), fila })
  if (responsavel) params.set('responsavel', responsavel)
  return apiRequest(`/api/pos-venda?${params}`)
}

export function fetchResponsaveisPosVenda() {
  return apiRequest('/api/pos-venda/responsaveis')
}

export function fetchOportunidade(id) {
  return apiRequest(`/api/pos-venda/${id}`)
}

export function salvarAcaoOportunidade(id, dados) {
  return apiRequest(`/api/pos-venda/${id}/acoes`, { method: 'POST', body: JSON.stringify(dados) })
}

export function previewImportacaoCsv(arquivo, fuso = 'America/Fortaleza') {
  const body = new FormData()
  body.append('arquivo', arquivo)
  return apiRequest(`/api/pos-venda/importacoes/csv/preview?fuso=${encodeURIComponent(fuso)}`, { method: 'POST', body })
}

export function importarCsv(arquivo, configuracao) {
  const body = new FormData()
  body.append('arquivo', arquivo)
  body.append('configuracao', new Blob([JSON.stringify(configuracao)], { type: 'application/json' }))
  return apiRequest('/api/pos-venda/importacoes/csv', { method: 'POST', body })
}

export function fetchImportacoes() {
  return apiRequest('/api/pos-venda/importacoes')
}

export function fetchImportacao(id) {
  return apiRequest(`/api/pos-venda/importacoes/${id}`)
}
