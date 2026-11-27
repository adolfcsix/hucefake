import { useRef, useState } from 'react'
import { api, downloadFile } from '../../services/api'
import { Field } from '../../components/forms/FormFields'
import { ErrorBox } from '../../components/common/ErrorBox'
import { Editor } from '../../components/forms/Editor'
import { decodeDraftFile, encodeDraftCsv } from './SectionTransfer'

export function DraftImport({ catalog,semester,close,done }) {
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
    downloadFile(encodeDraftCsv([sample]),'text/csv;charset=utf-8','mau-nhap-lop-huce.csv')
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
