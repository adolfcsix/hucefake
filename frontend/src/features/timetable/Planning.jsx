import { useState } from 'react'
import { api } from '../../services/api'
import { days } from '../../constants/domain'
import { ErrorBox } from '../../components/common/ErrorBox'
import { Field, Options, Select } from '../../components/forms/FormFields'
import { Panel } from '../../components/common/Panel'
import { useFetch } from '../../hooks/useFetch'
import { WeekGrid } from './WeekGrid'
import { same, filters } from './timetableFilters'
import { PatternEditor } from './PatternEditor'

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
