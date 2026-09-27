import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from './AuthContext'

// Envolve o Layout: sem usuário autenticado, manda pro /login. Enquanto
// ainda não sabemos (checando o token salvo em GET /api/auth/me), não
// redireciona — evita um flash de /login em quem já tem sessão válida.
export default function RotaProtegida({ children }) {
  const location = useLocation()
  const { autenticado, carregando } = useAuth()

  if (carregando) return <div className="login-page" role="status">Verificando sessão…</div>
  if (!autenticado) return <Navigate to="/login" state={{ from: location.pathname + location.search }} replace />
  return children || <Outlet />
}
