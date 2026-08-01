import { useEffect, useMemo, useRef, useState } from 'react'
import Topbar from './Topbar'
import CalendarGrid from './CalendarGrid'
import MonthView from './MonthView'
import CreateModal from './CreateModal'
import DetailModal from './DetailModal'
import { buildAccentPalette } from './colorUtils'
import { seedAppointments } from './mockAppointments'
import {
  addDays, formatDateKey, minutesToTime, parseDateKey, startOfWeek,
} from './dateUtils'
import {
  DEFAULT_ACCENT, DEFAULT_SLOT_MINUTES, DOW_FULL, END_MINUTES, MONTHS, PROFESSIONALS, START_MINUTES,
} from './constants'
import './Agenda.css'

const EMPTY_FORM = { patient: '', type: 'Consulta', dur: 30, status: 'confirmado' }

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

export default function Agenda({
  accentColor = DEFAULT_ACCENT,
  slotMinutes = DEFAULT_SLOT_MINUTES,
  showWeekend = true,
}) {
  const rootRef = useRef(null)
  const [view, setView] = useState('week')
  const [cursorKey, setCursorKey] = useState(() => formatDateKey(new Date()))
  const [profId, setProfId] = useState(PROFESSIONALS[0].id)
  const [appointments, setAppointments] = useState(seedAppointments)
  const [createAt, setCreateAt] = useState(null) // { dateKey, minute }
  const [form, setForm] = useState(EMPTY_FORM)
  const [detailId, setDetailId] = useState(null)
  const [now, setNow] = useState(() => new Date())

  useEffect(() => {
    const id = setInterval(() => setNow(new Date()), 60000)
    return () => clearInterval(id)
  }, [])

  useEffect(() => {
    if (!rootRef.current) return
    const palette = buildAccentPalette(accentColor)
    Object.entries(palette).forEach(([key, value]) => rootRef.current.style.setProperty(key, value))
  }, [accentColor])

  const cursorDate = useMemo(() => parseDateKey(cursorKey), [cursorKey])
  const headerTitle = useMemo(() => buildHeaderTitle(view, cursorDate, showWeekend), [view, cursorDate, showWeekend])
  const currentProf = PROFESSIONALS.find((p) => p.id === profId)
  const myAppointments = useMemo(() => appointments.filter((a) => a.profId === profId), [appointments, profId])
  const detailAppointment = detailId ? appointments.find((a) => a.id === detailId) : null

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

  function saveCreate() {
    if (!createAt) return
    const appointment = {
      id: 'a' + Date.now(),
      profId,
      dateKey: createAt.dateKey,
      start: minutesToTime(createAt.minute),
      dur: form.dur,
      patient: form.patient.trim() || 'Paciente sem nome',
      type: form.type,
      status: form.status,
    }
    setAppointments((prev) => [...prev, appointment])
    setCreateAt(null)
  }

  function changeStatus(id, status) {
    setAppointments((prev) => prev.map((a) => (a.id === id ? { ...a, status } : a)))
    setDetailId(null)
  }

  function rescheduleAppointment(id, dateKey, minute) {
    setAppointments((prev) => prev.map((a) => (a.id === id ? { ...a, dateKey, start: minutesToTime(minute) } : a)))
  }

  return (
    <div className="agenda-page" ref={rootRef}>
      <div className="agenda-main">
        <Topbar
          headerTitle={headerTitle}
          view={view}
          onViewChange={setView}
          onPrev={() => shift(-1)}
          onNext={() => shift(1)}
          onToday={() => setCursorKey(formatDateKey(new Date()))}
          professionals={PROFESSIONALS}
          profId={profId}
          onProfChange={setProfId}
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
        onChange={setForm}
        onCancel={() => setCreateAt(null)}
        onSave={saveCreate}
      />

      <DetailModal
        appointment={detailAppointment}
        professionalName={currentProf.name}
        professionalSpecialty={currentProf.specialty}
        onClose={() => setDetailId(null)}
        onChangeStatus={changeStatus}
      />
    </div>
  )
}
