import { Outlet } from 'react-router-dom'
import Sidebar from './Sidebar'
import './Layout.css'
import { useAuth } from '../../auth/AuthContext'

export default function Layout() {
  const { usuario } = useAuth()
  const support = usuario?.tenantAtivo?.suporteTemporario
  return (
    <div className="app-layout">
      <Sidebar />
      <div className="app-content">
        {support && <div className="support-access-banner" role="status">Acesso temporário de suporte · expira em {new Date(usuario.tenantAtivo.expiraEm).toLocaleString('pt-BR')} · todas as ações são auditadas</div>}
        <Outlet />
      </div>
    </div>
  )
}
