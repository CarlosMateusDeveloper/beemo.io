import { useState } from 'react'
import { NavLink } from 'react-router-dom'
import {
  LayoutDashboard, CalendarDays, Users, FileText, Repeat, MessageCircle, Stethoscope, Wallet, HeartHandshake, ChevronLeft, ChevronRight,
} from 'lucide-react'
import UserMenu from './UserMenu'
import './Sidebar.css'

const NAV_ITEMS = [
  { to: '/', label: 'Dashboard', icon: LayoutDashboard, end: true },
  { to: '/agenda', label: 'Agenda', icon: CalendarDays },
  { to: '/pacientes', label: 'Pacientes', icon: Users },
  { to: '/prontuario', label: 'Prontuários', icon: FileText },
  { to: '/retorno', label: 'Retorno', icon: Repeat },
  { to: '/whatsapp', label: 'WhatsApp', icon: MessageCircle },
  { to: '/medicos', label: 'Médicos', icon: Stethoscope },
  { to: '/caixa', label: 'Caixa', icon: Wallet },
  { to: '/convenios', label: 'Convênios', icon: HeartHandshake },
]

const COLLAPSE_STORAGE_KEY = 'sidebar-collapsed'

export default function Sidebar() {
  const [collapsed, setCollapsed] = useState(() => localStorage.getItem(COLLAPSE_STORAGE_KEY) === '1')

  function toggleCollapsed() {
    setCollapsed((prev) => {
      const next = !prev
      localStorage.setItem(COLLAPSE_STORAGE_KEY, next ? '1' : '0')
      return next
    })
  }

  return (
    <nav className={`sidebar${collapsed ? ' collapsed' : ''}`} aria-label="Navegação principal">
      <button
        type="button"
        className="sidebar-collapse-btn"
        onClick={toggleCollapsed}
        aria-label={collapsed ? 'Expandir menu' : 'Recolher menu'}
        title={collapsed ? 'Expandir menu' : 'Recolher menu'}
      >
        {collapsed ? <ChevronRight size={14} strokeWidth={2.5} /> : <ChevronLeft size={14} strokeWidth={2.5} />}
      </button>

      <div className="sidebar-header">
        <div className="sidebar-brand">ClinicOS</div>
      </div>
      <ul className="sidebar-nav">
        {NAV_ITEMS.map(({ to, label, icon: Icon, end }) => (
          <li key={to}>
            <NavLink
              to={to}
              end={end}
              className={({ isActive }) => `sidebar-link${isActive ? ' active' : ''}`}
              title={collapsed ? label : undefined}
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
