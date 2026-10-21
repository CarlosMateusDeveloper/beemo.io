import { useEffect, useState } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from './AuthContext'
import { apiRequest } from '../lib/apiClient'

export default function RotaSaas({ children }) {
  const { autenticado, carregando } = useAuth()
  const location = useLocation()
  const [state, setState] = useState('checking')
  useEffect(() => {
    if (!autenticado) return
    let active = true
    apiRequest('/api/saas/me').then(() => { if (active) setState('allowed') }).catch(() => { if (active) setState('denied') })
    return () => { active = false }
  }, [autenticado])
  if (carregando) return <main className="saas-loading">Verificando acesso interno…</main>
  if (!autenticado) return <Navigate to="/login" state={{ from: location.pathname + location.search }} replace />
  if (state === 'checking') return <main className="saas-loading">Verificando acesso interno…</main>
  if (state === 'denied') return <main className="saas-loading">Esta conta não possui acesso ao painel interno.</main>
  return children
}
