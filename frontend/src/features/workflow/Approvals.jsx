import { useState } from 'react'
import { api } from '../../services/api'
import { Editor } from '../../components/forms/Editor'
import { ErrorBox } from '../../components/common/ErrorBox'
import { Field } from '../../components/forms/FormFields'
import { Panel } from '../../components/common/Panel'
import { Table } from '../../components/common/Table'
import { Tag } from '../../components/common/Tag'
import { useFetch } from '../../hooks/useFetch'
import { WeekGrid } from '../timetable/WeekGrid'
import { SubmissionReadiness } from './SubmissionReadiness'

export function Approvals({ user, semester, catalog, revision, reload }) {
  const [selected, setSelected] = useState(null)
  const [returning, setReturning] = useState(false)
  const [reason, setReason] = useState('')
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)
  const state = useFetch(() => api('/timetable-versions?semesterId=' + semester.id), [semester.id, revision])
  const staff = user.role === 'STAFF'
  const validation = useFetch(async () => staff ? {
    revision, result:await api('/timetable-versions/readiness?semesterId=' + semester.id)
  } : null, [staff, semester.id, revision])
  const rejected = error?.details?.blockedSections ? error.details : null
  const readiness = rejected || (validation.data?.revision === revision ? validation.data.result : null)
  const versions = state.data || []
  const current = versions.find(r => r.id === selected)
  const approval = useFetch(() => current?.approval_request_id ? api('/approvals/' + current.approval_request_id) : Promise.resolve(null), [current?.approval_request_id, revision])
  const patterns = useFetch(() => api('/schedule-patterns?semesterId=' + semester.id), [semester.id, revision])
  const snapshot = (approval.data?.id === current?.approval_request_id ? approval.data?.payload || [] : []).map(slot => {
    const pattern = (patterns.data || []).find(p => p.id === slot.id)
    return { id:slot.id, day_of_week:slot.day, period_from:slot.first, period_count:slot.count,
      section_code:slot.sectionCode || pattern?.section_code || 'Lớp #' + slot.section,
      subject_name:slot.subjectName || pattern?.subject_name,
      room_code:slot.roomCode || catalog.rooms.find(r => r.id === slot.room)?.code }
  })
  const roomUsage = new Map()
  for (const slot of snapshot) {
    const code = slot.room_code || '—'
    const room = roomUsage.get(code) || { id:code, slots:0, periods:0 }
    room.slots++
    room.periods += slot.period_count
    roomUsage.set(code,room)
  }
  async function perform(func) {
    setBusy(true)
    setError(null)
    try { await func(); reload() }
    catch (e) { setError(e) }
    finally { setBusy(false) }
  }
  async function submit() {
    const version = await api('/timetable-versions','POST',{ semesterId:semester.id })
    await api('/timetable-versions/' + version.id + '/submit','POST')
    setSelected(version.id)
  }
  async function publish() {
    await api('/approvals/' + current.approval_request_id + '/publish','POST')
  }
  return <>
    <Panel title="Phiên bản thời khóa biểu" action={staff && <button disabled={busy || !readiness?.ready || versions.some(r => r.status === 'SUBMITTED')}
      onClick={() => perform(submit)}>{busy ? 'Đang gửi…' : 'Gửi duyệt thời khóa biểu'}</button>}>
      <ErrorBox error={(!rejected && error) || state.error || approval.error || patterns.error || validation.error} />
      {staff && <SubmissionReadiness data={readiness} hasError={!!validation.error} requestId={rejected && error.request_id}
        refresh={() => { setError(null); reload() }} />}
      <Table rows={versions} columns={[
        ['Phiên bản',r => 'Bản ' + r.version_no],['Người lập','created_by_name'],['Số lớp','section_count'],['Trạng thái',r => <Tag value={r.status}/>],
        ['Quyết định',r => <>{r.decision_status && <Tag value={r.decision_status} />}{r.decision_reason && <small>{r.decision_reason}</small>}</>],
        ['Hồ sơ',r => r.approval_request_id && <button className="quiet" onClick={() => setSelected(r.id)}>Xem phương án</button>]
      ]}/>
    </Panel>
    {current?.approval_request_id && <Panel title={'Phương án bản ' + current.version_no}
      action={user.role === 'MANAGER' && current.status === 'SUBMITTED' && <div className="button-row">
        {['PENDING','APPROVED'].includes(current.decision_status) && <button className="quiet" disabled={busy} onClick={() => {setReason(''); setReturning(true)}}>Trả lại</button>}
        <button disabled={busy} onClick={() => perform(publish)}>{current.decision_status === 'APPROVED' ? 'Công bố' : 'Duyệt và công bố'}</button>
      </div>}>
      {snapshot.length > 0 && <>
        <WeekGrid rows={snapshot} maxPeriod={Math.max(12, ...snapshot.map(r => r.period_from + r.period_count - 1))} />
        <h3 className="room-usage-heading">Sử dụng phòng trong phương án</h3>
        <Table rows={[...roomUsage.values()]} columns={[[ 'Phòng','id' ],[ 'Buổi/tuần','slots' ],[ 'Tiết/tuần','periods' ]]} />
      </>}
      {!snapshot.length && !approval.error && <div className="empty">Đang tải phương án…</div>}
    </Panel>}
    {returning && <Editor title="Trả lại thời khóa biểu" onClose={() => setReturning(false)} submit="Trả lại" onSubmit={async () => {
      await api('/approvals/' + current.approval_request_id + '/decide','POST',{ decision:'RETURNED', reason })
      reload()
    }}><Field label="Lý do trả lại"><textarea value={reason} onChange={e => setReason(e.target.value)} maxLength={500} required rows={4} /></Field></Editor>}
  </>
}
