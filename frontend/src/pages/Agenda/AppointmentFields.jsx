import { APPOINTMENT_TYPES } from './constants'

// Campos compartilhados entre o formulário de criação (CreateModal) e o
// modo de edição do DetailModal — evita duplicar o mesmo markup nos dois.
export default function AppointmentFields({ form, onChange, idPrefix }) {
  return (
    <div className="agenda-form">
      <div>
        <label className="agenda-label" htmlFor={`${idPrefix}-patient`}>Paciente</label>
        <input
          id={`${idPrefix}-patient`}
          className="agenda-input"
          placeholder="Nome do paciente"
          value={form.patient}
          onChange={(e) => onChange({ ...form, patient: e.target.value })}
        />
      </div>

      <div className="agenda-form-row">
        <div>
          <label className="agenda-label" htmlFor={`${idPrefix}-type`}>Tipo</label>
          <select
            id={`${idPrefix}-type`}
            className="agenda-select"
            value={form.type}
            onChange={(e) => onChange({ ...form, type: e.target.value })}
          >
            {APPOINTMENT_TYPES.map((type) => (
              <option key={type} value={type}>{type}</option>
            ))}
          </select>
        </div>
        <div>
          <label className="agenda-label" htmlFor={`${idPrefix}-dur`}>Duração</label>
          <select
            id={`${idPrefix}-dur`}
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
    </div>
  )
}
