import { days } from '../../constants/domain'
import { formatDate } from '../../utils/dates'

export function ErrorBox({ error, onSuggestion }) {
  if (!error) return null
  const details = error.details || error
  return <div className="error" role="alert">
    <strong>{error.message || 'Lịch dự kiến có xung đột.'}</strong>
    {details.issues?.map((issue, i) => <div key={i}>{[
      issue.rule, issue.message || 'Có lịch liên quan', issue.sectionCode || issue.section_code, issue.roomCode,
      issue.groupCodes?.length > 0 && issue.groupCodes.join(', '),
      issue.dayOfWeek && days[issue.dayOfWeek - 1],
      issue.periodFrom && ('tiết ' + issue.periodFrom + (issue.periodCount > 1 ? '–' + (issue.periodFrom + issue.periodCount - 1) : '')),
      issue.start_at && (formatDate(issue.start_at) + ' ' + issue.start_at.slice(11,16)).trim()
    ].filter(Boolean).join(' · ')}</div>)}
    {details.patterns?.map(p => <div key={p.id}>Lịch nháp · {p.section_code}</div>)}
    {error.request_id && <small className="request-reference">Mã yêu cầu: {error.request_id}</small>}
    {details.suggestions?.length > 0 && <div className="button-row suggestions">{details.suggestions.map(s =>
      <button type="button" className="quiet" key={s.roomId + '-' + s.dayOfWeek + '-' + s.periodFrom}
        onClick={() => onSuggestion?.(s)} disabled={!onSuggestion}>{s.roomCode} · {days[s.dayOfWeek - 1]} · tiết {s.periodFrom}</button>)}</div>}
  </div>
}
