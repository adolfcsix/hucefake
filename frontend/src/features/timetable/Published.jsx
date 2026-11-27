import { useState } from 'react'
import { api, downloadCsv, downloadFile } from '../../services/api'
import { PublishedWeek, WeekAgenda } from './Calendar'
import { makeCalendar } from './CalendarExport'
import { Pagination } from '../../components/common/Pagination'
import { usePagedQuery } from '../../hooks/usePagedQuery'
import { addCalendarDays as move, formatDate, vietnamToday } from '../../utils/dates'
import { codes } from '../../utils/forms'
import { days } from '../../constants/domain'
import { ErrorBox } from '../../components/common/ErrorBox'
import { Field, Options, Select } from '../../components/forms/FormFields'
import { Panel } from '../../components/common/Panel'
import { Table } from '../../components/common/Table'
import { Tag } from '../../components/common/Tag'
import { useFetch } from '../../hooks/useFetch'
import { filters } from './timetableFilters'
import { SessionEditor } from './SessionEditor'

const toDate = value => new Date(value + 'T12:00:00Z')
const weekday = date => (toDate(date).getUTCDay() + 6) % 7 + 1
const monday = date => move(date, 1 - weekday(date))

export function Published({ user, catalog, semester, revision, reload }) {
  const [scope, setScope] = useState('week')
  const [layout, setLayout] = useState(() => window.matchMedia('(max-width: 760px)').matches ? 'agenda' : 'grid')
  const [view, setView] = useState(user.role === 'STAFF' || user.role === 'MANAGER' ? 'all' : 'personal')
  const [id, setId] = useState('')
  const [edit, setEdit] = useState(null)
  const start = semester.start_date.slice(0, 10)
  const end = semester.end_date.slice(0, 10)
  const now = vietnamToday()
  const [focusDate, setFocusDate] = useState(now >= start && now <= end ? now : start)
  const week = monday(focusDate)
  const last = move(week, 6)
  const internal = user.role === 'STAFF' || user.role === 'MANAGER'
  const personal = useFetch(() => api('/schedule/sections?semesterId=' + semester.id + '&view=' + (internal ? 'all' : 'personal')), [semester.id, revision])
  const sectionOptions = personal.data || []
  const options = internal ? filters(catalog, sectionOptions) : { section: codes(sectionOptions) }
  const query = '/schedule?' + new URLSearchParams({ semesterId: semester.id, view, ...(id && { id }),
    ...(scope === 'week' && { from: week, to: last }) })
  const awaitingObject = view !== 'all' && view !== 'personal' && !id
  const [exporting, setExporting] = useState(null)
  const [exportError, setExportError] = useState(null)
  const data = useFetch(async () => ({ query, rows: scope !== 'week' || awaitingObject ? [] : await api(query) }), [query, revision, awaitingObject, scope])
  const paged = usePagedQuery(scope === 'semester' && !awaitingObject ? query : null,revision)
  const calendarReady = data.data?.query === query
  const ready = scope === 'week' ? calendarReady : !!paged.data
  const rows = scope === 'semester' ? paged.data?.items || [] : calendarReady ? data.data.rows : []
  async function exportSchedule(format) {
    if (exporting) return
    setExporting(format)
    setExportError(null)
    try {
      const exportRows = scope === 'semester' ? await api(query) : rows
      if (format === 'csv') downloadCsv(exportRows)
      else {
        const calendar = makeCalendar(exportRows)
        if (!calendar) throw new Error('Không có buổi học để tải ở phạm vi đã chọn.')
        downloadFile(calendar, 'text/calendar;charset=utf-8', 'thoikhoabieu.ics')
      }
    }
    catch (error) { setExportError(error) }
    finally { setExporting(null) }
  }
  const dates = days.map((_, i) => move(week, i))
  const empty = awaitingObject ? 'Chọn đối tượng để xem lịch.' : ready ?
    'Không có buổi học trong phạm vi đã chọn.' : 'Đang tải thời khóa biểu…'
  const canChange = row => row.status === 'PLANNED' && row.section_status === 'OPEN' && (
    user.role === 'STAFF' && user.unitId === row.managing_unit_id && (row.campus_id == null || catalog.campuses.some(c => c.id === row.campus_id)) ||
    user.role === 'LECTURER'
  )
  const hasExportRows = scope === 'semester' ? !!paged.data?.total : !!rows.length
  return <Panel title={user.role === 'LECTURER' ? 'Lịch giảng dạy' : user.role === 'STUDENT' ? 'Thời khóa biểu cá nhân' : 'Thời khóa biểu đã công bố'}
    action={<div className="button-row">
      <button className="quiet" onClick={() => exportSchedule('csv')} disabled={!!exporting || !hasExportRows}>{exporting === 'csv' ? 'Đang xuất…' : 'Xuất CSV'}</button>
      <button className="quiet" onClick={() => exportSchedule('ics')} disabled={!!exporting || !hasExportRows}>{exporting === 'ics' ? 'Đang tải…' : 'Tải lịch .ics'}</button>
    </div>}>
    <ErrorBox error={exportError || (scope === 'semester' ? paged.error : data.error) || personal.error} />
    <div className="toolbar">
      <Field label="Phạm vi"><Options value={scope} set={setScope} values={{ week: 'Theo tuần', semester: 'Cả học kỳ' }} /></Field>
      <Field label="Xem theo"><Options value={view} set={v => { setView(v); setId('') }} values={internal ?
        { all: 'Toàn trường', room: 'Phòng', staff:'Giảng viên', group:'Lớp hành chính', section:'Lớp học phần' } :
        { personal: 'Cá nhân', section: 'Lớp học phần' }} /></Field>
      {options[view] && <Field label="Đối tượng"><Select value={id} set={setId} options={options[view]} placeholder="Chọn đối tượng" /></Field>}
    </div>
    {scope === 'week' && <div className="calendar-controls">
      <div className="week-nav">
        <button type="button" className="quiet icon-only" aria-label="Tuần trước" onClick={() => {
          const previous = move(week, -7)
          setFocusDate(previous < start ? start : previous)
        }} disabled={week <= monday(start)}>‹</button>
        <strong aria-live="polite">{formatDate(week)} – {formatDate(last)}</strong>
        <button type="button" className="quiet icon-only" aria-label="Tuần sau" onClick={() => setFocusDate(move(week, 7))} disabled={last >= end}>›</button>
        {now >= start && now <= end && <button type="button" className="quiet jump-today" onClick={() => setFocusDate(now)}>Tuần này</button>}
      </div>
      <div className="calendar-jump"><Field label="Đi đến ngày">
        <input type="date" value={focusDate} min={start} max={end} onChange={e => {
          const date = e.target.value
          if (date && date >= start && date <= end) setFocusDate(date)
        }} />
      </Field></div>
      <div className="calendar-modes" role="group" aria-label="Kiểu hiển thị lịch">
        <button type="button" aria-pressed={layout === 'grid'} onClick={() => setLayout('grid')}>Lưới tuần</button>
        <button type="button" aria-pressed={layout === 'agenda'} onClick={() => setLayout('agenda')}>Theo ngày</button>
      </div>
    </div>}
    {scope === 'week' && (rows.length ? layout === 'grid' ?
      <PublishedWeek rows={rows} dates={dates} today={now} onItem={setEdit} /> :
      <WeekAgenda rows={rows} dates={dates} today={now} onItem={setEdit} /> :
      !data.error && <div className="empty" role="status">{empty}</div>)}
    {scope === 'semester' && <Table rows={rows} empty={empty} columns={[
      ['Ngày học', r => formatDate(r.start_at)], ['Giờ', r => r.start_at.slice(11,16) + ' – ' + r.end_at.slice(11,16)],
      ['Lớp học phần', r => <><strong>{r.section_code}</strong><small>{r.subject_name}</small></>], ['Phòng', 'room_code'],
      ['Giảng viên', 'staff_code'], ['Trạng thái', r => <><Tag value={r.status}/>{r.exception_reason && <small>{r.exception_reason}</small>}{r.source_session_id && <small>Buổi thay thế</small>}</>],
      ...(user.role === 'STAFF' || user.role === 'LECTURER' ? [['Thao tác', r => canChange(r) && <button className="quiet" onClick={() => setEdit(r)}>{user.role === 'LECTURER' ? 'Đề nghị đổi lịch' : 'Điều chỉnh'}</button>]] : [])
    ]} />}
    {scope === 'semester' && <Pagination query={paged} itemName="buổi học" />}
    {edit && <SessionEditor row={edit} user={user} catalog={catalog} semester={semester} readOnly={!canChange(edit)} close={() => setEdit(null)} reload={reload} />}
  </Panel>
}
