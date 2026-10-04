import { useEffect, useId, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { Check, ChevronsUpDown, HeartHandshake, LayoutDashboard, Wallet } from 'lucide-react'
import './ModuleSwitcher.css'

const MODULES = [
  { id: 'clinicos', label: 'ClinicOS', description: 'Gestão da clínica', to: '/', icon: LayoutDashboard },
  { id: 'pos-venda', label: 'Pós-venda', description: 'Pós-atendimento e retenção', to: '/pos-venda', icon: HeartHandshake },
  { id: 'financeiro', label: 'Financeiro', description: 'Caixa, resultados e despesas', to: '/caixa', icon: Wallet },
]

export default function ModuleSwitcher({ activeModule }) {
  const [open, setOpen] = useState(false)
  const container = useRef(null)
  const trigger = useRef(null)
  const popoverId = useId()
  const current = MODULES.find(module => module.id === activeModule) || MODULES[0]
  const CurrentIcon = current.icon

  useEffect(() => {
    if (!open) return
    function outside(event) {
      if (!container.current?.contains(event.target)) setOpen(false)
    }
    function escape(event) {
      if (event.key === 'Escape') {
        setOpen(false)
        trigger.current?.focus()
      }
    }
    document.addEventListener('pointerdown', outside)
    document.addEventListener('keydown', escape)
    return () => {
      document.removeEventListener('pointerdown', outside)
      document.removeEventListener('keydown', escape)
    }
  }, [open])

  return <div className="module-switcher" ref={container} onBlur={event => {
    if (!event.currentTarget.contains(event.relatedTarget)) setOpen(false)
  }}>
    <button ref={trigger} type="button" className="module-switcher-trigger" aria-expanded={open}
      aria-controls={popoverId} aria-label={`Módulo ${current.label}. Trocar módulo`} title="Trocar módulo"
      onClick={() => setOpen(value => !value)}>
      <CurrentIcon className="module-switcher-icon" size={20} aria-hidden="true" />
      <span className="module-switcher-label">{current.label}</span>
      <ChevronsUpDown className="module-switcher-chevron" size={15} aria-hidden="true" />
    </button>
    {open && <nav id={popoverId} className="module-switcher-popover" aria-label="Módulos do sistema">
      <p className="module-switcher-heading">Módulos</p>
      {MODULES.map(({ id, label, description, to, icon: Icon }) => <Link key={id} to={to}
        className={`module-switcher-option${id === activeModule ? ' selected' : ''}`}
        aria-current={id === activeModule ? 'true' : undefined} onClick={() => setOpen(false)}>
        <Icon size={19} aria-hidden="true" />
        <span><strong>{label}</strong><small>{description}</small></span>
        {id === activeModule && <Check size={16} aria-hidden="true" />}
      </Link>)}
    </nav>}
  </div>
}
