import { useState } from 'react'
import { api } from './api'
import { sessionInstant } from './CalendarExport'
import { SessionEditor } from './Timetable'
import { addCalendarDays, ErrorBox, formatDate, Panel, useFetch, vietnamToday } from './ui'

const details = row => [row.section_code, row.room_code || 'Chưa gán phòng'].join(' · ')
const hours = row => row.start_at.slice(11,16) + ' – ' + row.end_at.slice(11,16)
const weekday = new Intl.DateTimeFormat('vi-VN', { weekday:'long', timeZone:'Asia/Ho_Chi_Minh' })
const dateLabel = value => weekday.format(new Date(sessionInstant(value))) + ', ' + formatDate(value)

export default function StudentHome({ user, catalog, semester, revision }) {
  const [edit,setEdit] = useState(null)
  const today = vietnamToday()
  const now = Date.now()
  const start = semester.start_date.slice(0,10)
  const end = semester.end_date.slice(0,10)
  const from = today < start ? start : today
  const horizon = addCalendarDays(from,13)
  const to = horizon < end ? horizon : end
  const schedule = useFetch(() => from > to ? Promise.resolve([]) : api('/schedule?' + new URLSearchParams({
    semesterId:semester.id, view:'personal', from, to
  })), [semester.id,from,to,revision])
  const notices = useFetch(() => api('/notifications?page=1&pageSize=3'), [revision])
  const upcoming = (schedule.data || []).filter(row => row.status === 'PLANNED' && sessionInstant(row.start_at) > now)
  const [next,...later] = upcoming
  return <>
    <div className="student-overview">
      <section className="student-next" aria-labelledby="student-next-title">
        <h2 id="student-next-title">Buổi học tiếp theo</h2>
        <ErrorBox error={schedule.error} />
        {next ? <div className="next-content">
          <p className="next-date">{dateLabel(next.start_at)}</p>
          <h3>{next.subject_name}</h3>
          <p className="next-meta"><strong>{hours(next)}</strong><span>{details(next)}</span></p>
          <button className="quiet" onClick={() => setEdit(next)}>Chi tiết buổi học</button>
        </div> : !schedule.error && <p className="next-empty" role="status">
          {!schedule.data ? 'Đang tải lịch học…' : from > to ? 'Học kỳ đã kết thúc.' : 'Chưa có buổi học nào trong 14 ngày được xem.'}
        </p>}
        <a className="next-link" href="#schedule">Mở thời khóa biểu</a>
      </section>
      <Panel title="Thông báo gần đây" action={<a className="portal-link" href="#notifications">Xem tất cả</a>}>
        <ErrorBox error={notices.error} />
        {!!notices.data?.items.length && <ul className="overview-notices">
          {notices.data.items.map(item => <li key={item.id}>
            <time dateTime={item.created_at}>{formatDate(item.created_at)}</time>
            {!item.read_at && <span className="unread-notice">Chưa đọc</span>}
            <p>{item.message}</p>
          </li>)}
        </ul>}
        {!notices.error && !notices.data?.items.length &&
          <p className="overview-empty" role="status">{notices.data ? 'Chưa có thông báo.' : 'Đang tải thông báo…'}</p>}
      </Panel>
    </div>
    {later.length > 0 && <Panel title="Các buổi sắp tới">
      <ul className="overview-lessons">
        {later.slice(0,5).map(row => <li key={row.id}>
          <button type="button" className="quiet" onClick={() => setEdit(row)}>
            <span><strong>{hours(row)}</strong><small>{formatDate(row.start_at)}</small></span>
            <span><strong>{row.subject_name}</strong><small>{details(row)}</small></span>
          </button>
        </li>)}
      </ul>
    </Panel>}
    {edit && <SessionEditor row={edit} user={user} catalog={catalog} readOnly close={() => setEdit(null)} />}
  </>
}
