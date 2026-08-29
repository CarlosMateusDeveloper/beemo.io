import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from './AuthContext'

// Envolve o Layout: sem usuário autenticado, manda pro /login. Enquanto
// ainda não sabemos (checando o token salvo em GET /api/auth/me), não
// redireciona — evita um flash de /login em quem já tem sessão válida.
export default function RotaProtegida() {
  const { autenticado, carregando } = useAuth()

  if (carregando) return null
  if (!autenticado) return <Navigate to="/login" replace />
  return <Outlet />
}
