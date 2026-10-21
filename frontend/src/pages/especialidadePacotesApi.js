import { apiRequest } from '../lib/apiClient'

export const fetchPacotesEspecialidade = () => apiRequest('/api/configuracoes/especialidades/pacotes')
export const fetchRevisoresEspecialidade = () => apiRequest('/api/configuracoes/especialidades/revisores')
export const criarVersaoPacoteEspecialidade = (packageId, components) => apiRequest(`/api/configuracoes/especialidades/pacotes/${packageId}/versoes`, { method: 'POST', body: JSON.stringify({ componentes: components }) })
export const enviarVersaoParaRevisao = (versionId) => apiRequest(`/api/configuracoes/especialidades/versoes/${versionId}/revisao`, { method: 'POST' })
export const previewPacoteEspecialidade = (versionId, components) => {
  const query = new URLSearchParams()
  components.forEach((key) => query.append('componente', key))
  return apiRequest(`/api/configuracoes/especialidades/versoes/${versionId}/preview?${query}`)
}
export const publicarPacoteEspecialidade = (versionId, data) => apiRequest(`/api/configuracoes/especialidades/versoes/${versionId}/publicar`, { method: 'POST', body: JSON.stringify(data) })
export const aplicarPacoteEspecialidade = (versionId, data) => apiRequest(`/api/configuracoes/especialidades/versoes/${versionId}/aplicar`, { method: 'POST', body: JSON.stringify(data) })
export const desativarPacoteEspecialidade = (applicationId, version) => apiRequest(`/api/configuracoes/especialidades/aplicacoes/${applicationId}/desativar`, { method: 'POST', body: JSON.stringify({ versaoLock: version }) })
export const reverterPacoteEspecialidade = (applicationId, version) => apiRequest(`/api/configuracoes/especialidades/aplicacoes/${applicationId}/reverter`, { method: 'POST', body: JSON.stringify({ versaoLock: version }) })
