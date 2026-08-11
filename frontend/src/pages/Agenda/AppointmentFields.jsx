import { APPOINTMENT_TYPES } from './constants'

// Campos compartilhados entre o formulário de criação (CreateModal) e o
// modo de edição do DetailModal — evita duplicar o mesmo markup nos dois.
// "Paciente" é um select de pacientes já cadastrados (não texto livre):
// consulta.id_paciente é uma FK obrigatória no banco, não dá pra criar uma
// consulta para um nome que não existe na tabela paciente.
export default function AppointmentFields({ form, onChange, idPrefix, pacientes }) {
  return (
    <div className="agenda-form">
      <div>
        <label className="agenda-label" htmlFor={`${idPrefix}-patient`}>Paciente</label>
        <select
          id={`${idPrefix}-patient`}
          className="agenda-select"
          value={form.idPaciente}
          onChange={(e) => onChange({ ...form, idPaciente: Number(e.target.value) })}
        >
          <option value="">Selecione o paciente</option>
          {pacientes.map((p) => (
            <option key={p.id} value={p.id}>{p.name}</option>
          ))}
        </select>
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
