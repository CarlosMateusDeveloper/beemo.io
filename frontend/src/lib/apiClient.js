// Cliente HTTP único pro backend Java — todas as telas que falam com
// :8080 (dashboard, caixa, pacientes, prontuario, medicos, Agenda) usam
// isso em vez de duplicar `request()` local. Injeta o token salvo e, se a
// API responder 401/403 (token ausente/expirado), limpa a sessão e manda
// pro /login — sem isso, uma tela ficaria presa girando num "carregando"
// eterno quando o token vence.
const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080'
const TOKEN_KEY = 'clinicos-token'

export function getToken() {
  return window.localStorage.getItem(TOKEN_KEY)
}

export function setToken(token) {
  if (token) window.localStorage.setItem(TOKEN_KEY, token)
  else window.localStorage.removeItem(TOKEN_KEY)
}

// Setado pelo AuthProvider — evita import circular entre apiClient e o
// contexto de auth (que por sua vez usa apiClient pra chamar /api/auth/*).
let aoDeslogar = () => {}
export function aoReceberNaoAutenticado(callback) {
  aoDeslogar = callback
}

export async function apiRequest(path, options = {}) {
  const token = getToken()
  const res = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers,
    },
  })

  // 401/403 sem token nunca aconteceu por sessão expirada (não tinha sessão
  // pra expirar) — é o caso normal de "senha errada" no login, por exemplo.
  // Só desloga quando um token que a gente tinha parou de ser aceito.
  if ((res.status === 401 || res.status === 403) && token) {
    setToken(null)
    aoDeslogar()
    throw new Error('Sessão expirada. Faça login novamente.')
  }

  if (!res.ok) {
    const body = await res.json().catch(() => ({}))
    throw new Error(body.message || body.erro || `Erro ${res.status} em ${path}`)
  }
  if (res.status === 204) return null
  return res.json()
}
