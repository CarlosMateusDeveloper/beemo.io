import { useEffect, useState } from 'react'
import { Pencil } from 'lucide-react'
import { DOW_FULL, MONTHS, STATUS, STATUS_TRANSITIONS } from './constants'
import { minutesToTime, parseDateKey, timeToMinutes } from './dateUtils'
import AppointmentFields from './AppointmentFields'

const ACTION_CLASS = {
  positive: 'agenda-action-complete',
  negative: 'agenda-action-missed',
  neutral: 'agenda-action-cancel',
}

export default function DetailModal({ appointment, professionalName, professionalSpecialty, pacientes, onClose, onChangeStatus, onEdit }) {
  const [editing, setEditing] = useState(false)
  const [editForm, setEditForm] = useState(null)

  useEffect(() => {
    setEditing(false)
  }, [appointment?.id])

  if (!appointment) return null

  const status = STATUS[appointment.status]
  const date = parseDateKey(appointment.dateKey)
  const endLabel = minutesToTime(timeToMinutes(appointment.start) + appointment.dur)
  const transitions = STATUS_TRANSITIONS[appointment.status] ?? []

  function startEditing() {
    setEditForm({ idPaciente: appointment.idPaciente, type: appointment.type, dur: appointment.dur })
    setEditing(true)
  }

  function saveEdit() {
    onEdit(appointment.id, editForm)
    setEditing(false)
  }

  return (
    <div className="agenda-overlay" onClick={onClose}>
      <div className="agenda-modal detail" onClick={(e) => e.stopPropagation()}>
        <div className="agenda-detail-header">
          {editing ? (
            <span className="agenda-status-badge" style={{ background: 'var(--surface-muted)', color: 'var(--text-secondary)' }}>Editando</span>
          ) : (
            <span className="agenda-status-badge" style={{ background: status.bg, color: status.fg }}>{status.label}</span>
          )}
          <div style={{ display: 'flex', gap: 4 }}>
            {!editing && (
              <button className="agenda-close-btn" onClick={startEditing} aria-label="Editar consulta" title="Editar consulta">
                <Pencil size={16} strokeWidth={2} />
              </button>
            )}
            <button className="agenda-close-btn" onClick={onClose} aria-label="Fechar">×</button>
          </div>
        </div>

        {editing ? (
          <>
            <AppointmentFields form={editForm} onChange={setEditForm} idPrefix="agenda-edit" pacientes={pacientes} />
            <div className="agenda-modal-footer">
              <button className="agenda-btn-ghost" onClick={() => setEditing(false)}>Cancelar</button>
              <button className="agenda-btn-primary" onClick={saveEdit}>Salvar</button>
            </div>
          </>
        ) : (
          <>
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

            {transitions.length > 0 ? (
              <div className="agenda-detail-actions">
                {transitions.map((action) => (
                  <button
                    key={action.status}
                    className={`agenda-detail-action ${ACTION_CLASS[action.kind]}`}
                    onClick={() => onChangeStatus(appointment.id, action.status)}
                  >
                    {action.label}
                  </button>
                ))}
              </div>
            ) : (
              <div className="agenda-hint">Consulta finalizada — sem novas ações de status.</div>
            )}
          </>
        )}
      </div>
    </div>
  )
}
