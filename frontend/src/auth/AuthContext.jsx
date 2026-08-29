import { createContext, useContext, useEffect, useMemo, useState } from 'react'
import { apiRequest, aoReceberNaoAutenticado, getToken, setToken } from '../lib/apiClient'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [usuario, setUsuario] = useState(null)
  // null = ainda não sabemos (checando token salvo); depois vira true/false.
  const [carregando, setCarregando] = useState(true)

  useEffect(() => {
    aoReceberNaoAutenticado(() => setUsuario(null))
  }, [])

  useEffect(() => {
    if (!getToken()) { setCarregando(false); return }
    apiRequest('/api/auth/me')
      .then((dados) => setUsuario(dados))
      .catch(() => {}) // apiRequest já limpou o token se era isso
      .finally(() => setCarregando(false))
  }, [])

  async function login(email, senha) {
    const resp = await apiRequest('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, senha }),
    })
    setToken(resp.token)
    setUsuario(resp.usuario)
  }

  function logout() {
    setToken(null)
    setUsuario(null)
  }

  const value = useMemo(() => ({
    usuario, carregando, autenticado: !!usuario, login, logout,
  }), [usuario, carregando])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth deve ser usado dentro de um AuthProvider')
  return ctx
}
