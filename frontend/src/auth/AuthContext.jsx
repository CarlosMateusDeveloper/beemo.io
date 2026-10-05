/* eslint-disable react-refresh/only-export-components */
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react'
import { apiRequest, aoReceberNaoAutenticado, aoReceberTenantObrigatorio, sessaoAtualizada, geracaoSessao } from '../lib/apiClient'

const AuthContext = createContext(null)
export function AuthProvider({ children }) {
  const [usuario, setUsuario] = useState(null)
  const [carregando, setCarregando] = useState(true)
  const channel = useRef(null)
  useEffect(() => aoReceberNaoAutenticado(() => setUsuario(null)), [])
  useEffect(() => aoReceberTenantObrigatorio(() => setUsuario(u => u ? { ...u, tenantAtivo: null, perfil: null } : u)), [])
  useEffect(() => {
    let ativo = true
    // Remove apenas o token legado; a sessão atual fica em cookie HttpOnly.
    window.localStorage.removeItem('clinicos-token')
    async function restaurar() {
      const generation = geracaoSessao()
      try {
        const dados = await apiRequest('/api/auth/me', { authenticated: false })
        if (ativo && generation === geracaoSessao()) setUsuario(dados)
      } catch { if (ativo && generation === geracaoSessao()) setUsuario(null) }
      finally { if (ativo) setCarregando(false) }
    }
    if (typeof BroadcastChannel !== 'undefined') {
      channel.current = new BroadcastChannel('clinicos-auth')
      channel.current.onmessage = event => {
        if (event.data === 'tenant-changed') { window.location.reload(); return }
        sessaoAtualizada(); restaurar()
      }
    }
    restaurar()
    return () => { ativo = false; channel.current?.close(); channel.current = null }
  }, [])
  const autenticar = useCallback(async (path, payload) => {
    const resp = await apiRequest(path, { method: 'POST', authenticated: false, body: JSON.stringify(payload) })
    sessaoAtualizada(); setUsuario(resp.usuario); channel.current?.postMessage('changed')
  }, [])
  const login = useCallback((email, senha) => autenticar('/api/auth/login', { email, senha }), [autenticar])
  const entrarComLink = useCallback(token => autenticar('/api/auth/magic-link/verify', { token }), [autenticar])
  const logout = useCallback(async () => {
    await apiRequest('/api/auth/logout', { method: 'POST', authenticated: false })
    sessaoAtualizada(); setUsuario(null); channel.current?.postMessage('changed')
  }, [])
  const value = useMemo(() => ({ usuario, carregando, autenticado: !!usuario, login, entrarComLink, logout }),
    [usuario, carregando, login, entrarComLink, logout])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth deve ser usado dentro de um AuthProvider')
  return ctx
}
