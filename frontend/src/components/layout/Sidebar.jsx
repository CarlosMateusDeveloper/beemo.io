import { NavLink } from 'react-router-dom'
import {
  LayoutDashboard, CalendarDays, Users, MessageCircle, Stethoscope, FlaskConical, Wallet, HeartHandshake,
} from 'lucide-react'
import UserMenu from './UserMenu'
import ThemeToggle from '../../theme/ThemeToggle'
import './Sidebar.css'

const NAV_ITEMS = [
  { to: '/', label: 'Dashboard', icon: LayoutDashboard, end: true },
  { to: '/agenda', label: 'Agenda', icon: CalendarDays },
  { to: '/pacientes', label: 'Pacientes', icon: Users },
  { to: '/whatsapp', label: 'WhatsApp', icon: MessageCircle },
  { to: '/medicos', label: 'Médicos', icon: Stethoscope },
  { to: '/exames', label: 'Exames', icon: FlaskConical },
  { to: '/financeiro', label: 'Financeiro', icon: Wallet },
  { to: '/convenios', label: 'Convênios', icon: HeartHandshake },
]

export default function Sidebar() {
  return (
    <nav className="sidebar" aria-label="Navegação principal">
      <div className="sidebar-header">
        <div className="sidebar-brand">ClinicOS</div>
        <ThemeToggle />
      </div>
      <ul className="sidebar-nav">
        {NAV_ITEMS.map(({ to, label, icon: Icon, end }) => (
          <li key={to}>
            <NavLink
              to={to}
              end={end}
              className={({ isActive }) => `sidebar-link${isActive ? ' active' : ''}`}
            >
              <Icon size={18} strokeWidth={2} />
              <span>{label}</span>
            </NavLink>
          </li>
        ))}
      </ul>
      <UserMenu />
    </nav>
  )
}
