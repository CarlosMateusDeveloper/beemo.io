import { useState } from 'react'
import { NavLink, useLocation } from 'react-router-dom'
import {
  LayoutDashboard, CalendarDays, Users, FileText, MessageCircle, Stethoscope, Wallet, HeartHandshake, Target, Workflow, Megaphone, CheckSquare, ChevronLeft, ChevronRight,
} from 'lucide-react'
import UserMenu from './UserMenu'
import ModuleSwitcher from './ModuleSwitcher'
import './Sidebar.css'

const NAV_ITEMS = [
  { to: '/', label: 'Dashboard', icon: LayoutDashboard, end: true },
  { to: '/agenda', label: 'Agenda', icon: CalendarDays },
  { to: '/pacientes', label: 'Pacientes', icon: Users },
  { to: '/prontuario', label: 'Prontuários', icon: FileText },
  { to: '/whatsapp', label: 'WhatsApp', icon: MessageCircle },
  { to: '/medicos', label: 'Médicos', icon: Stethoscope },
  {
    to: '/caixa', label: 'Caixa', icon: Wallet, end: true,
    children: [
      { to: '/caixa/dre', label: 'DRE' },
      { to: '/caixa/fluxo-consolidado', label: 'Fluxo de caixa' },
      { to: '/caixa/despesas', label: 'Despesas' },
    ],
  },
  { to: '/convenios', label: 'Convênios', icon: HeartHandshake },
]

const POS_VENDA_NAV_ITEMS = [
  { to: '/pos-venda', label: 'Dashboard', icon: LayoutDashboard, end: true },
  { to: '/pos-venda/oportunidades', label: 'Oportunidades', icon: Target },
  { to: '/pos-venda/pacientes', label: 'Pacientes', icon: Users },
  { to: '/pos-venda/whatsapp', label: 'WhatsApp', icon: MessageCircle },
  { to: '/pos-venda/tarefas', label: 'Tarefas', icon: CheckSquare },
  { to: '/pos-venda/jornadas', label: 'Jornadas', icon: Workflow },
  { to: '/pos-venda/campanhas', label: 'Campanhas', icon: Megaphone },
]

const COLLAPSE_STORAGE_KEY = 'sidebar-collapsed'

export default function Sidebar() {
  const { pathname } = useLocation()
  const activeModule = pathname === '/pos-venda' || pathname.startsWith('/pos-venda/') ? 'pos-venda' : 'clinicos'
  const navigation = activeModule === 'clinicos' ? NAV_ITEMS : POS_VENDA_NAV_ITEMS
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
        <ModuleSwitcher key={activeModule} activeModule={activeModule} />
      </div>
      <ul className="sidebar-nav">
        {navigation.map(({ to, label, icon: Icon, end, children }) => (
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
            {children && (
              <ul className="sidebar-sublist">
                {children.map((child) => (
                  <li key={child.to}>
                    <NavLink
                      to={child.to}
                      className={({ isActive }) => `sidebar-sublink${isActive ? ' active' : ''}`}
                      title={collapsed ? child.label : undefined}
                    >
                      <span>{child.label}</span>
                    </NavLink>
                  </li>
                ))}
              </ul>
            )}
          </li>
        ))}
      </ul>
      <UserMenu />
    </nav>
  )
}
