import { cloneElement, useEffect, useId, useRef, useState } from 'react'

export const roomTypes = { LECTURE: 'Giảng đường', COMPUTER_LAB: 'Phòng máy', LAB: 'Phòng thí nghiệm', STUDIO: 'Xưởng thực hành' }
export const roleNames = { STAFF: 'Nhân viên đào tạo', MANAGER: 'Quản lý', LECTURER: 'Giảng viên', STUDENT: 'Sinh viên' }
export const days = ['Thứ Hai', 'Thứ Ba', 'Thứ Tư', 'Thứ Năm', 'Thứ Sáu', 'Thứ Bảy', 'Chủ nhật']
export const statuses = {
  DRAFT: 'Nháp', OPEN: 'Đang mở', CLOSED: 'Đã đóng', CANCELLED: 'Đã hủy', ACTIVE: 'Hoạt động',
  INACTIVE: 'Ngừng sử dụng', MAINTENANCE: 'Bảo trì', PLANNED: 'Đã lên lịch', COMPLETED: 'Hoàn tất',
  QUEUED: 'Đang chờ', RUNNING: 'Đang xử lý', FAILED: 'Thất bại', DISCARDED: 'Đã bỏ',
  APPLIED: 'Đã áp dụng', PENDING: 'Chờ xử lý', APPROVED: 'Đã duyệt', RETURNED: 'Đã trả lại',
  SUBMITTED: 'Chờ duyệt', PUBLISHED: 'Đã công bố', SUPERSEDED: 'Đã thay thế',
  CANCEL: 'Nghỉ học', RESCHEDULE: 'Học bù', ROOM_CHANGE: 'Đổi phòng', BUSY: 'Bận', AVOID: 'Hạn chế xếp'
}
export const formatDate = value => value?.slice(0, 10).split('-').reverse().join('/') || '—'
export const vietnamToday = () => new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Ho_Chi_Minh' }).format(new Date())
export function addCalendarDays(value, amount) {
  const date = new Date(value + 'T12:00:00Z')
  date.setUTCDate(date.getUTCDate() + amount)
  return date.toISOString().slice(0,10)
}

export function Field({ label, children }) {
  const id = useId()
  return <div className="field">
    <label htmlFor={id}>{label}</label>
    {cloneElement(children, { id })}
  </div>
}
export function Select({ value, set, options, placeholder, ...props }) {
  return <select value={value ?? ''} onChange={e => set(e.target.value)} {...props}>
    {placeholder && <option value="">{placeholder}</option>}
    {options.map(([id, label]) => <option key={id} value={id}>{label}</option>)}
  </select>
}
export function Options({ value, set, values, ...props }) {
  return <Select value={value} set={set} options={Object.entries(values)} {...props} />
}
export const codes = rows => rows.map(r => [r.id, r.name ? r.code + ' · ' + r.name : r.code])
export const dayOptions = days.map((day, i) => [i + 1, day])
export const values = (start, end) => Array.from({ length: end - start + 1 }, (_, i) => start + i)
export const builder = set => key => value => set(previous => ({ ...previous, [key]: value }))
export const numeric = (form, names) => ({ ...form, ...Object.fromEntries(names.map(n => [n, form[n] === '' ? null : Number(form[n])])) })

export function Tag({ value }) {
  return <span className={'tag ' + value?.toLowerCase()}>{statuses[value] || value || '—'}</span>
}
export function Panel({ title, children, action }) {
  return <section className="panel">{(title || action) && <div className="panel-heading"><h2>{title}</h2>{action}</div>}{children}</section>
}
export function Table({ columns, rows, empty = 'Chưa có dữ liệu.' }) {
  return rows.length ? <div className="table-wrap" tabIndex={0} role="region" aria-label="Bảng dữ liệu"><table><thead><tr>{columns.map(([label]) => <th scope="col" key={label}>{label}</th>)}</tr></thead>
    <tbody>{rows.map(row => <tr key={row.id}>{columns.map(([label, render]) =>
      <td key={label}>{typeof render === 'function' ? render(row) : row[render] ?? '—'}</td>)}</tr>)}</tbody></table></div>
    : <div className="empty">{empty}</div>
}
export function ErrorBox({ error, onSuggestion }) {
  if (!error) return null
  const details = error.details || error
  return <div className="error" role="alert">
    <strong>{error.message || 'Lịch dự kiến có xung đột.'}</strong>
    {details.issues?.map((issue, i) => <div key={i}>{[
      issue.rule, issue.message || 'Có lịch liên quan', issue.sectionCode || issue.section_code, issue.roomCode,
      issue.groupCodes?.length > 0 && issue.groupCodes.join(', '),
      issue.dayOfWeek && days[issue.dayOfWeek - 1],
      issue.periodFrom && ('tiết ' + issue.periodFrom + (issue.periodCount > 1 ? '–' + (issue.periodFrom + issue.periodCount - 1) : '')),
      issue.start_at && (formatDate(issue.start_at) + ' ' + issue.start_at.slice(11,16)).trim()
    ].filter(Boolean).join(' · ')}</div>)}
    {details.patterns?.map(p => <div key={p.id}>Lịch nháp · {p.section_code}</div>)}
    {error.request_id && <small className="request-reference">Mã yêu cầu: {error.request_id}</small>}
    {details.suggestions?.length > 0 && <div className="button-row suggestions">{details.suggestions.map(s =>
      <button type="button" className="quiet" key={s.roomId + '-' + s.dayOfWeek + '-' + s.periodFrom}
        onClick={() => onSuggestion?.(s)} disabled={!onSuggestion}>{s.roomCode} · {days[s.dayOfWeek - 1]} · tiết {s.periodFrom}</button>)}</div>}
  </div>
}
export function Editor({ title, onClose, onSubmit, children, submit = 'Lưu', extra, onSuggestion, latestFields, onUseVersion, scheduleKey, onFieldError }) {
  const ref = useRef(null)
  const headingId = useId()
  const [pending, setPending] = useState(false)
  const [failure, setFailure] = useState(null)
  const error = failure?.error?.code === 'SCHEDULE_CONFLICT' && failure.scheduleKey !== scheduleKey ? null : failure?.error
  const latest = error?.code === 'VERSION_CONFLICT' && Number.isInteger(error.details?.version) ? error.details : null
  useEffect(() => {
    const dialog = ref.current
    dialog.showModal()
    return () => dialog.close()
  }, [])
  async function save(event) {
    event.preventDefault()
    if (pending || latest) return
    setPending(true)
    setFailure(null)
    try { await onSubmit(); onClose() }
    catch (error) { if (!onFieldError?.(error)) setFailure({ error, scheduleKey }) }
    finally { setPending(false) }
  }
  return <dialog ref={ref} aria-labelledby={headingId} onCancel={event => { event.preventDefault(); if (!pending) onClose() }}>
    <form onSubmit={save}>
      <div className="dialog-heading"><h2 id={headingId}>{title}</h2><button type="button" className="quiet icon-only" aria-label="Đóng" onClick={onClose} disabled={pending}>×</button></div>
      <div className="dialog-content"><ErrorBox error={error} onSuggestion={onSuggestion} />
        {latest && <section className="version-resolution">
          <h3>Dữ liệu đang lưu · Phiên bản {latest.version}</h3>
          {latestFields && <dl>{latestFields(latest).map(([label,value]) =>
            <div key={label}><dt>{label}</dt><dd>{value ?? '—'}</dd></div>)}</dl>}
          <p>Kiểm tra dữ liệu mới trước khi lưu tiếp. Nội dung đang nhập vẫn nằm trong biểu mẫu.</p>
          {onUseVersion && <button type="button" className="quiet" onClick={() => { onUseVersion(latest); setFailure(null) }}>
            Dùng phiên bản mới và giữ nội dung đang nhập
          </button>}
        </section>}
        {children}</div>
      <div className="dialog-actions">{extra && <div className="dialog-extra">{extra}</div>}
        <button type="button" className="quiet" onClick={onClose} disabled={pending}>Đóng</button>
        <button type="submit" disabled={pending || !!latest}>{pending ? 'Đang lưu…' : submit}</button>
      </div>
    </form>
  </dialog>
}
export function useFetch(loader, deps) {
  const [state, setState] = useState({ data: null, error: null })
  useEffect(() => {
    let current = true
    loader().then(data => { if (current) setState({ data, error: null }) })
      .catch(error => { if (current) setState({ data: null, error }) })
    return () => { current = false }
  }, deps)
  return state
}
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
