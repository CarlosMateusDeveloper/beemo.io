import { DOW_FULL, MONTHS } from './constants'
import { minutesToTime } from './dateUtils'
import AppointmentFields from './AppointmentFields'

export default function CreateModal({ createAt, form, professionalName, pacientes, onChange, onCancel, onSave }) {
  if (!createAt) return null

  const { date, minute } = createAt
  const endLabel = minutesToTime(minute + form.dur)
  const subtitle = `${DOW_FULL[date.getDay()]}, ${date.getDate()} de ${MONTHS[date.getMonth()]} - ${minutesToTime(minute)} - ${endLabel} - ${professionalName}`

  return (
    <div className="agenda-overlay" onClick={onCancel}>
      <div className="agenda-modal create" onClick={(e) => e.stopPropagation()}>
        <div className="agenda-modal-title">Nova consulta</div>
        <div className="agenda-modal-subtitle">{subtitle}</div>

        <AppointmentFields form={form} onChange={onChange} idPrefix="agenda-create" pacientes={pacientes} />

        <div style={{ marginTop: 13 }}>
          <label className="agenda-label" htmlFor="agenda-create-status">Status inicial</label>
          <select
            id="agenda-create-status"
            className="agenda-select"
            value={form.status}
            onChange={(e) => onChange({ ...form, status: e.target.value })}
          >
            <option value="Agendada">Agendada</option>
            <option value="Confirmada">Confirmada</option>
          </select>
        </div>

        <div className="agenda-modal-footer">
          <button className="agenda-btn-ghost" onClick={onCancel}>Cancelar</button>
          <button className="agenda-btn-primary" onClick={onSave} disabled={!form.idPaciente}>Agendar</button>
        </div>
      </div>
    </div>
  )
}
