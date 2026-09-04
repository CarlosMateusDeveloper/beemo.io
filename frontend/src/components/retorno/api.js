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
