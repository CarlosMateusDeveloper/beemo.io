export function serviceBase(configured, port) {
  if (configured) return configured.replace(/\/$/, '')
  const hostname = globalThis.window?.location?.hostname || 'localhost'
  if (!['localhost', '127.0.0.1'].includes(hostname)) return globalThis.window.location.origin + (port === 8080 ? '' : port === 8081 ? '/agenda-api' : '/chatbot-api')
  return `http://${hostname}:${port}`
}
export const API_BASE = serviceBase(import.meta.env?.VITE_API_URL, 8080)
let csrfPromise
let generation = 0
let aoDeslogar = () => {}
let aoPerderTenant = () => {}
export function aoReceberTenantObrigatorio(callback) { aoPerderTenant = callback; return () => { if (aoPerderTenant === callback) aoPerderTenant = () => {} } }
export function sessaoAtualizada() { generation++; csrfPromise = undefined; return generation }
export function geracaoSessao() { return generation }
export function aoReceberNaoAutenticado(callback) {
  aoDeslogar = callback
  return () => { if (aoDeslogar === callback) aoDeslogar = () => {} }
}
async function csrfToken() {
  if (!csrfPromise) {
    csrfPromise = fetch(`${API_BASE}/api/auth/csrf`, { credentials: 'include' })
      .then(async res => { if (!res.ok) throw new Error('Não foi possível preparar o acesso seguro.'); return res.json() })
      .catch(() => { csrfPromise = undefined; throw new Error('Não foi possível preparar o acesso seguro. Verifique a conexão e tente novamente.') })
  }
  return (await csrfPromise).token
}
export function apiRequest(path, options = {}) { return authenticatedRequest(`${API_BASE}${path}`, options) }
export async function authenticatedRequest(url, options = {}) {
  const { authenticated = true, ...fetchOptions } = options
  const initialGeneration = generation
  const mutation = !['GET', 'HEAD', 'OPTIONS'].includes((options.method || 'GET').toUpperCase())
  for (let attempt = 0; attempt < 2; attempt++) {
    const headers = new Headers(options.headers)
    if (!headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
    if (mutation) headers.set('X-CSRF-TOKEN', await csrfToken())
    const res = await fetch(url, { ...fetchOptions, credentials: 'include', headers })
      .catch(() => { throw new Error('Não foi possível conectar ao clinicOS. Verifique sua conexão e tente novamente.') })
    if (res.status === 401 && authenticated && initialGeneration === generation) {
      sessaoAtualizada(); aoDeslogar()
      throw new Error('Sessão expirada. Faça login novamente.')
    }
    if (res.status === 204) return null
    const body = await res.json().catch(() => ({}))
    if (res.status === 403 && body.code === 'csrf_invalid' && mutation && attempt === 0) {
      csrfPromise = undefined
      continue
    }
    if (res.status === 403 && body.code === 'tenant_required' && initialGeneration === generation) aoPerderTenant()
    if (!res.ok) throw new Error(body.message || body.erro || (typeof body.detail === 'string' ? body.detail : null) || (res.status === 403 ? 'Seu perfil não tem permissão para esta ação.' : `Não foi possível concluir a solicitação (${res.status}).`))
    return body
  }
}
