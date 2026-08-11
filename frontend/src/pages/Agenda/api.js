const API_BASE = import.meta.env.VITE_AGENDA_SERVICE_URL || 'http://localhost:8081'
const WS_BASE = API_BASE.replace(/^http/, 'ws')

async function request(path, options) {
  const res = await fetch(`${API_BASE}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  })
  if (!res.ok) {
    const body = await res.json().catch(() => ({}))
    throw new Error(body.erro || `Erro ${res.status} em ${path}`)
  }
  if (res.status === 204) return null
  return res.json()
}

export function fetchConsultas() {
  return request('/consultas')
}

export function fetchMedicos() {
  return request('/medicos')
}

export function fetchPacientes() {
  return request('/pacientes')
}

export function createConsulta(payload) {
  return request('/consultas', { method: 'POST', body: JSON.stringify(payload) })
}

export function updateConsulta(id, payload) {
  return request(`/consultas/${id}`, { method: 'PUT', body: JSON.stringify(payload) })
}

// Adapta o formato do agenda-service (Go) pro formato usado pelos
// componentes da Agenda no frontend.
export function toAppointment(consulta) {
  return {
    id: consulta.id,
    profId: consulta.idMedico,
    idPaciente: consulta.idPaciente,
    patient: consulta.pacienteNome,
    dateKey: consulta.dataSlot,
    start: consulta.horaSlot.slice(0, 5), // "14:30:00" -> "14:30"
    dur: consulta.duracaoMinutos,
    type: consulta.tipo,
    status: consulta.status,
  }
}

export function toMedico(medico) {
  return { id: medico.id, name: medico.nome, specialty: medico.especialidade }
}

export function toPaciente(paciente) {
  return { id: paciente.id, name: paciente.nome }
}

// Conecta no WebSocket de eventos em tempo real (created/updated de agenda
// e consulta). onEvent recebe o payload já desserializado.
export function connectAgendaSocket(onEvent) {
  const socket = new WebSocket(`${WS_BASE}/ws/agenda`)
  socket.onmessage = (event) => {
    try {
      onEvent(JSON.parse(event.data))
    } catch {
      // mensagem que não é JSON válido — ignora
    }
  }
  return socket
}
