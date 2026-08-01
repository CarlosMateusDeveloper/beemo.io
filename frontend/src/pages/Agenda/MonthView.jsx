import { useMemo } from 'react'
import { addDays, formatDateKey, startOfWeek, timeToMinutes } from './dateUtils'
import { MONTH_HEADER_DOWS, STATUS } from './constants'

export default function MonthView({ cursorDate, appointments, onDayClick }) {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const todayKey = formatDateKey(today)

  const byDay = useMemo(() => {
    const map = {}
    appointments.forEach((a) => {
      map[a.dateKey] = map[a.dateKey] || []
      map[a.dateKey].push(a)
    })
    return map
  }, [appointments])

  const cells = useMemo(() => {
    const firstOfMonth = new Date(cursorDate.getFullYear(), cursorDate.getMonth(), 1)
    const gridStart = startOfWeek(firstOfMonth)
    return Array.from({ length: 42 }, (_, i) => {
      const day = addDays(gridStart, i)
      const key = formatDateKey(day)
      const inMonth = day.getMonth() === cursorDate.getMonth()
      const list = (byDay[key] || []).slice().sort((a, b) => timeToMinutes(a.start) - timeToMinutes(b.start))
      return { key, day, inMonth, isToday: key === todayKey, appointments: list }
    })
  }, [cursorDate, byDay, todayKey])

  return (
    <div className="agenda-month">
      <div className="agenda-month-header">
        {MONTH_HEADER_DOWS.map((dow) => (
          <div className="agenda-month-header-cell" key={dow}>{dow}</div>
        ))}
      </div>
      <div className="agenda-month-grid">
        {cells.map((cell) => {
          const visible = cell.appointments.slice(0, 3)
          const extra = cell.appointments.length - visible.length
          return (
            <div
              key={cell.key}
              className={`agenda-month-cell${cell.inMonth ? '' : ' outside'}`}
              onClick={() => onDayClick(cell.key)}
            >
              <div className={`agenda-month-num${cell.isToday ? ' today' : cell.inMonth ? '' : ' outside'}`}>
                {cell.day.getDate()}
              </div>
              {visible.map((appointment) => {
                const status = STATUS[appointment.status]
                return (
                  <div
                    key={appointment.id}
                    className={`agenda-month-chip${status.strike ? ' strike' : ''}`}
                    style={{ background: status.bg, color: status.fg }}
                  >
                    <span className="agenda-month-chip-dot" style={{ background: status.dot }} />
                    <span className="agenda-month-chip-label">{appointment.start} {appointment.patient.split(' ')[0]}</span>
                  </div>
                )
              })}
              {extra > 0 && <div className="agenda-month-more">+ {extra} consultas</div>}
            </div>
          )
        })}
      </div>
    </div>
  )
}
