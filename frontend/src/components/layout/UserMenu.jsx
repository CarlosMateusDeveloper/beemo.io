import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { User, Settings, HelpCircle, LogOut, ChevronsUpDown, Sun, Moon } from 'lucide-react'
import { useTheme } from '../../theme/ThemeContext'
import { useAuth } from '../../auth/AuthContext'
import './UserMenu.css'

const PERFIL_LABEL = { medico: 'Médico(a)', administrador: 'Administrador(a)' }

function getInitials(name) {
  return name.split(' ').map((w) => w[0]).slice(0, 2).join('').toUpperCase()
}

const MENU_ITEMS = [
  { key: 'perfil', label: 'Perfil', icon: User },
  { key: 'configuracoes', label: 'Configurações', icon: Settings },
  { key: 'ajuda', label: 'Ajuda', icon: HelpCircle },
]

export default function UserMenu() {
  const [open, setOpen] = useState(false)
  const containerRef = useRef(null)
  const navigate = useNavigate()
  const { theme, toggleTheme } = useTheme()
  const isDark = theme === 'dark'
  const { usuario, logout } = useAuth()

  useEffect(() => {
    function handleClickOutside(e) {
      if (containerRef.current && !containerRef.current.contains(e.target)) setOpen(false)
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  function handleSelect(key) {
    setOpen(false)
    if (key === 'sair') {
      logout()
      navigate('/login')
    }
  }

  if (!usuario) return null

  return (
    <div className="user-menu" ref={containerRef}>
      {open && (
        <div className="user-menu-popover">
          {MENU_ITEMS.map(({ key, label, icon: Icon }) => (
            <button key={key} className="user-menu-item" onClick={() => handleSelect(key)}>
              <Icon size={16} strokeWidth={2} />
              <span>{label}</span>
            </button>
          ))}
          <button className="user-menu-item" onClick={toggleTheme}>
            {isDark ? <Sun size={16} strokeWidth={2} /> : <Moon size={16} strokeWidth={2} />}
            <span>{isDark ? 'Modo claro' : 'Modo escuro'}</span>
          </button>
          <div className="user-menu-divider" />
          <button className="user-menu-item danger" onClick={() => handleSelect('sair')}>
            <LogOut size={16} strokeWidth={2} />
            <span>Sair</span>
          </button>
        </div>
      )}

      <button className="user-menu-trigger" onClick={() => setOpen((v) => !v)}>
        <div className="user-menu-avatar">{getInitials(usuario.nome)}</div>
        <div className="user-menu-info">
          <div className="user-menu-name">{usuario.nome}</div>
          <div className="user-menu-role">{PERFIL_LABEL[usuario.perfil] ?? usuario.perfil}</div>
        </div>
        <ChevronsUpDown size={14} strokeWidth={2} className="user-menu-chevron" />
      </button>
    </div>
  )
}
