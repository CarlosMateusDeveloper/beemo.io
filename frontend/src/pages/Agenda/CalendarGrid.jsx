import { useMemo, useState } from 'react'
import {
  addDays, formatDateKey, startOfWeek, timeToMinutes, minutesToTime,
} from './dateUtils'
import { DOW_SHORT, START_MINUTES, END_MINUTES, STATUS } from './constants'

function SlotCell({ dateKey, minute, isHourEnd, height, onSlotClick, onDrop }) {
  const [isDragOver, setIsDragOver] = useState(false)

  return (
    <div
      className={`agenda-slot-cell ${isHourEnd ? 'hour-end' : 'half-hour'}${isDragOver ? ' drag-over' : ''}`}
      style={{ height }}
      title="Clique para agendar"
      onClick={() => onSlotClick(dateKey, minute)}
      onDragOver={(e) => { e.preventDefault(); setIsDragOver(true) }}
      onDragLeave={() => setIsDragOver(false)}
      onDrop={(e) => {
        e.preventDefault()
        setIsDragOver(false)
        const id = e.dataTransfer.getData('text/plain')
        if (id) onDrop(id, dateKey, minute)
      }}
    />
  )
}

function AppointmentCard({ appointment, top, height, lane, onAppointmentClick }) {
  const status = STATUS[appointment.status]
  const endLabel = minutesToTime(timeToMinutes(appointment.start) + appointment.dur)

  return (
    <div
      className="agenda-appt-card"
      draggable
      onDragStart={(e) => {
        e.dataTransfer.setData('text/plain', appointment.id)
        e.dataTransfer.effectAllowed = 'move'
      }}
      onClick={(e) => { e.stopPropagation(); onAppointmentClick(appointment.id) }}
      style={{
        left: 3 + lane * 14, right: 3, top: top + 1, height: height - 3,
        background: status.bg, color: status.fg,
        border: `1px ${status.dashed ? 'dashed' : 'solid'} ${status.border}`,
        zIndex: 2 + lane,
      }}
    >
      <div className="agenda-appt-time">{appointment.start} - {endLabel}</div>
      <div className={`agenda-appt-name${status.strike ? ' strike' : ''}${height < 36 ? ' hidden' : ''}`}>
        {appointment.patient}
      </div>
    </div>
  )
}

export default function CalendarGrid({
  view, cursorDate, appointments, slotMinutes, showWeekend, now,
  onSlotClick, onDrop, onAppointmentClick,
}) {
  const ppm = view === 'day' ? 1.8 : 1.5
  const slotHeight = slotMinutes * ppm
  const slotCount = (END_MINUTES - START_MINUTES) / slotMinutes
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const todayKey = formatDateKey(today)
  const nowMinutes = now.getHours() * 60 + now.getMinutes()

  const dayList = useMemo(() => {
    if (view === 'day') return [cursorDate]
    const weekStart = startOfWeek(cursorDate)
    const count = showWeekend ? 7 : 5
    return Array.from({ length: count }, (_, i) => addDays(weekStart, i))
  }, [view, cursorDate, showWeekend])

  const byDay = useMemo(() => {
    const map = {}
    appointments.forEach((a) => {
      map[a.dateKey] = map[a.dateKey] || []
      map[a.dateKey].push(a)
    })
    return map
  }, [appointments])

  const gutterSlots = []
  for (let i = 0; i < slotCount; i++) {
    const minute = START_MINUTES + i * slotMinutes
    gutterSlots.push({ minute, label: minute % 60 === 0 ? minutesToTime(minute) : '' })
  }

  return (
    <div className="agenda-grid-scroll">
      <div className="agenda-grid-inner">
        <div className="agenda-days-header">
          <div className="agenda-gutter-spacer" />
          {dayList.map((day) => {
            const key = formatDateKey(day)
            const isToday = key === todayKey
            return (
              <div className="agenda-day-header-cell" key={key}>
                <div className="agenda-day-dow">{DOW_SHORT[day.getDay()]}</div>
                <div className={`agenda-day-num${isToday ? ' today' : ''}`}>{day.getDate()}</div>
              </div>
            )
          })}
        </div>

        <div className="agenda-body-row">
          <div className="agenda-gutter">
            {gutterSlots.map((slot, i) => (
              <div className="agenda-gutter-label" style={{ height: slotHeight }} key={i}>{slot.label}</div>
            ))}
          </div>

          {dayList.map((day) => {
            const key = formatDateKey(day)
            const isToday = key === todayKey
            const dayAppointments = (byDay[key] || []).slice().sort((a, b) => timeToMinutes(a.start) - timeToMinutes(b.start))

            return (
              <div className="agenda-day-col" key={key}>
                {gutterSlots.map((slot, i) => (
                  <SlotCell
                    key={i}
                    dateKey={key}
                    minute={slot.minute}
                    isHourEnd={(slot.minute + slotMinutes) % 60 === 0}
                    height={slotHeight}
                    onSlotClick={onSlotClick}
                    onDrop={onDrop}
                  />
                ))}

                {dayAppointments.map((appointment, i) => {
                  const startMin = timeToMinutes(appointment.start)
                  let lane = 0
                  for (let j = 0; j < i; j++) {
                    const other = dayAppointments[j]
                    if (timeToMinutes(other.start) + other.dur > startMin) lane++
                  }
                  const top = (startMin - START_MINUTES) * ppm
                  const height = appointment.dur * ppm
                  return (
                    <AppointmentCard
                      key={appointment.id}
                      appointment={appointment}
                      top={top}
                      height={height}
                      lane={lane}
                      onAppointmentClick={onAppointmentClick}
                    />
                  )
                })}

                {isToday && nowMinutes >= START_MINUTES && nowMinutes <= END_MINUTES && (
                  <div className="agenda-now-line" style={{ top: (nowMinutes - START_MINUTES) * ppm }}>
                    <div className="agenda-now-dot" />
                  </div>
                )}
              </div>
            )
          })}
        </div>
      </div>
    </div>
  )
}
