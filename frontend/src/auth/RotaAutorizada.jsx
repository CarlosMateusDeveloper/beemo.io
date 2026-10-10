import { Link } from 'react-router-dom'
import { useAuth } from './AuthContext'
import { hasPermission } from './permissions'

export function AcessoNegado() {
  return <main className="access-denied" role="alert">
    <div>
      <span>403</span>
      <h1>Acesso não autorizado</h1>
      <p>Seu papel nesta clínica não permite abrir esta página.</p>
      <Link to="/dashboard">Voltar ao início</Link>
    </div>
  </main>
}

export default function RotaAutorizada({ permission, children }) {
  const { usuario } = useAuth()
  return hasPermission(usuario, permission) ? children : <AcessoNegado />
}
