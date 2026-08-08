function getInitials(name) {
  return name
    .replace(/^Dra?\.\s*/, '')
    .split(' ')
    .map((w) => w[0])
    .slice(0, 2)
    .join('')
    .toUpperCase()
}

const VIEWS = [
  { id: 'day', label: 'Dia' },
  { id: 'week', label: 'Semana' },
  { id: 'month', label: 'Mês' },
]

export default function Topbar({
  headerTitle, view, onViewChange, onPrev, onNext, onToday,
  professionals, profId, onProfChange, specialties, specialty, onSpecialtyChange, onCreateClick,
}) {
  const selectedProf = professionals.find((p) => p.id === profId)

  return (
    <header className="agenda-topbar">
      <div className="agenda-nav-group">
        <button className="agenda-icon-btn" onClick={onPrev} aria-label="Período anterior">‹</button>
        <button className="agenda-icon-btn" onClick={onNext} aria-label="Próximo período">›</button>
        <button className="agenda-today-btn" onClick={onToday}>Hoje</button>
      </div>

      <div className="agenda-title">{headerTitle}</div>
      <div className="agenda-spacer" />

      <div className="agenda-view-switch">
        {VIEWS.map((v) => (
          <button
            key={v.id}
            className={`agenda-view-btn${view === v.id ? ' active' : ''}`}
            onClick={() => onViewChange(v.id)}
          >
            {v.label}
          </button>
        ))}
      </div>

      <div className="agenda-specialty-picker">
        <select
          className="agenda-specialty-select"
          value={specialty}
          onChange={(e) => onSpecialtyChange(e.target.value)}
          aria-label="Filtrar por especialidade"
        >
          <option value="">Todas especialidades</option>
          {specialties.map((s) => (
            <option key={s} value={s}>{s}</option>
          ))}
        </select>
      </div>

      <div className="agenda-prof-picker">
        <div className="agenda-prof-avatar">{selectedProf ? getInitials(selectedProf.name) : ''}</div>
        <select className="agenda-prof-select" value={profId} onChange={(e) => onProfChange(e.target.value)}>
          {professionals.map((p) => (
            <option key={p.id} value={p.id}>{p.name}</option>
          ))}
        </select>
      </div>

      <button className="agenda-create-btn" onClick={onCreateClick}>+ Nova consulta</button>
    </header>
  )
}
