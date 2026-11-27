import { useId, useRef, useState } from 'react'
import { api } from '../../services/api'
import { Field, Select, Options } from '../../components/forms/FormFields'
import { codes, builder, numeric } from '../../utils/forms'
import { roomTypes } from '../../constants/domain'
import { ErrorBox } from '../../components/common/ErrorBox'
import { Editor } from '../../components/forms/Editor'
import { formatDate } from '../../utils/dates'

export function SectionEditor({ dialog, catalog, semester, close, reload }) {
  const { type, row } = dialog
  const [codeError,setCodeError] = useState(null)
  const codeInput = useRef(null)
  const codeErrorId = useId()
  const [form, set] = useState({
    semesterId: semester.id, subjectId: row?.subject_id ?? '', code: row?.code ?? '', capacity: row?.capacity ?? 40,
    sessionsPerWeek: row?.sessions_per_week ?? 1, periodsPerSession: row?.periods_per_session ?? 3,
    requiredRoomType: row?.required_room_type ?? 'LECTURE', startDate: row?.start_date?.slice(0, 10) ?? semester.start_date.slice(0, 10),
    endDate: row?.end_date?.slice(0, 10) ?? semester.end_date.slice(0, 10), version: row?.version ?? null,
    staffId: row?.staff_id ?? '', assistantIds: row?.assistant_ids || [], groupIds: row?.group_ids || []
  })
  const field = builder(set)
  const base = '/course-sections' + (row ? '/' + row.id : '')
  const titles = { edit: row ? 'Sửa lớp ' + row.code : 'Thêm lớp học phần', assign: 'Phân công giảng viên', groups: 'Lớp hành chính đối tượng', cancel: 'Hủy lớp ' + row?.code }
  function handleFieldError(error) {
    if (type !== 'edit' || error.details?.field !== 'code') return false
    setCodeError(error)
    codeInput.current?.focus()
    return true
  }
  async function save() {
    if (type === 'edit') await api(base, row ? 'PATCH' : 'POST', numeric(form, ['subjectId', 'capacity', 'sessionsPerWeek', 'periodsPerSession']))
    if (type === 'assign') await api(base + '/assignments', 'PUT', { staffId: Number(form.staffId), assistantIds: form.assistantIds, version: form.version })
    if (type === 'groups') await api(base + '/target-groups', 'PUT', { groupIds: form.groupIds, version: form.version })
    if (type === 'cancel') await api(base + '/cancel?version=' + form.version, 'POST')
    reload()
  }
  function checks(list, key) {
    return <div className="choices">{list.map(option => <label key={option.id}><input type="checkbox" checked={form[key].includes(option.id)}
      onChange={e => field(key)(e.target.checked ? [...form[key], option.id] : form[key].filter(id => id !== option.id))} />{option.code}</label>)}</div>
  }
  const input = (key, label, props = {}) => <Field label={label}><input required value={form[key]} onChange={e => field(key)(e.target.value)} {...props} /></Field>
  function latestFields(latest) {
    return [['Mã lớp',latest.code],['Học phần',latest.subject_name],['Sĩ số tối đa',latest.capacity],
      ['Số buổi mỗi tuần',latest.sessions_per_week],['Số tiết mỗi buổi',latest.periods_per_session],
      ['Loại phòng',roomTypes[latest.required_room_type]],['Ngày bắt đầu',formatDate(latest.start_date)],
      ['Ngày kết thúc',formatDate(latest.end_date)],['Giảng viên chính',latest.staff_code],
      ['Trợ giảng',catalog.lecturers.filter(x=>latest.assistant_ids?.includes(x.id)).map(x=>x.code).join(', ') || '—'],
      ['Lớp đối tượng',catalog.groups.filter(x=>latest.group_ids?.includes(x.id)).map(x=>x.code).join(', ') || '—']]
  }
  return <Editor title={titles[type]} onClose={close} onSubmit={save} submit={type === 'cancel' ? 'Hủy lớp' : 'Lưu'}
    latestFields={latestFields} onUseVersion={latest => field('version')(latest.version)} onFieldError={handleFieldError}>
    {type === 'edit' && <div className="form-grid">
      <div>
        <Field label="Mã lớp"><input ref={codeInput} required maxLength={40} value={form.code}
          aria-invalid={codeError ? true : undefined} aria-describedby={codeError ? codeErrorId : undefined}
          onChange={e => { setCodeError(null); field('code')(e.target.value) }} /></Field>
        {codeError && <div id={codeErrorId}><ErrorBox error={codeError} /></div>}
      </div>
      <Field label="Học phần"><Select value={form.subjectId} set={field('subjectId')} options={codes(catalog.subjects)} placeholder="Chọn học phần" required /></Field>
      {input('capacity','Sĩ số tối đa',{ type: 'number', min: 1 })}
      <Field label="Loại phòng"><Options value={form.requiredRoomType} set={field('requiredRoomType')} values={roomTypes} /></Field>
      {input('sessionsPerWeek','Số buổi mỗi tuần',{ type: 'number', min: 1, max: 6 })}
      {input('periodsPerSession','Số tiết mỗi buổi',{ type: 'number', min: 1, max: 6 })}
      {input('startDate','Ngày bắt đầu',{ type: 'date' })}
      {input('endDate','Ngày kết thúc',{ type: 'date' })}
    </div>}
    {type === 'assign' && <div className="stack">
      <Field label="Giảng viên chính"><Select value={form.staffId} set={value => set(previous => ({ ...previous, staffId: value, assistantIds: previous.assistantIds.filter(id => id !== Number(value)) }))} options={codes(catalog.lecturers)} placeholder="Chọn giảng viên" required /></Field>
      <fieldset><legend>Trợ giảng</legend>{checks(catalog.lecturers.filter(r => r.id !== Number(form.staffId)), 'assistantIds')}</fieldset>
    </div>}
    {type === 'groups' && <fieldset><legend>Lớp phải học học phần này</legend>{checks(catalog.groups, 'groupIds')}</fieldset>}
    {type === 'cancel' && <p>Lịch định kỳ đã xếp cho lớp nháp này sẽ được gỡ. Hồ sơ lớp vẫn còn trong danh sách với trạng thái Đã hủy.</p>}
  </Editor>
}
