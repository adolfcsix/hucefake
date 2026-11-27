import { days } from '../../constants/domain'
import { values } from '../../utils/forms'

export function WeekGrid({ rows, onSlot, onItem, onDropSlot, maxPeriod = 12, dayLabels = days, periods = [], placementConflict }) {
  const list = values(1, maxPeriod)
  return <div className="week-scroll" tabIndex={0} role="region" aria-label="Lưới lịch theo tiết"><table className="week-grid"><thead><tr>
    <th scope="col">Tiết</th>{dayLabels.map((day, i) => <th key={i} scope="col">{day}</th>)}
  </tr></thead><tbody>{list.map(period => {
    const definition = periods.find(p => p.period_no === period)
    return <tr className={period === 7 ? 'shift-start' : ''} key={period}>
      <th scope="row">{period}{definition && <small>{definition.start_time}</small>}</th>
      {dayLabels.map((day, i) => {
        const items = rows.filter(row => row.day_of_week === i + 1 && row.period_from === period)
        const conflict = placementConflict?.day === i + 1 && placementConflict.period === period
        return <td key={day} className={conflict ? 'placement-conflict' : undefined} onDragOver={onDropSlot ? event => event.preventDefault() : undefined}
          onDrop={onDropSlot ? event => { event.preventDefault(); onDropSlot(event.dataTransfer.getData('text/plain'), i + 1, period) } : undefined}>
          {items.map(item => <button key={item.id} className={'schedule-card ' + (item.status === 'CANCELLED' ? 'cancelled-card' : '') +
            (item.violation_note ? ' has-violation' : '')}
            type="button" onClick={() => onItem?.(item)} disabled={!onItem}>
            <strong>{item.section_code}</strong><span>{item.subject_name}</span>
            <small>{item.room_code || '—'} · {item.period_count} tiết</small>
            {item.violation_note && <small>{item.violation_note}</small>}
          </button>)}
          {conflict && <small className="placement-conflict-label">Xung đột · {placementConflict.sectionCode}</small>}
          {onSlot && <button className="empty-slot quiet" onClick={() => onSlot(i + 1, period)} aria-label={'Xếp lịch ' + day + ' tiết ' + period}>+</button>}
        </td>
      })}
    </tr>
  })}</tbody></table></div>
}
