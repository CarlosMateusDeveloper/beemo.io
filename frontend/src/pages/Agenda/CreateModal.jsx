import { DOW_FULL, MONTHS } from './constants'
import { minutesToTime } from './dateUtils'

export default function CreateModal({ createAt, form, professionalName, onChange, onCancel, onSave }) {
  if (!createAt) return null

  const { date, minute } = createAt
  const endLabel = minutesToTime(minute + form.dur)
  const subtitle = `${DOW_FULL[date.getDay()]}, ${date.getDate()} de ${MONTHS[date.getMonth()]} - ${minutesToTime(minute)} - ${endLabel} - ${professionalName}`

  return (
    <div className="agenda-overlay" onClick={onCancel}>
      <div className="agenda-modal create" onClick={(e) => e.stopPropagation()}>
        <div className="agenda-modal-title">Nova consulta</div>
        <div className="agenda-modal-subtitle">{subtitle}</div>

        <div className="agenda-form">
          <div>
            <label className="agenda-label" htmlFor="agenda-form-patient">Paciente</label>
            <input
              id="agenda-form-patient"
              className="agenda-input"
              placeholder="Nome do paciente"
              value={form.patient}
              onChange={(e) => onChange({ ...form, patient: e.target.value })}
            />
          </div>

          <div className="agenda-form-row">
            <div>
              <label className="agenda-label" htmlFor="agenda-form-type">Tipo</label>
              <select
                id="agenda-form-type"
                className="agenda-select"
                value={form.type}
                onChange={(e) => onChange({ ...form, type: e.target.value })}
              >
                <option value="Consulta">Consulta</option>
                <option value="Retorno">Retorno</option>
                <option value="Exame">Exame</option>
                <option value="Avaliação">Avaliação</option>
              </select>
            </div>
            <div>
              <label className="agenda-label" htmlFor="agenda-form-dur">Duração</label>
              <select
                id="agenda-form-dur"
                className="agenda-select"
                value={form.dur}
                onChange={(e) => onChange({ ...form, dur: Number(e.target.value) })}
              >
                <option value={30}>30 min</option>
                <option value={45}>45 min</option>
                <option value={60}>60 min</option>
              </select>
            </div>
          </div>

          <div>
            <label className="agenda-label" htmlFor="agenda-form-status">Status inicial</label>
            <select
              id="agenda-form-status"
              className="agenda-select"
              value={form.status}
              onChange={(e) => onChange({ ...form, status: e.target.value })}
            >
              <option value="confirmado">Confirmada</option>
              <option value="pendente">Pendente de confirmação</option>
            </select>
          </div>
        </div>

        <div className="agenda-modal-footer">
          <button className="agenda-btn-ghost" onClick={onCancel}>Cancelar</button>
          <button className="agenda-btn-primary" onClick={onSave}>Agendar</button>
        </div>
      </div>
    </div>
  )
}
