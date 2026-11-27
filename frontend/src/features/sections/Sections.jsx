import { useState } from 'react'
import { downloadFile } from '../../services/api'
import { Pagination } from '../../components/common/Pagination'
import { usePagedQuery } from '../../hooks/usePagedQuery'
import { Field, Select, Options } from '../../components/forms/FormFields'
import { codes } from '../../utils/forms'
import { Tag } from '../../components/common/Tag'
import { Panel } from '../../components/common/Panel'
import { Table } from '../../components/common/Table'
import { ErrorBox } from '../../components/common/ErrorBox'
import { formatDate } from '../../utils/dates'
import { draftFromSection, encodeDraftCsv, encodeDraftJson } from './SectionTransfer'
import { DraftImport } from './DraftImport'
import { SectionEditor } from './SectionEditor'

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
    if (format==='json') downloadFile(encodeDraftJson(drafts),'application/json;charset=utf-8','lop-nhap-huce.json')
    else downloadFile(encodeDraftCsv(drafts),'text/csv;charset=utf-8','lop-nhap-huce.csv')
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
