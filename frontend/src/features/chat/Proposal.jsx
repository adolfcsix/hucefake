import { roomTypes } from '../../constants/domain'
import { displayTime, proposalNames } from './chatPresentation'

function proposalInfo({action,preview}) {
  if (action==='GENERATE') {
    const p=preview.parameters
    return [['Quần thể',p.population],['Số thế hệ',p.generations],['Lai ghép',p.crossover],
      ['Đột biến',p.mutation],['Hạt giống',p.seed]]
  }
  if (action==='APPLY') return [['Lần chạy','#'+preview.runId],['Số buổi',preview.count],['Vi phạm cứng',preview.hardViolations]]
  if (action==='DRAFT') return [['Mã lớp',preview.code],['Học phần',preview.subject],['Sĩ số',preview.capacity],
    ['Buổi / tuần',preview.sessionsPerWeek],['Tiết / buổi',preview.periodsPerSession],
    ['Loại phòng',roomTypes[preview.requiredRoomType]],
    ['Thời gian',preview.startDate+' → '+preview.endDate]]
  return [['Thông báo chưa đọc',preview.count]]
}

function proposalResult(result) {
  if (result.action==='GENERATE') return 'Đã đưa lần chạy #'+result.id+' vào hàng đợi.'
  if (result.action==='APPLY') return 'Phương án #'+result.id+' đã áp dụng.'
  if (result.action==='DRAFT') return 'Đã tạo lớp nháp '+result.code+'.'
  return 'Đã đánh dấu '+result.marked+' thông báo.'
}

export function Proposal({ proposal, now, busy, decide }) {
  const outcome=proposal.outcome || 'PENDING'
  const pending=outcome==='PENDING' && Date.parse(proposal.expires_at)>now
  return <section className="chat-proposal">
    <h3>{proposalNames[proposal.action]}</h3>
    {proposal.semester && <p>Học kỳ {proposal.semester}</p>}
    <dl>{proposalInfo(proposal).map(([key,value])=><div key={key}><dt>{key}</dt><dd>{value}</dd></div>)}</dl>
    {pending ? <>
      <small>Hạn xác nhận {displayTime.format(new Date(proposal.expires_at))}</small>
      <div className="button-row">
        <button type="button" disabled={busy} onClick={()=>decide(proposal.id,'confirm')}>Xác nhận</button>
        <button type="button" className="quiet" disabled={busy} onClick={()=>decide(proposal.id,'cancel')}>Hủy đề xuất</button>
      </div>
    </> : <p role="status">{outcome==='CANCELLED'?'Đã hủy đề xuất.':outcome==='CONFIRMED' && proposal.result ?
      proposalResult(proposal.result) : 'Đề xuất đã hết hạn.'}</p>}
  </section>
}
