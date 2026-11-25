import { days, formatDate, Tag, values } from './ui'

function periodEnd(row) {
  return row.period_from + row.period_count
}

function placeSessions(rows) {
  const lanes = []
  const ordered = [...rows].sort((a,b) => a.period_from - b.period_from || periodEnd(b) - periodEnd(a) || a.id - b.id)
  const items = ordered.map(row => {
    let lane = lanes.findIndex(end => end <= row.period_from)
    if (lane === -1) lane = lanes.length
    lanes[lane] = periodEnd(row)
    return { row, lane }
  })
  return { items, lanes: Math.max(1, lanes.length) }
}

const time = value => value.slice(11,16)

function sessionClass(row) {
  if (row.status === 'CANCELLED') return 'is-cancelled'
  if (row.source_session_id) return 'is-replacement'
  return ''
}

function sessionLabel(row) {
  return [row.subject_name, row.section_code, row.room_code, formatDate(row.start_at), time(row.start_at) + ' – ' + time(row.end_at),
    row.status === 'CANCELLED' ? 'Đã hủy' : row.source_session_id ? 'Buổi thay thế' : undefined].filter(Boolean).join(' · ')
}

export function PublishedWeek({ rows, dates, today, onItem }) {
  const periods = values(1, Math.max(12, ...rows.map(row => periodEnd(row) - 1)))
  const columns = dates.map(date => placeSessions(rows.filter(row => row.start_at.slice(0,10) === date)))
  const gridTemplateColumns = '64px ' + columns.map(({ lanes }) => 'minmax(' + (lanes === 1 ? 102 : lanes * 110) + 'px, 1fr)').join(' ')
  return <div className="timetable-scroll" tabIndex={0} role="region" aria-label="Lưới lịch theo tuần">
    <div className="timetable" style={{ gridTemplateColumns }}>
      <div className="calendar-heading period-heading">Tiết</div>
      {dates.map((date, index) => <div key={date} className={'calendar-heading' + (date === today ? ' today' : '')}>
        <span>{days[index]}</span>
        <time dateTime={date} aria-current={date === today ? 'date' : undefined}>{formatDate(date).slice(0,5)}</time>
      </div>)}
      <div className="period-axis">
        {periods.map(period => <div key={period} className={period === 7 ? 'after-break' : ''}>{period}</div>)}
      </div>
      {columns.map(({ items, lanes }, index) => <div key={dates[index]}
        className={'day-column' + (dates[index] === today ? ' today' : '')}
        style={{ gridTemplateColumns: 'repeat(' + lanes + ', minmax(0,1fr))', gridTemplateRows: 'repeat(' + periods.length + ', var(--period-height))' }}>
        {periods.map(period => <div key={period} className={'period-line' + (period === 7 ? ' after-break' : '')}
          style={{ gridArea: period + ' / 1 / span 1 / -1' }} aria-hidden="true" />)}
        {items.map(({ row, lane }) => <button type="button" key={row.id} onClick={() => onItem(row)}
          className={'calendar-session ' + sessionClass(row)} title={sessionLabel(row)} aria-label={sessionLabel(row)}
          style={{ gridArea: row.period_from + ' / ' + (lane + 1) + ' / span ' + row.period_count + ' / span 1' }}>
          <strong>{row.subject_name}</strong>
          <span>{time(row.start_at)}–{time(row.end_at)}{row.room_code ? ' · ' + row.room_code : ''}</span>
          {row.status === 'CANCELLED' ? <small>Đã hủy</small> : row.source_session_id && <small>Buổi thay thế</small>}
        </button>)}
      </div>)}
    </div>
  </div>
}

export function WeekAgenda({ rows, dates, today, onItem }) {
  return <div className="week-agenda">
    {dates.map((date, index) => {
      const sessions = rows.filter(row => row.start_at.slice(0,10) === date)
      if (!sessions.length) return null
      return <section className="agenda-day" key={date}>
        <h3>{days[index]} <time dateTime={date} aria-current={date === today ? 'date' : undefined}>{formatDate(date).slice(0,5)}</time></h3>
        <ul>{sessions.map(row => <li key={row.id}>
          <button type="button" className={'agenda-session ' + sessionClass(row)} onClick={() => onItem(row)}>
            <span className="agenda-time">{time(row.start_at)}<small>{time(row.end_at)}</small></span>
            <span><strong>{row.subject_name}</strong>
              <small>{[row.section_code, row.room_code, row.staff_code].filter(Boolean).join(' · ')}</small>
              {row.exception_reason && <small>{row.exception_reason}</small>}
            </span>
            {(row.status === 'CANCELLED' || row.source_session_id) && <span className="agenda-status">
              {row.status === 'CANCELLED' ? <Tag value={row.status} /> : 'Buổi thay thế'}
            </span>}
          </button>
        </li>)}</ul>
      </section>
    })}
  </div>
}
