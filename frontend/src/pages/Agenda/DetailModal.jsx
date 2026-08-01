import { DOW_FULL, MONTHS, STATUS } from './constants'
import { minutesToTime, parseDateKey, timeToMinutes } from './dateUtils'

const ACTIONS = [
  { status: 'concluido', label: 'Concluir', className: 'agenda-action-complete' },
  { status: 'faltou', label: 'Marcar falta', className: 'agenda-action-missed' },
  { status: 'cancelado', label: 'Cancelar', className: 'agenda-action-cancel' },
]

export default function DetailModal({ appointment, professionalName, professionalSpecialty, onClose, onChangeStatus }) {
  if (!appointment) return null

  const status = STATUS[appointment.status]
  const date = parseDateKey(appointment.dateKey)
  const endLabel = minutesToTime(timeToMinutes(appointment.start) + appointment.dur)
  const availableActions = ACTIONS.filter((a) => a.status !== appointment.status)

  return (
    <div className="agenda-overlay" onClick={onClose}>
      <div className="agenda-modal detail" onClick={(e) => e.stopPropagation()}>
        <div className="agenda-detail-header">
          <span className="agenda-status-badge" style={{ background: status.bg, color: status.fg }}>{status.label}</span>
          <button className="agenda-close-btn" onClick={onClose} aria-label="Fechar">×</button>
        </div>

        <div className="agenda-detail-patient">{appointment.patient}</div>
        <div className="agenda-detail-type">{appointment.type} - {professionalSpecialty}</div>

        <div className="agenda-detail-fields">
          <div className="agenda-detail-field">
            <span className="agenda-detail-field-label">Data</span>
            <span className="agenda-detail-field-value">{DOW_FULL[date.getDay()]}, {date.getDate()} de {MONTHS[date.getMonth()]}</span>
          </div>
          <div className="agenda-detail-field">
            <span className="agenda-detail-field-label">Horário</span>
            <span className="agenda-detail-field-value mono">{appointment.start} - {endLabel} ({appointment.dur} min)</span>
          </div>
          <div className="agenda-detail-field">
            <span className="agenda-detail-field-label">Profissional</span>
            <span className="agenda-detail-field-value">{professionalName}</span>
          </div>
        </div>

        <div className="agenda-hint">Dica: arraste o card na agenda para reagendar.</div>

        <div className="agenda-detail-actions">
          {availableActions.map((action) => (
            <button
              key={action.status}
              className={`agenda-detail-action ${action.className}`}
              onClick={() => onChangeStatus(appointment.id, action.status)}
            >
              {action.label}
            </button>
          ))}
        </div>
      </div>
    </div>
  )
}
