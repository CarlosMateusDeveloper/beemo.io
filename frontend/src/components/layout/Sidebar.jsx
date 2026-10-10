import { useState } from 'react'
import { NavLink, useLocation } from 'react-router-dom'
import {
  LayoutDashboard, CalendarDays, Users, FileText, MessageCircle, Stethoscope, Wallet,
  HeartHandshake, Target, Workflow, Megaphone, CheckSquare, ChevronLeft, ChevronRight,
  BarChart3, ArrowUpDown, Receipt,
} from 'lucide-react'
import UserMenu from './UserMenu'
import ModuleSwitcher from './ModuleSwitcher'
import { usePermissions } from '../../auth/permissions'
import './Sidebar.css'

const NAV_ITEMS = [
  { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard, end: true, permissions: ['dashboard.operacional.visualizar', 'dashboard.financeiro.visualizar'] },
  { to: '/agenda', label: 'Agenda', icon: CalendarDays, permissions: ['agenda.visualizar'] },
  { to: '/pacientes', label: 'Pacientes', icon: Users, permissions: ['paciente.visualizar'], hideForDoctorOnly: true },
  { to: '/prontuario', label: 'Prontuários', icon: FileText, permissions: ['prontuario.visualizar'] },
  { to: '/whatsapp', label: 'WhatsApp', icon: MessageCircle, permissions: ['whatsapp.visualizar'] },
  { to: '/medicos', label: 'Médicos', icon: Stethoscope, permissions: ['medico.visualizar'] },
]

const POS_VENDA_NAV_ITEMS = [
  { to: '/pos-venda', label: 'Dashboard', icon: LayoutDashboard, end: true, permissions: ['oportunidade.visualizar'] },
  { to: '/pos-venda/oportunidades', label: 'Oportunidades', icon: Target, permissions: ['oportunidade.visualizar'] },
  { to: '/pos-venda/pacientes', label: 'Pacientes', icon: Users, permissions: ['oportunidade.visualizar'] },
  { to: '/pos-venda/whatsapp', label: 'WhatsApp', icon: MessageCircle, permissions: ['whatsapp.visualizar'] },
  { to: '/pos-venda/tarefas', label: 'Tarefas', icon: CheckSquare, permissions: ['tarefa.visualizar'] },
  { to: '/pos-venda/jornadas', label: 'Jornadas', icon: Workflow, permissions: ['jornada.executar', 'jornada.configurar'] },
  { to: '/pos-venda/campanhas', label: 'Campanhas', icon: Megaphone, permissions: ['campanha.executar', 'campanha.configurar'] },
]

const FINANCEIRO_NAV_ITEMS = [
  { to: '/caixa', label: 'Visão geral', icon: Wallet, end: true, permissions: ['caixa.visualizar'] },
  { to: '/caixa/dre', label: 'DRE', icon: BarChart3, permissions: ['financeiro.dre.visualizar'] },
  { to: '/caixa/fluxo-consolidado', label: 'Fluxo de caixa', icon: ArrowUpDown, permissions: ['financeiro.fluxo.visualizar'] },
  { to: '/caixa/despesas', label: 'Despesas', icon: Receipt, permissions: ['despesa.visualizar'] },
  { to: '/convenios', label: 'Convênios', icon: HeartHandshake, permissions: ['convenio.gerenciar'] },
]

const COLLAPSE_STORAGE_KEY = 'sidebar-collapsed'

export default function Sidebar() {
  const { pathname } = useLocation()
  const { canAny, roles } = usePermissions()
  const activeModule = pathname === '/pos-venda' || pathname.startsWith('/pos-venda/')
    ? 'pos-venda'
    : pathname === '/caixa' || pathname.startsWith('/caixa/') || pathname === '/convenios' || pathname.startsWith('/convenios/') ? 'financeiro' : 'clinicos'
  const allItems = activeModule === 'pos-venda'
    ? POS_VENDA_NAV_ITEMS
    : activeModule === 'financeiro' ? FINANCEIRO_NAV_ITEMS : NAV_ITEMS
  const doctorOnly = roles.length === 1 && roles[0] === 'medico'
  const navigation = allItems.filter(item => canAny(item.permissions) && !(item.hideForDoctorOnly && doctorOnly))
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
