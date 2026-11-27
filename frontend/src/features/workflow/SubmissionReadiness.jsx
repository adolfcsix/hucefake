import { days } from '../../constants/domain'
import { Table } from '../../components/common/Table'

export function SubmissionReadiness({ data, hasError, requestId, refresh }) {
  const message = data ? data.pending ? 'Học kỳ đang có phiên bản chờ duyệt.' :
    !data.draftCount ? 'Học kỳ chưa có lớp nháp để gửi duyệt.' :
    !data.eligible ? 'Khoa của bạn chưa có lớp nháp trong học kỳ này.' :
    data.ready ? data.draftCount + ' lớp nháp đủ điều kiện gửi duyệt.' :
    data.blockedSections.length + ' lớp nháp cần xử lý trước khi gửi duyệt.' :
    hasError ? 'Chưa tải được kết quả kiểm tra.' : 'Đang kiểm tra điều kiện gửi duyệt…'
  return <section className="submission-readiness" aria-label="Điều kiện gửi duyệt">
    <div className="button-row">
      <p role="status">{message}</p>
      <button type="button" className="quiet" onClick={refresh} disabled={!data && !hasError}>Kiểm tra lại</button>
    </div>
    {requestId && <small className="request-reference">Mã yêu cầu: {requestId}</small>}
    {data?.blockedSections.length > 0 && <Table rows={data.blockedSections} columns={[
      ['Lớp học phần', row => <><strong>{row.code}</strong><small>{row.subjectName}</small></>],
      ['Buổi đã xếp', row => row.scheduled + '/' + row.required],
      ['Cần xử lý', row => <ul className="submission-issues">
        {row.missing.map(issue => <li key={issue.rule}>{issue.message}</li>)}
        {row.conflicts.map(slot => <li key={slot.id}>
          <strong>{days[slot.dayOfWeek - 1]}, tiết {slot.periodFrom} · {slot.roomCode}</strong>
          {slot.issues.map((issue,index) => <small key={index}>
            {issue.rule} · {issue.message || 'Trùng lịch đã công bố.'}
            {(issue.sectionCode || issue.section_code) && ' · ' + (issue.sectionCode || issue.section_code)}
            {issue.groupCodes?.length > 0 && ' · ' + issue.groupCodes.join(', ')}
          </small>)}
        </li>)}
      </ul>],
      ['Thao tác', row => row.editable ? <>
        {row.missing.some(issue => issue.rule === 'BR02') && <a href="#sections">Phân công</a>}
        {(row.conflicts.length > 0 || row.missing.some(issue => issue.rule === 'BR10')) && <a href="#planning">Xếp lịch thủ công</a>}
      </> : <small>Liên hệ khoa phụ trách</small>]
    ]} />}
  </section>
}
