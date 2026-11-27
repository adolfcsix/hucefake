import { useState } from 'react'
import { api } from '../../services/api'
import { formatDate } from '../../utils/dates'
import { builder, codes, numeric } from '../../utils/forms'
import { statuses } from '../../constants/domain'
import { Editor } from '../../components/forms/Editor'
import { Field, Options, Select } from '../../components/forms/FormFields'
import { Tag } from '../../components/common/Tag'

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
