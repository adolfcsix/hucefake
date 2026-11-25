import { useId, useRef, useState } from 'react'
import { api, downloadFile } from './api'
import { Pagination, usePagedQuery } from './Pagination'
import { Field, Select, Options, codes, roomTypes, Tag, Panel, Table, ErrorBox, Editor, builder, numeric, formatDate } from './ui'
import { decodeDraftFile, draftFromSection, encodeDraftCsv, encodeDraftJson } from './SectionTransfer'

export default function Sections({ user, catalog, semester, revision, reload }) {
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState('')
  const [subject, setSubject] = useState('')
  const [dialog, setDialog] = useState(null)
  const [importOpen,setImportOpen] = useState(false)
  const [imported,setImported] = useState(null)
  const data = usePagedQuery('/course-sections?' + new URLSearchParams({ semesterId:semester.id, search,status,
    ...(subject && { subjectId:subject }) }),revision)
  const staff = user.role === 'STAFF'
  const rows = data.data?.items || []
  const drafts = rows.filter(row=>row.status==='DRAFT').map(row=>draftFromSection(row,semester.code))
  const showActions = staff && rows.some(row => row.status === 'DRAFT')
  function exportDrafts(format) {
    if (format==='json') downloadFile(encodeDraftJson(drafts),'application/json;charset=utf-8','lop-nhap-fm05.json')
    else downloadFile(encodeDraftCsv(drafts),'text/csv;charset=utf-8','lop-nhap-fm05.csv')
  }
  return <>
    <Panel title="Danh sách lớp học phần" action={staff && <button onClick={() => setDialog({ type: 'edit' })}>Thêm lớp học phần</button>}>
      <ErrorBox error={data.error} />
      {imported && <p className="notice success" role="status">Đã nhập {imported} lớp nháp vào học kỳ {semester.code}.</p>}
      <div className="toolbar">
        <Field label="Tìm lớp"><input type="search" placeholder="Mã lớp hoặc tên học phần" value={search} onChange={e => setSearch(e.target.value)} /></Field>
        <Field label="Học phần"><Select value={subject} set={setSubject} options={codes(catalog.subjects)} placeholder="Tất cả học phần" /></Field>
        <Field label="Trạng thái"><Options value={status} set={setStatus} values={{ DRAFT: 'Nháp', OPEN: 'Đang mở', CANCELLED: 'Đã hủy', CLOSED: 'Đã đóng' }} placeholder="Tất cả trạng thái" /></Field>
        {data.data && <small className="result-count" aria-live="polite">{data.data.total} lớp học phần</small>}
      </div>
      {staff && <div className="button-row section-transfer" role="group" aria-label="Nhập xuất lớp học phần">
        <button className="quiet" onClick={() => { setImported(null); setImportOpen(true) }}>Nhập lớp nháp</button>
        <button className="quiet" disabled={!drafts.length} onClick={() => exportDrafts('csv')}>Xuất lớp nháp CSV</button>
        <button className="quiet" disabled={!drafts.length} onClick={() => exportDrafts('json')}>Xuất lớp nháp JSON</button>
        <small>Xuất các lớp nháp trên trang đang xem.</small>
      </div>}
      <Table rows={rows} empty={data.data ? 'Không tìm thấy lớp học phần.' : 'Đang tải lớp học phần…'} columns={[
        ['Lớp học phần', r => <><strong>{r.code}</strong><small>{r.subject_name}</small></>],
        ['Giảng viên chính', r => r.staff_code || <span className="warning-text">Chưa phân công</span>],
        ['Sĩ số', 'capacity'], ['Số buổi/tuần', r => r.scheduled + '/' + r.sessions_per_week],
        ['Khoảng ngày', r => formatDate(r.start_date) + ' – ' + formatDate(r.end_date)], ['Trạng thái', r => <Tag value={r.status} />],
        ...(showActions ? [['Thao tác', r => r.status === 'DRAFT' && <div className="button-row row-actions">
          {[['edit','Sửa'], ['assign','Phân công'], ['groups','Lớp đối tượng'], ['cancel','Hủy lớp']].map(([type,label]) =>
            <button className="quiet" key={type} onClick={() => setDialog({ type, row: r })}>{label}</button>)}
        </div>]] : [])
      ]} />
      <Pagination query={data} itemName="lớp học phần" />
    </Panel>
    {dialog && <SectionEditor dialog={dialog} catalog={catalog} semester={semester} close={() => setDialog(null)} reload={reload} />}
    {importOpen && <DraftImport catalog={catalog} semester={semester} close={() => setImportOpen(false)}
      done={count => { setImported(count); setSearch(''); setStatus('DRAFT'); setSubject(''); reload() }} />}
  </>
}

function DraftImport({ catalog,semester,close,done }) {
  const [rows,setRows] = useState(null)
  const [error,setError] = useState(null)
  const reading = useRef(0)
  async function selectFile(file) {
    const request = ++reading.current
    setRows(null)
    setError(null)
    if (!file) return
    try {
      if (file.size>110_000) throw new Error('Tệp lớn quá 110 KB. Chia danh sách thành nhiều tệp nhỏ hơn.')
      const result = decodeDraftFile(file.name,await file.text())
      if (reading.current===request) setRows(result)
    } catch (error) { if (reading.current===request) setError(error) }
  }
  function downloadTemplate() {
    const sample = { semesterCode:semester.code,subjectCode:catalog.subjects[0]?.code || '',code:'LOP-MAU-01',
      capacity:40,sessionsPerWeek:1,periodsPerSession:3,requiredRoomType:'LECTURE',
      startDate:semester.start_date,endDate:semester.end_date }
    downloadFile(encodeDraftCsv([sample]),'text/csv;charset=utf-8','mau-nhap-lop-fm05.csv')
  }
  return <Editor title="Nhập lớp học phần" submit="Nhập lớp nháp" onClose={close} onSubmit={async () => {
    if (!rows) throw new Error('Hãy chọn tệp hợp lệ trước khi nhập.')
    const result = await api('/course-sections/import','POST',{ semesterId:semester.id,rows })
    done(result.imported)
  }}>
    <div className="stack">
      <p>Học kỳ <strong>{semester.code}</strong>. Tệp chỉ tạo lớp nháp mới. Nếu một dòng có lỗi, toàn bộ tệp sẽ không được ghi.</p>
      <div><button type="button" className="quiet" onClick={downloadTemplate}>Tải mẫu CSV</button></div>
      <Field label="Tệp lớp học phần"><input type="file" accept=".csv,.json" required onChange={e=>selectFile(e.target.files[0])}/></Field>
      <ErrorBox error={error} />
      {rows && <p role="status">Đã đọc {rows.length} dòng. Bấm Nhập lớp nháp để kiểm tra và lưu.</p>}
    </div>
  </Editor>
}

function SectionEditor({ dialog, catalog, semester, close, reload }) {
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
