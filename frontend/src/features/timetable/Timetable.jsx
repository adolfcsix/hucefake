import { useEffect, useRef, useState } from 'react'
import { api, checkScheduleConflicts, downloadCsv, downloadFile } from '../../services/api'
import { PublishedWeek, WeekAgenda } from './Calendar'
import { makeCalendar } from './CalendarExport'
import { Pagination } from '../../components/common/Pagination'
import { usePagedQuery } from '../../hooks/usePagedQuery'
import { addCalendarDays as move, formatDate, vietnamToday } from '../../utils/dates'
import { builder, codes, numeric, values } from '../../utils/forms'
import { days, dayOptions, statuses } from '../../constants/domain'
import { Editor } from '../../components/forms/Editor'
import { ErrorBox } from '../../components/common/ErrorBox'
import { Field, Options, Select } from '../../components/forms/FormFields'
import { Panel } from '../../components/common/Panel'
import { Table } from '../../components/common/Table'
import { Tag } from '../../components/common/Tag'
import { useFetch } from '../../hooks/useFetch'
import { WeekGrid } from './WeekGrid'

const same = (a, b) => Number(a) === Number(b)
const toDate = value => new Date(value + 'T12:00:00Z')
const weekday = date => (toDate(date).getUTCDay() + 6) % 7 + 1
const monday = date => move(date, 1 - weekday(date))

function filters(catalog, sections) {
  return { room: codes(catalog.rooms), staff: codes(catalog.lecturers), group: codes(catalog.groups), section: codes(sections) }
}

export function Planning({ user, catalog, semester, revision, reload }) {
  const [view, setView] = useState('all')
  const [id, setId] = useState('')
  const [sectionId, setSectionId] = useState('')
  const [edit, setEdit] = useState(null)
  const [placementConflict, setPlacementConflict] = useState(null)
  const staff = user.role === 'STAFF'
  const patterns = useFetch(() => api('/schedule-patterns?semesterId=' + semester.id), [semester.id, revision])
  const sections = useFetch(() => api('/course-sections?semesterId=' + semester.id), [semester.id, revision])
  const versions = useFetch(() => api('/timetable-versions?semesterId=' + semester.id), [semester.id, revision])
  const rows = (patterns.data || []).filter(p => {
    if (view === 'group') return p.group_ids.includes(Number(id))
    if (view === 'section') return same(p.section_id, id)
    return view === 'all' || same(p[view + '_id'], id)
  })
  const drafts = (sections.data || []).filter(r => r.status === 'DRAFT' && r.staff_id && r.scheduled < r.sessions_per_week)
  const selectedRoom = view === 'room' ? catalog.rooms.find(room => same(room.id, id)) : null
  const campusId = selectedRoom?.campus_id ?? catalog.campuses[0]?.id
  const activePeriods = catalog.periods.filter(p => p.campus_id === campusId && p.effective_from.slice(0,10) <= semester.start_date.slice(0,10) &&
    (!p.effective_to || p.effective_to.slice(0,10) >= semester.start_date.slice(0,10)))
  const canEdit = staff && versions.data && !versions.data.some(v => v.status === 'SUBMITTED')
  function open(day, period, section = sectionId) {
    const target = section ? drafts.find(s => same(s.id, section)) : drafts[0]
    if (canEdit && target) {
      const room = catalog.campuses.some(c => c.id === selectedRoom?.campus_id) ? selectedRoom : null
      setPlacementConflict(null)
      setEdit({ day, period, section: target, room })
    }
  }
  return <>
    <Panel title="Lịch định kỳ" action={canEdit && drafts.length > 0 && <button onClick={() => open(1, 1)}>Xếp buổi học</button>}>
      <ErrorBox error={patterns.error || sections.error || versions.error} />
      <div className="toolbar">
        <Field label="Xem theo"><Options value={view} set={v => { setView(v); setId(''); setPlacementConflict(null) }} values={{ all: 'Tất cả', room: 'Phòng', staff: 'Giảng viên', group: 'Lớp hành chính', section: 'Lớp học phần' }} /></Field>
        {view !== 'all' && <Field label="Đối tượng"><Select value={id} set={value => { setId(value); setPlacementConflict(null) }} options={filters(catalog, sections.data || [])[view]} placeholder="Chọn đối tượng" /></Field>}
      </div>
      {staff && !canEdit && versions.data && <p className="notice">Học kỳ đang chờ duyệt. Có thể xem lịch định kỳ trong thời gian này.</p>}
      {canEdit && <div className="planning-tools">
        <Field label="Buổi chưa xếp"><Select value={sectionId} set={setSectionId} options={drafts.map(s => [s.id, s.code + ' · còn ' + (s.sessions_per_week - s.scheduled) + ' buổi'])} placeholder={drafts.length ? 'Chọn lớp học phần' : 'Không còn buổi đủ điều kiện'} /></Field>
        {drafts.length > 0 && <div className="drag-list">{drafts.map(s => <button type="button" key={s.id} className={'quiet ' + (same(sectionId, s.id) ? 'selected' : '')}
          draggable onDragStart={e => e.dataTransfer.setData('text/plain', String(s.id))} onClick={() => setSectionId(s.id)}>{s.code}</button>)}</div>}
      </div>}
      {placementConflict && !edit && <p className="notice placement-conflict-summary" role="status">
        {days[placementConflict.day - 1]}, tiết {placementConflict.period} · {placementConflict.sectionCode} · {placementConflict.roomCode} · {placementConflict.message}
        {placementConflict.otherSection && ' Lớp liên quan: ' + placementConflict.otherSection + '.'}
      </p>}
      <WeekGrid rows={rows} maxPeriod={Math.max(12, ...activePeriods.map(p => p.period_no), ...rows.map(r => r.period_from + r.period_count - 1), placementConflict?.period ?? 0)} periods={activePeriods}
        placementConflict={placementConflict}
        onSlot={canEdit && drafts.length > 0 ? open : undefined}
        onDropSlot={canEdit && drafts.length > 0 ? (id, day, period) => open(day, period, id) : undefined}
        onItem={item => {
          const section = (sections.data || []).find(s => s.id === item.section_id)
          setPlacementConflict(null)
          setEdit({ item, section, readOnly: !canEdit || !section || item.section_status !== 'DRAFT' || !catalog.campuses.some(c => c.id === catalog.rooms.find(r => r.id === item.room_id)?.campus_id) })
        }} />
    </Panel>
    {edit && <PatternEditor edit={edit} catalog={catalog} drafts={drafts} close={() => setEdit(null)} reload={reload} onConflict={setPlacementConflict} />}
  </>
}

function eligibleRooms(catalog, section) {
  return catalog.rooms.filter(room => section && room.status === 'ACTIVE' && room.type === section.required_room_type &&
    room.capacity >= section.capacity && catalog.campuses.some(c => c.id === room.campus_id))
}

function defaultRoom(catalog, section, selected) {
  if (selected) return selected.id
  const rooms = eligibleRooms(catalog, section)
  return rooms.length === 1 ? rooms[0].id : ''
}

function conflictCell(slot, verdict, sectionCode, roomCode) {
  const issue = verdict.issues?.[0]
  if (!issue) return null
  const otherSection = issue.sectionCode || issue.section_code
  return { day: slot.dayOfWeek, period: slot.periodFrom, sectionCode, roomCode,
    otherSection: otherSection !== sectionCode ? otherSection : null,
    message: issue.message || 'Trùng với lịch đã công bố.' }
}

function PatternEditor({ edit, catalog, drafts, close, reload, onConflict }) {
  const { item, section, readOnly } = edit
  const [form, set] = useState(() => ({ sectionId: section?.id ?? '', roomId: item?.room_id ?? defaultRoom(catalog, section, edit.room), dayOfWeek: item?.day_of_week ?? edit.day,
    periodFrom: item?.period_from ?? edit.period, effectiveFrom: item?.effective_from?.slice(0, 10) ?? section?.start_date?.slice(0, 10),
    effectiveTo: item?.effective_to?.slice(0, 10) ?? section?.end_date?.slice(0, 10), version: item?.version ?? null }))
  const [assessment, setAssessment] = useState(null)
  const [revision, recheck] = useState(0)
  const request = useRef(0)
  const [remove, setRemove] = useState(false)
  const [observed, setObserved] = useState(item)
  const field = builder(set)
  const payload = () => numeric({ ...form, patternId: item?.id ?? null }, ['sectionId','roomId','dayOfWeek','periodFrom'])
  const current = item ? section : drafts.find(s => same(s.id, form.sectionId))
  const rooms = eligibleRooms(catalog, current)
  const chosenRoom = catalog.rooms.find(room => same(room.id, form.roomId))
  const roomOptions = codes(rooms)
  if (chosenRoom && !rooms.includes(chosenRoom)) roomOptions.unshift([chosenRoom.id, chosenRoom.code + ' · Không đủ điều kiện'])
  const previewKey = !readOnly && !remove && form.sectionId && form.roomId && form.effectiveFrom && form.effectiveTo
    ? JSON.stringify(payload()) : null
  const result = assessment?.key === previewKey && assessment?.revision === revision ? assessment.result : null
  const checking = !!previewKey && !result
  const sectionCode = current?.code
  const roomCode = chosenRoom?.code
  useEffect(() => {
    const generation = ++request.current
    setAssessment(null)
    onConflict(null)
    if (!previewKey) return
    const slot = JSON.parse(previewKey)
    const timer = setTimeout(async () => {
      try {
        const verdict = await checkScheduleConflicts(slot)
        if (request.current !== generation) return
        setAssessment({ key: previewKey, revision, result: verdict })
        onConflict(conflictCell(slot, verdict, sectionCode, roomCode))
      } catch (error) {
        if (request.current === generation) setAssessment({ key: previewKey, revision, result: error })
      }
    }, 200)
    return () => { clearTimeout(timer); request.current++ }
  }, [previewKey, revision, sectionCode, roomCode, onConflict])
  function suggestion(s) {
    set(previous => ({ ...previous, roomId: s.roomId, dayOfWeek: s.dayOfWeek, periodFrom: s.periodFrom }))
  }
  async function save() {
    if (readOnly) return
    if (remove) await api('/schedule-patterns/' + item.id + '?version=' + form.version, 'DELETE')
    else {
      const slot = payload()
      const generation = request.current
      try { await api('/schedule-patterns' + (item ? '/' + item.id : ''), item ? 'PATCH' : 'POST', slot) }
      catch (error) {
        if (error.code === 'SCHEDULE_CONFLICT' && request.current === generation) {
          request.current++
          setAssessment({ key: previewKey, revision, result: { reportedBySave: true } })
          onConflict(conflictCell(slot, error.details, sectionCode, roomCode))
        }
        throw error
      }
    }
    onConflict(null)
    reload()
  }
  return <Editor title={readOnly ? 'Chi tiết lịch định kỳ' : remove ? 'Gỡ lịch định kỳ' : item ? 'Sửa lịch định kỳ' : 'Xếp buổi học'} onClose={close} onSubmit={save}
    onSuggestion={suggestion} scheduleKey={previewKey} submit={readOnly ? 'Xong' : remove ? 'Gỡ lịch' : 'Lưu lịch'}
    latestFields={latest => [['Thứ',days[latest.day_of_week-1]],['Tiết bắt đầu',latest.period_from],
      ['Phòng',catalog.rooms.find(room=>room.id===latest.room_id)?.code],['Từ ngày',formatDate(latest.effective_from)],
      ['Đến ngày',formatDate(latest.effective_to)]]}
    onUseVersion={latest => { setObserved(latest); field('version')(latest.version) }}
    extra={!readOnly && !remove && <>
      <button type="button" className="quiet" disabled={checking} onClick={event => {
        if (event.currentTarget.form.reportValidity()) recheck(value => value + 1)
      }}>Kiểm tra xung đột</button>
      {item && <button type="button" className="quiet danger" onClick={() => setRemove(true)}>Gỡ lịch</button>}
    </>}>
    {readOnly ? <p><strong>{item.section_code} · {item.subject_name}</strong><br />{days[item.day_of_week - 1]}, tiết {item.period_from} · {item.room_code} · {item.staff_code}</p>
      : remove ? <p>Gỡ buổi <strong>{item.section_code}</strong> vào {days[observed.day_of_week - 1]}, tiết {observed.period_from}?</p> : <>
        {checking && <p role="status" className="notice">Đang kiểm tra xung đột…</p>}
        {result?.valid ? <p role="status" className="notice success">Khung đã chọn không có xung đột.</p>
          : !result?.reportedBySave && <ErrorBox error={result} onSuggestion={suggestion} />}
        {!form.roomId && <p className="notice">Chọn phòng để kiểm tra khung giờ đã đặt.</p>}
        <div className="form-grid">
          <Field label="Lớp học phần"><Select value={form.sectionId} set={value => {
            const target = drafts.find(s => same(s.id, value))
            set(previous => ({ ...previous, sectionId: value, roomId: defaultRoom(catalog, target, edit.room),
              effectiveFrom: target.start_date.slice(0,10), effectiveTo: target.end_date.slice(0,10) }))
          }} options={codes(item ? [section] : drafts)} disabled={!!item} required /></Field>
          <Field label="Phòng"><Select value={form.roomId} set={field('roomId')} options={roomOptions} placeholder="Chọn phòng" required /></Field>
          <Field label="Thứ"><Select value={form.dayOfWeek} set={field('dayOfWeek')} options={dayOptions} /></Field>
          <Field label={'Tiết bắt đầu · ' + (current?.periods_per_session || '—') + ' tiết'}><Select value={form.periodFrom} set={field('periodFrom')} options={values(1,15).map(n => [n,n])} /></Field>
          <Field label="Từ ngày"><input type="date" value={form.effectiveFrom} onChange={e => field('effectiveFrom')(e.target.value)} required /></Field>
          <Field label="Đến ngày"><input type="date" value={form.effectiveTo} onChange={e => field('effectiveTo')(e.target.value)} required /></Field>
        </div>
      </>}
  </Editor>
}

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

export function SessionEditor({ row, user, catalog, semester, close, reload, readOnly = false, request = false, returning = false }) {
  const lecturer = user.role === 'LECTURER'
  const [form, set] = useState({ action: returning ? 'RETURNED' : request ? row.action : 'CANCEL', reason: request && !returning ? row.reason : '',
    date: (row.start_at || row.session_time)?.slice(0,10) || '', roomId: '', periodFrom: row.period_from ?? 1 })
  const field = builder(set)
  const replacement = !lecturer && ['ROOM_CHANGE', 'RESCHEDULE'].includes(form.action)
  const rooms = (catalog.rooms || []).filter(r => r.status === 'ACTIVE' && catalog.campuses.some(c => c.id === r.campus_id) &&
    (form.action !== 'ROOM_CHANGE' || r.id !== row.room_id))
  const actions = lecturer ? { CANCEL: statuses.CANCEL, RESCHEDULE: statuses.RESCHEDULE } :
    { CANCEL: statuses.CANCEL, RESCHEDULE: statuses.RESCHEDULE, ROOM_CHANGE: statuses.ROOM_CHANGE }
  function suggestion(s) { set(p => ({ ...p, roomId: s.roomId, periodFrom: s.periodFrom })) }
  async function save() {
    if (readOnly) return
    const body = numeric(form, ['roomId','periodFrom'])
    if (request) await api('/session-change-requests/' + row.id + '/decide', 'POST', body)
    else if (lecturer) await api('/class-sessions/' + row.id + '/requests', 'POST', body)
    else if (form.action === 'RESCHEDULE') await api('/class-sessions', 'POST', { ...body, sourceSessionId:row.id })
    else if (form.action === 'ROOM_CHANGE') await api('/class-sessions/' + row.id, 'PATCH', body)
    else await api('/class-sessions/' + row.id + '/exceptions', 'POST', body)
    reload()
  }
  return <Editor title={readOnly ? 'Chi tiết buổi học' : returning ? 'Trả lại đề nghị' : request ? 'Xử lý đề nghị' : lecturer ? 'Đề nghị nghỉ hoặc học bù' : 'Điều chỉnh buổi học'}
    onClose={close} onSubmit={save} submit={readOnly ? 'Xong' : lecturer ? 'Gửi đề nghị' : 'Xác nhận'} onSuggestion={suggestion}>
    <div className="session-details">
      <p><strong>{row.subject_name || row.section_code}</strong>{row.subject_name && <small>{row.section_code}</small>}</p>
      <dl>
        <div><dt>Ngày học</dt><dd>{formatDate(row.start_at || row.session_time)}</dd></div>
        <div><dt>Giờ</dt><dd>{(row.start_at || row.session_time)?.slice(11,16)}{row.end_at && ' – ' + row.end_at.slice(11,16)}</dd></div>
        {row.room_code && <div><dt>Phòng</dt><dd>{row.room_code}</dd></div>}
        {row.staff_code && <div><dt>Giảng viên</dt><dd>{row.staff_code}</dd></div>}
      </dl>
    </div>
    {readOnly ? <div className="button-row"><Tag value={row.status} />{row.source_session_id && <span>Buổi thay thế</span>}{row.exception_reason && <p>{row.exception_reason}</p>}</div> : <div className="form-grid">
      {!returning && <Field label="Thay đổi"><Options value={form.action} set={field('action')} values={actions} disabled={request} /></Field>}
      {replacement && <Field label="Phòng thay thế"><Select value={form.roomId} set={field('roomId')} options={codes(rooms)} placeholder="Chọn phòng" required /></Field>}
      {replacement && form.action === 'RESCHEDULE' && <>
        <Field label="Ngày học bù"><input type="date" min={semester?.start_date} max={semester?.end_date} value={form.date} onChange={e => field('date')(e.target.value)} required /></Field>
        <Field label="Tiết bắt đầu"><input type="number" min="1" max="15" value={form.periodFrom} onChange={e => field('periodFrom')(e.target.value)} required /></Field>
      </>}
      <Field label="Lý do"><textarea value={form.reason} onChange={e => field('reason')(e.target.value)} required maxLength={500} rows={3} /></Field>
    </div>}
  </Editor>
}

export function ChangeRequests({ user, catalog, semester, revision, reload }) {
  const [edit, setEdit] = useState(null)
  const data = usePagedQuery('/session-change-requests?semesterId=' + semester.id,revision)
  return <Panel title="Đề nghị nghỉ hoặc học bù">
    <ErrorBox error={data.error} />
    <Table columns={[
      ['Lớp học phần','section_code'], ['Buổi học','session_time'], ['Đề nghị',r => statuses[r.action]],
      ['Lý do','reason'], ['Trạng thái',r => <><Tag value={r.status} />{r.decision_reason && <small>{r.decision_reason}</small>}</>],
      ...(user.role === 'STAFF' ? [['Thao tác',r => r.status === 'PENDING' && <div className="button-row">
        <button className="quiet" onClick={() => setEdit({ row:r })}>Xử lý</button>
        <button className="quiet" onClick={() => setEdit({ row:r, returning:true })}>Trả lại</button>
      </div>]] : [])
    ]} rows={data.data?.items || []} />
    <Pagination query={data} itemName="đề nghị" />
    {edit && <SessionEditor {...edit} request user={user} catalog={catalog} semester={semester} close={() => setEdit(null)} reload={reload} />}
  </Panel>
}
