import { useEffect, useMemo, useState } from 'react'
import Topbar from './Topbar'
import CalendarGrid from './CalendarGrid'
import MonthView from './MonthView'
import CreateModal from './CreateModal'
import DetailModal from './DetailModal'
import {
  fetchConsultas, fetchMedicos, fetchPacientes, createConsulta, updateConsulta,
  toAppointment, toMedico, toPaciente, connectAgendaSocket,
} from './api'
import {
  addDays, formatDateKey, minutesToTime, parseDateKey, startOfWeek,
} from './dateUtils'
import {
  DEFAULT_SLOT_MINUTES, DOW_FULL, END_MINUTES, MONTHS, START_MINUTES,
} from './constants'
import './Agenda.css'

const EMPTY_FORM = { idPaciente: '', type: 'Consulta', dur: 30, status: 'Confirmada' }

function buildHeaderTitle(view, cursorDate, showWeekend) {
  if (view === 'day') {
    return `${DOW_FULL[cursorDate.getDay()]}, ${cursorDate.getDate()} de ${MONTHS[cursorDate.getMonth()]} de ${cursorDate.getFullYear()}`
  }
  if (view === 'week') {
    const weekStart = startOfWeek(cursorDate)
    const weekEnd = addDays(weekStart, (showWeekend ? 7 : 5) - 1)
    if (weekStart.getMonth() === weekEnd.getMonth()) {
      return `${weekStart.getDate()} - ${weekEnd.getDate()} de ${MONTHS[weekStart.getMonth()]} de ${weekEnd.getFullYear()}`
    }
    return `${weekStart.getDate()} de ${MONTHS[weekStart.getMonth()]} - ${weekEnd.getDate()} de ${MONTHS[weekEnd.getMonth()]} de ${weekEnd.getFullYear()}`
  }
  const monthName = MONTHS[cursorDate.getMonth()]
  return `${monthName.charAt(0).toUpperCase()}${monthName.slice(1)} de ${cursorDate.getFullYear()}`
}

function upsertAppointment(list, appointment) {
  const exists = list.some((a) => a.id === appointment.id)
  return exists ? list.map((a) => (a.id === appointment.id ? appointment : a)) : [...list, appointment]
}

export default function Agenda({
  slotMinutes = DEFAULT_SLOT_MINUTES,
  showWeekend = true,
}) {
  const [view, setView] = useState('week')
  const [cursorKey, setCursorKey] = useState(() => formatDateKey(new Date()))
  const [specialty, setSpecialty] = useState('')
  const [medicos, setMedicos] = useState([])
  const [pacientes, setPacientes] = useState([])
  const [profId, setProfId] = useState(null)
  const [appointments, setAppointments] = useState([])
  const [createAt, setCreateAt] = useState(null) // { dateKey, minute }
  const [form, setForm] = useState(EMPTY_FORM)
  const [detailId, setDetailId] = useState(null)
  const [now, setNow] = useState(() => new Date())
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState(null)
  const [actionError, setActionError] = useState(null)

  useEffect(() => {
    const id = setInterval(() => setNow(new Date()), 60000)
    return () => clearInterval(id)
  }, [])

  // Carga inicial: médicos, pacientes e consultas vêm do agenda-service (Go).
  useEffect(() => {
    let cancelled = false
    async function load() {
      try {
        const [medicosRes, pacientesRes, consultasRes] = await Promise.all([
          fetchMedicos(), fetchPacientes(), fetchConsultas(),
        ])
        if (cancelled) return
        const medicosList = medicosRes.map(toMedico)
        setMedicos(medicosList)
        setPacientes(pacientesRes.map(toPaciente))
        setAppointments(consultasRes.map(toAppointment))
        if (medicosList.length > 0) setProfId(medicosList[0].id)
      } catch (err) {
        if (!cancelled) setLoadError(err.message)
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    load()
    return () => { cancelled = true }
  }, [])

  // Tempo real: qualquer aba/usuário que mude uma consulta reflete aqui sem reload.
  useEffect(() => {
    const socket = connectAgendaSocket((event) => {
      if (event.entity === 'consulta' && event.consulta) {
        setAppointments((prev) => upsertAppointment(prev, toAppointment(event.consulta)))
      }
    })
    return () => socket.close()
  }, [])

  const cursorDate = useMemo(() => parseDateKey(cursorKey), [cursorKey])
  const headerTitle = useMemo(() => buildHeaderTitle(view, cursorDate, showWeekend), [view, cursorDate, showWeekend])
  const specialties = useMemo(() => [...new Set(medicos.map((m) => m.specialty))], [medicos])
  const visibleProfessionals = useMemo(
    () => (specialty ? medicos.filter((m) => m.specialty === specialty) : medicos),
    [medicos, specialty],
  )
  const currentProf = medicos.find((m) => m.id === profId) ?? null
  const myAppointments = useMemo(() => appointments.filter((a) => a.profId === profId), [appointments, profId])
  const detailAppointment = detailId ? appointments.find((a) => a.id === detailId) : null

  function handleSpecialtyChange(nextSpecialty) {
    setSpecialty(nextSpecialty)
    const stillVisible = nextSpecialty === '' || currentProf?.specialty === nextSpecialty
    if (!stillVisible) {
      const firstMatch = medicos.find((m) => m.specialty === nextSpecialty)
      if (firstMatch) setProfId(firstMatch.id)
    }
  }

  function shift(amount) {
    setCursorKey((prevKey) => {
      const date = parseDateKey(prevKey)
      if (view === 'day') return formatDateKey(addDays(date, amount))
      if (view === 'week') return formatDateKey(addDays(date, 7 * amount))
      return formatDateKey(new Date(date.getFullYear(), date.getMonth() + amount, 1))
    })
  }

  function openCreate(dateKey, minute) {
    setCreateAt({ dateKey, minute })
    setForm(EMPTY_FORM)
  }

  function openCreateNow() {
    const current = new Date()
    const todayKey = formatDateKey(current)
    let minute = Math.ceil((current.getHours() * 60 + current.getMinutes()) / slotMinutes) * slotMinutes
    minute = Math.max(START_MINUTES, Math.min(minute, END_MINUTES - slotMinutes))
    openCreate(todayKey, minute)
  }

  async function saveCreate() {
    if (!createAt || !form.idPaciente || !profId) return
    try {
      const consulta = await createConsulta({
        idPaciente: form.idPaciente,
        idMedico: profId,
        dataSlot: createAt.dateKey,
        horaSlot: minutesToTime(createAt.minute),
        tipo: form.type,
        duracaoMinutos: form.dur,
        status: form.status,
      })
      setAppointments((prev) => upsertAppointment(prev, toAppointment(consulta)))
      setCreateAt(null)
    } catch (err) {
      setActionError(err.message)
    }
  }

  async function changeStatus(id, status) {
    try {
      const consulta = await updateConsulta(id, { status })
      setAppointments((prev) => upsertAppointment(prev, toAppointment(consulta)))
      setDetailId(null)
    } catch (err) {
      setActionError(err.message)
    }
  }

  async function editAppointment(id, fields) {
    try {
      const consulta = await updateConsulta(id, {
        idPaciente: fields.idPaciente,
        tipo: fields.type,
        duracaoMinutos: fields.dur,
      })
      setAppointments((prev) => upsertAppointment(prev, toAppointment(consulta)))
    } catch (err) {
      setActionError(err.message)
    }
  }

  async function rescheduleAppointment(id, dateKey, minute) {
    try {
      const consulta = await updateConsulta(id, { dataSlot: dateKey, horaSlot: minutesToTime(minute) })
      setAppointments((prev) => upsertAppointment(prev, toAppointment(consulta)))
    } catch (err) {
      setActionError(err.message)
    }
  }

  if (loading) {
    return (
      <div className="agenda-page">
        <div className="agenda-main">
          <div className="agenda-hint" style={{ margin: 24 }}>Carregando agenda…</div>
        </div>
      </div>
    )
  }

  if (loadError || !currentProf) {
    return (
      <div className="agenda-page">
        <div className="agenda-main">
          <div className="agenda-hint" style={{ margin: 24 }}>
            Não foi possível carregar a agenda ({loadError || 'nenhum médico cadastrado'}).
            Confirme se o agenda-service está rodando em {import.meta.env.VITE_AGENDA_SERVICE_URL || 'http://localhost:8081'}.
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="agenda-page">
      <div className="agenda-main">
        {actionError && (
          <div className="agenda-hint" style={{ margin: '12px 18px 0', color: 'var(--danger)', background: 'var(--danger-bg)' }}>
            {actionError}
            <button className="agenda-close-btn" style={{ float: 'right' }} onClick={() => setActionError(null)} aria-label="Fechar aviso">×</button>
          </div>
        )}

        <Topbar
          headerTitle={headerTitle}
          view={view}
          onViewChange={setView}
          onPrev={() => shift(-1)}
          onNext={() => shift(1)}
          onToday={() => setCursorKey(formatDateKey(new Date()))}
          professionals={visibleProfessionals}
          profId={profId}
          onProfChange={setProfId}
          specialties={specialties}
          specialty={specialty}
          onSpecialtyChange={handleSpecialtyChange}
          onCreateClick={openCreateNow}
        />

        {view === 'month' ? (
          <MonthView
            cursorDate={cursorDate}
            appointments={myAppointments}
            onDayClick={(dateKey) => { setView('day'); setCursorKey(dateKey) }}
          />
        ) : (
          <CalendarGrid
            view={view}
            cursorDate={cursorDate}
            appointments={myAppointments}
            slotMinutes={slotMinutes}
            showWeekend={showWeekend}
            now={now}
            onSlotClick={openCreate}
            onDrop={rescheduleAppointment}
            onAppointmentClick={setDetailId}
          />
        )}
      </div>

      <CreateModal
        createAt={createAt ? { date: parseDateKey(createAt.dateKey), minute: createAt.minute } : null}
        form={form}
        professionalName={currentProf.name}
        pacientes={pacientes}
        onChange={setForm}
        onCancel={() => setCreateAt(null)}
        onSave={saveCreate}
      />

      <DetailModal
        appointment={detailAppointment}
        professionalName={currentProf.name}
        professionalSpecialty={currentProf.specialty}
        pacientes={pacientes}
        onClose={() => setDetailId(null)}
        onChangeStatus={changeStatus}
        onEdit={editAppointment}
      />
    </div>
  )
}
